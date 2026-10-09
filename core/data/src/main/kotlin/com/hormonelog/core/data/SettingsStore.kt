package com.hormonelog.core.data

import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.E2Unit
import com.hormonelog.core.domain.FontScale
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.TUnit
import com.hormonelog.core.domain.ThemeMode
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * The app's own settings as a small JSON file, kept apart from the records. A value this
 * build does not understand falls back to its default instead of failing the whole file.
 */
class SettingsStore(private val file: File) {

    /** null on a genuine first run (no file yet); defaults when the file cannot be read. */
    fun load(): AppSettings? {
        if (!file.exists()) return null
        return try {
            fromJson(JSONObject(file.readText()))
        } catch (_: Exception) {
            runCatching { file.renameTo(File(file.parentFile, "${file.nameWithoutExtension}.corrupt-${System.currentTimeMillis()}.json")) }
            AppSettings()
        }
    }

    fun save(settings: AppSettings) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            tmp.writeText(toJson(settings).toString())
            if (!tmp.renameTo(file)) {
                file.writeText(toJson(settings).toString())
                tmp.delete()
            }
        } catch (e: Exception) {
            tmp.delete()
            throw if (e is IOException) e else IOException(e.message, e)
        }
    }

    companion object {
        fun toJson(s: AppSettings): JSONObject = JSONObject().apply {
            put("version", 1)
            put("onboardingDone", s.onboardingDone)
            put("themeMode", s.themeMode.name)
            put("fontScale", s.fontScale.name)
            put("e2Unit", s.e2Unit.name)
            put("tUnit", s.tUnit.name)
            put("clock24", s.clock24)
            put("gonadalStatus", s.gonadalStatus.name)
            put("hideInRecents", s.hideInRecents)
            put("disguiseLauncher", s.disguiseLauncher)
            put("neutralNotifications", s.neutralNotifications)
            put("notifyInjection", s.notifyInjection)
            put("notifyDaily", s.notifyDaily)
            put("notifyLab", s.notifyLab)
            put("notifyAppointment", s.notifyAppointment)
            put("hideOnLockScreen", s.hideOnLockScreen)
            putOpt("lastBackupAtMillis", s.lastBackupAtMillis)
            putOpt("backupBannerHiddenUntilMillis", s.backupBannerHiddenUntilMillis)
        }

        fun fromJson(o: JSONObject): AppSettings {
            val d = AppSettings()
            return AppSettings(
                onboardingDone = o.optBoolean("onboardingDone", d.onboardingDone),
                themeMode = o.enumOr("themeMode", d.themeMode),
                fontScale = o.enumOr("fontScale", d.fontScale),
                e2Unit = o.enumOr("e2Unit", d.e2Unit),
                tUnit = o.enumOr("tUnit", d.tUnit),
                clock24 = o.optBoolean("clock24", d.clock24),
                gonadalStatus = o.enumOr("gonadalStatus", d.gonadalStatus),
                hideInRecents = o.optBoolean("hideInRecents", d.hideInRecents),
                disguiseLauncher = o.optBoolean("disguiseLauncher", d.disguiseLauncher),
                neutralNotifications = o.optBoolean("neutralNotifications", d.neutralNotifications),
                notifyInjection = o.optBoolean("notifyInjection", d.notifyInjection),
                notifyDaily = o.optBoolean("notifyDaily", d.notifyDaily),
                notifyLab = o.optBoolean("notifyLab", d.notifyLab),
                notifyAppointment = o.optBoolean("notifyAppointment", d.notifyAppointment),
                hideOnLockScreen = o.optBoolean("hideOnLockScreen", d.hideOnLockScreen),
                lastBackupAtMillis = o.longOrNull("lastBackupAtMillis"),
                backupBannerHiddenUntilMillis = o.longOrNull("backupBannerHiddenUntilMillis"),
            )
        }

        private fun JSONObject.stringOrNull(key: String): String? =
            if (isNull(key)) null else optString(key).ifEmpty { null }

        private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else optLong(key)

        private inline fun <reified E : Enum<E>> JSONObject.enumOr(key: String, default: E): E =
            stringOrNull(key)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default
    }
}
