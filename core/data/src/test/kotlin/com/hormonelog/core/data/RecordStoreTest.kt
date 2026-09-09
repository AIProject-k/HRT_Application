package com.hormonelog.core.data

import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.Clinic
import com.hormonelog.core.domain.PrescriptionBasis
import com.hormonelog.core.domain.Telehealth
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.util.UUID

class RecordStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = RecordStore(tmp.newFile("records.json").also { it.delete() })

    /** Unwraps a load that is expected to have succeeded. */
    private fun RecordStore.loaded(): RecordStore.Snapshot =
        (load() as RecordStore.Load.Ok).snapshot

    @Test
    fun missingFileIsAGenuineFirstRun() {
        assertEquals(RecordStore.Load.Empty, store().load())
    }

    @Test
    fun anUnreadableFileIsQuarantinedRatherThanReportedAsEmpty() {
        val f = tmp.newFile("bad.json").apply { writeText("{ not json") }
        val store = RecordStore(f)

        val result = store.load()
        assertTrue("must not look like a first run", result is RecordStore.Load.Unreadable)

        // The damaged bytes survive, and the original path is free for a fresh file.
        val kept = (result as RecordStore.Load.Unreadable).quarantined
        assertNotNull(kept)
        assertEquals("{ not json", kept!!.readText())
        assertFalse(f.exists())
    }

    @Test
    fun savingAfterAnUnreadableLoadDoesNotDestroyTheOriginal() {
        val f = tmp.newFile("bad2.json").apply { writeText("{ not json") }
        val store = RecordStore(f)
        val kept = (store.load() as RecordStore.Load.Unreadable).quarantined!!

        store.save(RecordStore.Snapshot())
        assertEquals("{ not json", kept.readText())
    }

    @Test
    fun roundTripsDosesLabsAndRegimens() {
        val s = RecordStore.Snapshot(
            doses = listOf(
                DoseEvent(
                    UUID.randomUUID(), Instant.parse("2026-07-01T09:00:00Z"), "Asia/Seoul",
                    Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 10.0, DoseUnit.MG, 10.0,
                    DoseStatus.ADMINISTERED, note = "left thigh", revision = 2,
                ),
                DoseEvent(
                    UUID.randomUUID(), Instant.parse("2026-07-02T09:00:00Z"), "Asia/Seoul",
                    Drug.ESTRADIOL_PATCH, Route.PATCH, 100.0, DoseUnit.MG_PER_DAY, null, DoseStatus.SKIPPED,
                ),
            ),
            labs = listOf(
                LabResult(
                    UUID.randomUUID(), Instant.parse("2026-07-10T01:00:00Z"), "Asia/Seoul", Assay.LC_MS_MS,
                    analytes = listOf(
                        LabAnalyteValue(Analyte.ESTRADIOL, 174.0, "pg/mL", 174.0),
                        LabAnalyteValue(Analyte.TOTAL_TESTOSTERONE, 21.0, "ng/dL", null),
                    ),
                    note = "fasting",
                ),
                LabResult(UUID.randomUUID(), null, null, Assay.UNKNOWN),
            ),
            regimens = listOf(
                Regimen(
                    UUID.randomUUID(), Drug.CYPROTERONE, Route.ORAL, 25.0, DoseUnit.MG, 1,
                    Instant.parse("2026-06-01T00:00:00Z"), null, active = true,
                ),
            ),
            clinics = listOf(
                Clinic(
                    UUID.randomUUID(), "OO의원", "서울 강남",
                    PrescriptionBasis.INFORMED_CONSENT, Telehealth.YES,
                    priceNote = "초진 3만", memo = "예약 필수", sourceUrl = "https://arca.live/b/...",
                ),
                Clinic(UUID.randomUUID(), "△△병원"),
            ),
        )
        val store = store()
        store.save(s)
        assertEquals(s, store.loaded())
    }

    @Test
    fun labWithoutACollectionTimeSurvivesTheRoundTrip() {
        // The recorder can now save "채혈 시각 모름", so null has to come back as null
        // rather than as a guessed instant that would then look eligible for calibration.
        val s = RecordStore.Snapshot(
            labs = listOf(
                LabResult(
                    UUID.randomUUID(), null, null, Assay.UNKNOWN,
                    listOf(LabAnalyteValue(Analyte.ESTRADIOL, 412.0, "pg/mL", 412.0)),
                ),
            ),
        )
        val store = store()
        store.save(s)
        assertEquals(s, store.loaded())
        assertNull(store.loaded().labs.single().collectedAt)
    }

    @Test
    fun aSkippedOrLateDoseKeepsItsStatus() {
        val at = Instant.parse("2026-07-01T09:00:00Z")
        val s = RecordStore.Snapshot(
            doses = listOf(
                DoseEvent(
                    UUID.randomUUID(), at, "Asia/Seoul", Drug.ESTRADIOL_VALERATE,
                    Route.IM_INJECTION, 10.0, DoseUnit.MG, 10.0, DoseStatus.SKIPPED,
                ),
                DoseEvent(
                    UUID.randomUUID(), at.plusSeconds(3600), "Asia/Seoul", Drug.CYPROTERONE,
                    Route.ORAL, 25.0, DoseUnit.MG, 25.0, DoseStatus.DELAYED,
                ),
            ),
        )
        val store = store()
        store.save(s)
        val back = store.loaded().doses
        assertEquals(DoseStatus.SKIPPED, back[0].status)
        assertEquals(DoseStatus.DELAYED, back[1].status)
    }
}
