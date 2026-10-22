package com.hormonelog.core.data

import com.hormonelog.core.domain.*
import java.time.Instant
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LegacyChatSettingsMigrationTest {
    @get:Rule val temp = TemporaryFolder()
    private val settings = AppSettings(onboardingDone = true, themeMode = ThemeMode.LIGHT,
        fontScale = FontScale.LARGE, clock24 = true, notifyDaily = true, tUnit = TUnit.NMOL_L,
        lastBackupAtMillis = 1234, backupBannerHiddenUntilMillis = 5678)

    @Test fun legacyModelSelectionsAreIgnoredWithoutResettingOrRewritingOtherSettings() {
        val file = temp.newFile("settings.json")
        for (legacy in listOf<Any>("OPUS", "SONNET", "HAIKU", "future", 42, JSONObject())) {
            val original = SettingsStore.toJson(settings).put("chatModel", legacy).toString()
            file.writeText(original)
            val loaded = SettingsStore(file).load()!!
            assertEquals(settings, loaded)
            assertEquals("Loading must not rewrite user files", original, file.readText())
            SettingsStore(file).save(loaded)
            assertFalse(JSONObject(file.readText()).has("chatModel"))
            assertEquals(settings, SettingsStore(file).load())
        }
        assertFalse(SettingsStore.toJson(AppSettings()).has("chatModel"))
    }

    @Test fun backupsWithLegacySelectionsRestoreRecordsAndPreferencesAndExportWithoutTheRetiredKey() {
        val dose = DoseEvent(UUID.randomUUID(), Instant.parse("2026-10-21T00:00:00Z"), "UTC",
            Drug.CYPROTERONE, Route.ORAL, 25.0, DoseUnit.MG, 25.0, DoseStatus.ADMINISTERED)
        val snapshot = RecordSnapshot(doses = listOf(dose))
        for (legacy in listOf("OPUS", "SONNET", "HAIKU", "future")) {
            val oldBackup = JSONObject(String(BackupCodec.create(snapshot, settings, 0), Charsets.UTF_8))
            oldBackup.getJSONObject("settings").put("chatModel", legacy)
            val loaded = BackupCodec.open(oldBackup.toString().toByteArray(Charsets.UTF_8)) as BackupCodec.Opened.Ok
            assertEquals(snapshot, loaded.snapshot)
            assertEquals(settings.copy(lastBackupAtMillis = null, backupBannerHiddenUntilMillis = null), loaded.settings)
            val exported = JSONObject(String(BackupCodec.create(loaded.snapshot, loaded.settings!!, 0), Charsets.UTF_8))
            assertFalse(exported.getJSONObject("settings").has("chatModel"))
            val roundTrip = BackupCodec.open(exported.toString().toByteArray(Charsets.UTF_8)) as BackupCodec.Opened.Ok
            assertEquals(loaded.snapshot, roundTrip.snapshot)
            assertEquals(loaded.settings, roundTrip.settings)
        }
    }
}
