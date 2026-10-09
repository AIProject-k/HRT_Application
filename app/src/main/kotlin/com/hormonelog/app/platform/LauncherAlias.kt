package com.hormonelog.app.platform

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Chooses which launcher entry the phone shows: the app's own name and icon, or a plain
 * "메모" with a neutral icon. Both are aliases of the same screen, so nothing else changes.
 */
object LauncherAlias {
    private const val DEFAULT = "com.hormonelog.app.LauncherDefault"
    private const val DISGUISED = "com.hormonelog.app.LauncherDisguised"

    fun apply(context: Context, disguised: Boolean) {
        val pm = context.packageManager
        // Turn the new one on before the old one off, so the app never has no launcher entry.
        if (disguised) {
            set(pm, context, DISGUISED, true)
            set(pm, context, DEFAULT, false)
        } else {
            set(pm, context, DEFAULT, true)
            set(pm, context, DISGUISED, false)
        }
    }

    private fun set(pm: PackageManager, context: Context, name: String, enabled: Boolean) {
        pm.setComponentEnabledSetting(
            ComponentName(context.packageName, name),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }
}
