package com.wavecat.outline.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.os.Build
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.wavecat.outline.api.Script
import com.wavecat.outline.api.Speech
import com.wavecat.outline.api.installAccessibilityEventLib
import com.wavecat.outline.api.installAutomationLib
import com.wavecat.outline.api.installKeyEventLib
import com.wavecat.outline.api.installMediaLib
import com.wavecat.outline.api.installUtilsLib
import com.wavecat.outline.api.KeyConsumption
import com.wavecat.outline.api.LuaConstants
import com.wavecat.outline.api.locks.utils.AllLock
import com.wavecat.outline.api.locks.utils.Lock
import com.wavecat.outline.utils.CustomDebugLib
import com.wavecat.outline.utils.coroutineCreate
import com.wavecat.outline.utils.coroutineYield
import com.wavecat.outline.utils.oneArgFunction
import com.wavecat.outline.utils.runOnUiThread
import com.wavecat.outline.utils.toList
import com.wavecat.outline.utils.toStringUserdata
import com.wavecat.outline.utils.twoArgFunction
import com.wavecat.outline.utils.varArgFunction
import com.wavecat.outline.utils.zeroArgFunction
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.LuaValue.NIL
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import org.luaj.vm2.lib.jse.JsePlatform


@SuppressLint("AccessibilityPolicy")
class OutlineService : AccessibilityService() {
    private val preferences by lazy { PreferenceManager.getDefaultSharedPreferences(this) }

    private val debugLib = CustomDebugLib()
    private val scriptContext: Script?
        get() = scripts.find { it.isRunning }

    private lateinit var speech: Speech

    private val globals: Globals by lazy {
        JsePlatform.standardGlobals().apply {
            for ((name, value) in LuaConstants.globalActions.constants)
                set(name, value)

            set("service", CoerceJavaToLua.coerce(this))

            set("delay", oneArgFunction { arg ->
                val context = scriptContext ?: throw LuaError("scriptContext == null")

                context.delay(arg.checklong())
                NIL
            })

            set("getPreferences", zeroArgFunction { CoerceJavaToLua.coerce(preferences) })

            installAccessibilityEventLib()
            installUtilsLib()
            installKeyEventLib()
            installMediaLib(this@OutlineService)

            installAutomationLib(applicationContext)

            set("waitFor", varArgFunction { args ->
                coroutineYield(
                    when (args.narg()) {
                        0 -> CoerceJavaToLua.coerce(Lock())
                        1 -> args.arg1()
                        else -> CoerceJavaToLua.coerce(AllLock(args.toList()))
                    }
                )
            })

            set("getAccessibilityEvent", zeroArgFunction {
                CoerceJavaToLua.coerce(scriptContext?.latestAccessibilityEvent)
            })

            set("runScriptUnique", twoArgFunction { function, name ->
                val targetName = name.optjstring("")

                if (scripts.find { it.name == targetName } != null)
                    return@twoArgFunction NIL

                CoerceJavaToLua.coerce(runScript(function.checkfunction(), targetName))
            })

            set("runScript", twoArgFunction { function, name ->
                CoerceJavaToLua.coerce(runScript(function.checkfunction(), name.optjstring("")))
            })

            set("findRoot", oneArgFunction { event ->
                val context = scriptContext ?: throw LuaError("scriptContext == null")

                var root: AccessibilityNodeInfo? =
                    (if (event.isnil()) context.latestAccessibilityEvent else
                        event.checkuserdata() as AccessibilityEvent)?.source

                while (root?.parent != null)
                    root = root.parent

                CoerceJavaToLua.coerce(root)
            })

            set("getKeyEvent", zeroArgFunction {
                CoerceJavaToLua.coerce(scriptContext?.latestKeyEvent)
            })

            set("perform", oneArgFunction { arg ->
                performGlobalAction(arg.checkint())
                NIL
            })

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                set("takeScreenshot", zeroArgFunction {
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                    NIL
                })
            }

            set("vibrate", oneArgFunction { duration ->
                val vibrator = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration.checklong())
                NIL
            })

            set("toast", oneArgFunction { arg ->
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        arg.toStringUserdata(),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                NIL
            })

            set("speak", twoArgFunction { text, queueMode ->
                speech.speak(text.checkjstring(), flush = queueMode.optjstring("add") == "flush")
                NIL
            })

            set("speakAndWait", twoArgFunction { text, queueMode ->
                val context = scriptContext ?: throw LuaError("scriptContext == null")
                val phrase = text.checkjstring()
                val flush = queueMode.optjstring("add") == "flush"

                context.await { wake -> speech.speak(phrase, flush, wake) }
                NIL
            })

            load(debugLib)
        }
    }

    private val scripts = mutableListOf<Script>()

    private val keyConsumption = KeyConsumption()

    override fun onCreate() {
        super.onCreate()
        speech = Speech(this)
    }

    override fun onDestroy() {
        speech.shutdown()
        super.onDestroy()
    }

    override fun onServiceConnected() {
        instance = this

        super.onServiceConnected()

        val info = AccessibilityServiceInfo()

        info.flags =
            AccessibilityServiceInfo.DEFAULT or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS

        info.eventTypes = 0
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK

        serviceInfo = info

        if (preferences.getBoolean(AUTO_START_PREF, true))
            loadScripts()
    }

    fun loadScripts() {
        interruptScripts()

        val code = preferences.getString("script", "").orEmpty()

        code
            .split("-----\n")
            .forEachIndexed { index, script ->
                runCatching { runScript(globals.load(script, "#$index")) }
                    .onFailure(::notifyError)
            }
    }

    private fun interruptScripts() = debugLib.apply {
        interrupted = true

        scripts.toList().forEach { it.interrupt() }
        scripts.clear()

        interrupted = false
    }

    private fun runScript(function: LuaValue, name: String = ""): Script =
        Script(
            name = name.ifEmpty { "#${scripts.size}" },
            globals = globals,
            coroutine = globals.coroutineCreate(function),
            onRecycle = { scripts.remove(it) },
            onError = ::notifyError,
        )
            .apply {
                scripts.add(this)
                resume()
            }

    private inline fun dispatch(block: (Script) -> Unit) {
        for (script in scripts.toList()) {
            if (script.isRecycled)
                continue

            runCatching { block(script) }
                .onFailure(::notifyError)
        }
    }

    private fun notifyError(e: Throwable) {
        Toast.makeText(this, e.toString(), Toast.LENGTH_SHORT).show()
        e.printStackTrace()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        dispatchEvent(event)
    }

    fun dispatchEvent(event: Any) = dispatch { it.onEvent(event) }

    override fun onInterrupt() {
        instance = null
        interruptScripts()
        keyConsumption.clear()
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false

        var requested = false

        dispatch {
            if (it.onEvent(event))
                requested = true
        }

        return keyConsumption.decide(event.keyCode, event.action, event.repeatCount, requested)
    }

    companion object {
        var instance: OutlineService? = null

        const val AUTO_START_PREF = "auto_start"
    }
}