package com.hormonelog.core.data

import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.Clinic
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.util.UUID

class BackupCodecTest {
    private val snapshot = RecordSnapshot(
        doses = listOf(
            DoseEvent(
                UUID.randomUUID(), Instant.parse("2026-09-01T00:00:00Z"), "Asia/Seoul", Drug.CYPROTERONE,
                Route.ORAL, 12.5, DoseUnit.MG, 12.5, DoseStatus.SKIPPED, note = "비밀 메모",
            ),
        ),
        regimens = listOf(
            Regimen(UUID.randomUUID(), Drug.CYPROTERONE, Route.ORAL, 12.5, DoseUnit.MG, 1, Instant.parse("2026-03-01T00:00:00Z"), null),
        ),
        clinics = listOf(Clinic(UUID.randomUUID(), "OO의원", memo = "예약 필수")),
    )
    private val settings = AppSettings(
        onboardingDone = true,
        themeMode = ThemeMode.LIGHT,
        gonadalStatus = GonadalStatus.POST_ORCHIECTOMY,
        lastBackupAtMillis = 5L,
    )

    @Test
    fun aBackupHoldsEverythingACsvLeavesOut() {
        val bytes = BackupCodec.create(snapshot, settings, createdAtMillis = 1_700_000_000_000)
        val opened = BackupCodec.open(bytes) as BackupCodec.Opened.Ok

        assertEquals(snapshot, opened.snapshot)
        assertEquals(1, opened.preview.regimens)
        assertEquals(1, opened.preview.clinics)
        assertEquals(1_700_000_000_000, opened.preview.createdAtMillis)
        assertEquals(ThemeMode.LIGHT, opened.settings!!.themeMode)
        assertEquals(GonadalStatus.POST_ORCHIECTOMY, opened.settings!!.gonadalStatus)
    }

    @Test
    fun theBackupIsPlainJsonAnyoneWithTheFileCanRead() {
        val text = String(BackupCodec.create(snapshot, settings, 0L), Charsets.UTF_8)
        assertTrue(text.startsWith("{"))
        assertTrue(text.contains("비밀 메모"))
    }

    @Test
    fun theBackupNudgeBelongsToThePhoneNotToTheFile() {
        val opened = BackupCodec.open(BackupCodec.create(snapshot, settings, 0L)) as BackupCodec.Opened.Ok
        assertNull(opened.settings!!.lastBackupAtMillis)
    }

    @Test
    fun somethingThatIsNotABackupIsSaidSoPlainly() {
        assertEquals(BackupCodec.Opened.Unreadable, BackupCodec.open("hello".toByteArray()))
        assertEquals(BackupCodec.Opened.Unreadable, BackupCodec.open("""{"format":"other"}""".toByteArray()))
        assertEquals(BackupCodec.Opened.Unreadable, BackupCodec.open("""{"format":"hormonelog-backup","formatVersion":99}""".toByteArray()))
        // A file sealed with a password by an earlier test build is not JSON: it is said to be unreadable, not a crash.
        assertEquals(BackupCodec.Opened.Unreadable, BackupCodec.open(byteArrayOf(0x48, 0x4C, 0x42, 0x31, 0, 0, 0, 1, 2, 3)))
    }

    @Test
    fun theBackupCarriesRecordsAThisBuildCouldNotRead() {
        val withCarried = snapshot.copy(carried = mapOf("doses" to listOf("""{"id":"x","drug":"FUTURE"}""")))
        val opened = BackupCodec.open(BackupCodec.create(withCarried, settings, 0L)) as BackupCodec.Opened.Ok
        assertEquals(1, opened.snapshot.carriedCount)
    }
}

class SettingsStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun noFileMeansAFirstRun() {
        assertNull(SettingsStore(tmp.newFile("s.json").also { it.delete() }).load())
    }

    @Test
    fun settingsRoundTrip() {
        val store = SettingsStore(tmp.newFile("s2.json").also { it.delete() })
        val s = AppSettings(
            onboardingDone = true, themeMode = ThemeMode.DARK, clock24 = true,
            gonadalStatus = GonadalStatus.DECLINED, hideInRecents = false, disguiseLauncher = true,
            lastBackupAtMillis = 123L,
        )
        store.save(s)
        assertEquals(s, store.load())
    }

    @Test
    fun aValueFromANewerBuildFallsBackToItsDefault() {
        val f = tmp.newFile("s3.json").apply { writeText("""{"themeMode":"HOLOGRAM","clock24":true}""") }
        val loaded = SettingsStore(f).load()!!
        assertEquals(ThemeMode.SYSTEM, loaded.themeMode)
        assertTrue(loaded.clock24)
    }

    @Test
    fun aFileFromABuildThatHadAnAppLockStillLoads() {
        // Those keys are gone from the app; they are simply ignored and dropped on the next save.
        val f = tmp.newFile("s4.json").apply { writeText("""{"themeMode":"DARK","lockMode":"PIN_ONLY","pinSalt":"a","pinHash":"b","pinFailures":3}""") }
        assertEquals(ThemeMode.DARK, SettingsStore(f).load()!!.themeMode)
    }
}
