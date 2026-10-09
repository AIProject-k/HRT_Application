package com.hormonelog.app

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.hormonelog.core.domain.AppSettings

class MainActivity : ComponentActivity() {

    private lateinit var vm: AppViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm = ViewModelProvider(this)[AppViewModel::class.java]
        applySecurity(vm.state.settings)
        setContent { AppRoot(vm = vm, onSettingsChanged = ::applySecurity) }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    /**
     * Keeps the app's content out of screenshots and the recent-apps switcher when the user asks
     * for it, and — in disguise — labels this task the way the launcher entry is labelled, so the
     * switcher does not give the real name away either.
     */
    private fun applySecurity(settings: AppSettings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Hides only the switcher's thumbnail; the user can still take a screenshot of their own screen.
            setRecentsScreenshotEnabled(!settings.hideInRecents)
        } else if (settings.hideInRecents) {
            // Before Android 13 the only way to hide the thumbnail also blocks screenshots.
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        @Suppress("DEPRECATION")
        if (settings.disguiseLauncher) {
            setTaskDescription(ActivityManager.TaskDescription(getString(R.string.disguised_label), R.mipmap.ic_memo, 0xFF0F1115.toInt()))
        } else {
            setTaskDescription(ActivityManager.TaskDescription(getString(R.string.app_name), R.mipmap.ic_launcher, 0xFF0F1115.toInt()))
        }
    }
}
