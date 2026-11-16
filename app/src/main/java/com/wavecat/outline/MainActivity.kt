package com.wavecat.outline

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.preference.PreferenceManager
import com.wavecat.outline.ai.AiController
import com.wavecat.outline.databinding.ActivityMainBinding
import com.wavecat.outline.services.OutlineService


class MainActivity : AppCompatActivity() {
    private val binding: ActivityMainBinding by lazy { ActivityMainBinding.inflate(layoutInflater) }

    private val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(
            this
        )
    }

    private lateinit var ai: AiController

    private val pushNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
            )

            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)

            insets
        }

        binding.hints.attach(binding.script)
        ai = AiController(this, binding.script, binding.hints)

        binding.save.setOnClickListener {
            preferences.edit {
                putString("script", binding.script.text.toString())
                apply()
            }

            val service = OutlineService.instance

            if (service == null) {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } else {
                service.loadScripts()
            }
        }

        binding.script.setText(preferences.getString("script", ""), TextView.BufferType.EDITABLE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pushNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(Menu.NONE, MENU_GENERATE, Menu.NONE, R.string.ai_generate)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(Menu.NONE, MENU_AI_SETTINGS, Menu.NONE, R.string.ai_settings)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        MENU_GENERATE -> {
            ai.showGenerateDialog()
            true
        }

        MENU_AI_SETTINGS -> {
            ai.showSettingsDialog()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    private companion object {
        const val MENU_GENERATE = 1
        const val MENU_AI_SETTINGS = 2
    }
}