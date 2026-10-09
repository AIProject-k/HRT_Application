package com.hormonelog.core.data

import com.hormonelog.core.domain.AppSettings
import org.json.JSONObject

/**
 * The full backup file (`.hlb`): every record, schedule and clinic note plus the user's
 * preferences, in one plain JSON file. Unlike a CSV nothing is left out.
 */
object BackupCodec {
    const val FORMAT = "hormonelog-backup"
    private const val FORMAT_VERSION = 1

    data class Preview(
        val createdAtMillis: Long,
        val doses: Int,
        val labs: Int,
        val regimens: Int,
        val clinics: Int,
        val memos: Int = 0,
        val journal: Int = 0,
    )

    sealed interface Opened {
        data class Ok(val snapshot: RecordSnapshot, val settings: AppSettings?, val preview: Preview) : Opened

        /** Not a HormoneLog backup, damaged, or written by a newer format. */
        data object Unreadable : Opened
    }

    fun create(snapshot: RecordSnapshot, settings: AppSettings, createdAtMillis: Long): ByteArray {
        val root = JSONObject().apply {
            put("format", FORMAT)
            put("formatVersion", FORMAT_VERSION)
            put("createdAt", createdAtMillis)
            put("records", RecordJson.toJson(snapshot))
            put("settings", SettingsStore.toJson(settings.forBackup()))
        }
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    fun open(bytes: ByteArray): Opened = try {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        if (root.optString("format") != FORMAT || root.optInt("formatVersion", Int.MAX_VALUE) > FORMAT_VERSION) {
            Opened.Unreadable
        } else {
            val snapshot = RecordJson.fromJson(root.getJSONObject("records"))
            val settings = root.optJSONObject("settings")?.let(SettingsStore::fromJson)
            Opened.Ok(
                snapshot,
                settings,
                Preview(root.optLong("createdAt"), snapshot.doses.size, snapshot.labs.size, snapshot.regimens.size, snapshot.clinics.size, snapshot.memos.size, snapshot.journal.size),
            )
        }
    } catch (_: Exception) {
        Opened.Unreadable
    }

    /** The backup nudge belongs to this phone, not to the file. */
    private fun AppSettings.forBackup() = copy(
        lastBackupAtMillis = null,
        backupBannerHiddenUntilMillis = null,
    )
}
