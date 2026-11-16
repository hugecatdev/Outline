package com.wavecat.outline.api

import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.wavecat.outline.api.locks.IgnoreKeyEvent
import com.wavecat.outline.api.locks.utils.Lock
import com.wavecat.outline.utils.coroutineResume
import com.wavecat.outline.utils.coroutineStatus
import com.wavecat.outline.utils.coroutineYield
import com.wavecat.outline.utils.runOnUiThread
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaString
import org.luaj.vm2.LuaUserdata
import org.luaj.vm2.LuaValue
import org.luaj.vm2.lib.jse.CoerceJavaToLua
import java.util.Timer
import kotlin.concurrent.timerTask

class Script(
    val name: String,
    private val globals: Globals,
    private val coroutine: LuaValue,
    private val onRecycle: (Script) -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    private val timer: Timer = Timer()

    private var lock: Lock? = null
    private var wakeToken: Any? = null

    var isRecycled = false
        private set

    var latestKeyEvent: KeyEvent? = null
    var latestAccessibilityEvent: AccessibilityEvent? = null

    val isRunning: Boolean
        get() = globals.running === coroutine

    fun resume(): Boolean {
        Log.i(TAG, "$name resumed")

        val previousLock = lock
        lock = null

        val result = globals.coroutineResume(coroutine)

        if (!result.arg1().optboolean(true))
            fail(LuaError(result.arg(2)))

        if (globals.coroutineStatus(coroutine) == DEAD) {
            Log.i(TAG, "$name finished")
            dispose()
            return true
        }

        when (val value = result.arg(2)) {
            is LuaString -> fail(LuaError(value))

            is LuaUserdata -> when (val yielded = value.touserdata()) {
                is IgnoreKeyEvent -> {
                    Log.i(TAG, "$name flagged ignore KeyEvent")

                    resume()
                    return false
                }

                is Lock -> waitFor(yielded)

                else -> fail(LuaError("$name: expected Lock, got $yielded"))
            }

            else -> previousLock?.let { waitFor(it) }
        }

        return true
    }

    private fun waitFor(newLock: Lock) {
        newLock.reset()
        lock = newLock

        Log.i(TAG, "$name locked by $newLock")
    }

    fun delay(interval: Long) {
        Log.i(TAG, "$name start $interval delay")

        await { wake -> timer.schedule(timerTask { wake() }, interval) }
    }

    fun await(start: (wake: () -> Unit) -> Unit) {
        val token = Any()
        wakeToken = token

        start {
            runOnUiThread {
                if (isRecycled || wakeToken !== token)
                    return@runOnUiThread

                wakeToken = null
                runCatching { resume() }.onFailure(onError)
            }
        }

        globals.coroutineYield(CoerceJavaToLua.coerce(Lock()))
    }

    fun onEvent(event: Any): Boolean {
        val current = lock ?: return false

        val unlocked = try {
            current.tryUnlock(event) == true
        } catch (e: Throwable) {
            current.reset()
            throw e
        }

        if (!unlocked)
            return false

        when (event) {
            is KeyEvent -> latestKeyEvent = event
            is AccessibilityEvent -> latestAccessibilityEvent = event
        }

        return resume()
    }

    fun interrupt() {
        recycle()
        globals.coroutineResume(coroutine)
    }

    private fun fail(error: LuaError): Nothing {
        Log.e(TAG, "$name failed", error)
        dispose()
        throw error
    }

    private fun dispose() {
        recycle()
        onRecycle(this)
    }

    private fun recycle() {
        isRecycled = true
        lock = null
        timer.cancel()
        timer.purge()
    }

    companion object {
        const val TAG = "Script"

        private const val DEAD = "dead"
    }
}
