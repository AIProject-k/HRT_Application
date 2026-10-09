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
import com.hormonelog.core.domain.InjectionSite
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.RecordSource
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
    private fun RecordStore.loaded(): RecordSnapshot =
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

        store.save(RecordSnapshot())
        assertEquals("{ not json", kept.readText())
    }

    @Test
    fun roundTripsDosesLabsAndRegimens() {
        val s = RecordSnapshot(
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
        val s = RecordSnapshot(
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
        val s = RecordSnapshot(
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

    @Test
    fun newFieldsSurviveTheRoundTrip() {
        val s = RecordSnapshot(
            doses = listOf(
                DoseEvent(
                    UUID.randomUUID(), Instant.parse("2026-07-01T09:00:00Z"), "Asia/Seoul", Drug.ESTRADIOL_VALERATE,
                    Route.IM_INJECTION, 5.0, DoseUnit.MG, 5.0, DoseStatus.ADMINISTERED,
                    source = RecordSource.SCHEDULE, site = InjectionSite.LEFT_THIGH,
                ),
            ),
            labs = listOf(
                LabResult(
                    UUID.randomUUID(), Instant.parse("2026-02-20T01:00:00Z"), "Asia/Seoul", Assay.UNKNOWN,
                    listOf(LabAnalyteValue(Analyte.TOTAL_TESTOSTERONE, 5.2, "ng/mL", 520.0)),
                    isBaseline = true, source = RecordSource.IMPORT,
                ),
            ),
            regimens = listOf(
                Regimen(
                    UUID.randomUUID(), Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 5.0, DoseUnit.MG, 7,
                    Instant.parse("2026-09-19T00:00:00Z"), null, weekdays = 0b0100000, timeMinutes = 9 * 60,
                ),
            ),
        )
        val store = store()
        store.save(s)
        assertEquals(s, store.loaded())
    }

    @Test
    fun oneRecordThisBuildCannotReadIsKeptAndTheRestStillLoads() {
        val f = tmp.newFile("future.json").apply {
            writeText(
                """{"version":3,"doses":[
                    {"id":"${UUID.randomUUID()}","occurredAt":1,"sourceZoneId":"UTC","drug":"A_DRUG_FROM_THE_FUTURE","route":"ORAL","amountEntered":1.0,"enteredUnit":"MG","status":"ADMINISTERED"},
                    {"id":"${UUID.randomUUID()}","occurredAt":2,"sourceZoneId":"UTC","drug":"CYPROTERONE","route":"ORAL","amountEntered":25.0,"enteredUnit":"MG","status":"ADMINISTERED"}
                ]}""",
            )
        }
        val store = RecordStore(f)

        val loaded = (store.load() as RecordStore.Load.Ok).snapshot
        assertEquals(1, loaded.doses.size)
        assertEquals(1, loaded.carriedCount)

        // Saving must hand the unreadable record back untouched, not drop it.
        store.save(loaded)
        assertTrue(f.readText().contains("A_DRUG_FROM_THE_FUTURE"))
        assertEquals(1, (store.load() as RecordStore.Load.Ok).snapshot.carriedCount)
    }

    /**
     * A file exactly as v0.1.0 / v0.1.1 wrote it (version 1: no source, site, plan weekdays, memos, journal or stock).
     * Whoever installs a newer build over it must find every record where it was.
     */
    private val v011File = """
        {"version":1,
         "doses":[
           {"id":"11111111-1111-4111-8111-111111111111","occurredAt":1787900000000,"sourceZoneId":"Asia/Seoul","drug":"ESTRADIOL_VALERATE","route":"IM_INJECTION","amountEntered":5.0,"enteredUnit":"MG","normalizedMilligrams":5.0,"status":"ADMINISTERED","note":"왼쪽 허벅지","revision":1},
           {"id":"22222222-2222-4222-8222-222222222222","occurredAt":1787300000000,"sourceZoneId":"Asia/Seoul","drug":"CYPROTERONE","route":"ORAL","amountEntered":12.5,"enteredUnit":"MG","normalizedMilligrams":12.5,"status":"SKIPPED","revision":2}
         ],
         "labs":[
           {"id":"33333333-3333-4333-8333-333333333333","collectedAt":1787800000000,"sourceZoneId":"Asia/Seoul","assay":"LC_MS_MS","note":"트로프","analytes":[{"analyte":"ESTRADIOL","reportedValue":168.0,"reportedUnit":"pg/mL","canonicalValue":168.0}]},
           {"id":"44444444-4444-4444-8444-444444444444","assay":"UNKNOWN","analytes":[{"analyte":"TOTAL_TESTOSTERONE","reportedValue":41.0,"reportedUnit":"ng/dL"}]}
         ],
         "regimens":[
           {"id":"55555555-5555-4555-8555-555555555555","drug":"ESTRADIOL_VALERATE","route":"IM_INJECTION","amountEntered":5.0,"enteredUnit":"MG","everyDays":7,"startAt":1780000000000,"active":true}
         ],
         "clinics":[
           {"id":"66666666-6666-4666-8666-666666666666","name":"OO의원","region":"서울","prescriptionBasis":"INFORMED_CONSENT","telehealth":"YES","priceNote":"초진 3만원","memo":"전화 예약","sourceUrl":"https://example.com"}
         ]}
    """.trimIndent()

    @Test
    fun aFileWrittenByAnEarlierReleaseLoadsWithEveryRecordInPlace() {
        val f = tmp.newFile("v011.json").apply { writeText(v011File) }
        val store = RecordStore(f)
        val s = store.loaded()

        assertEquals(0, s.carriedCount)
        assertEquals(2, s.doses.size)
        val first = s.doses.first { it.drug == Drug.ESTRADIOL_VALERATE }
        assertEquals(Instant.ofEpochMilli(1787900000000), first.occurredAt)
        assertEquals("왼쪽 허벅지", first.note)
        assertEquals(RecordSource.MANUAL, first.source) // the field did not exist yet
        assertEquals(2, s.doses.first { it.status == DoseStatus.SKIPPED }.revision)

        assertEquals(2, s.labs.size)
        assertEquals(168.0, s.labs.first { it.collectedAt != null }.analytes.single().reportedValue, 0.0)
        assertNull(s.labs.first { it.collectedAt == null }.collectedAt)
        assertFalse(s.labs.any { it.isBaseline })

        val plan = s.regimens.single()
        assertEquals(7, plan.everyDays)
        assertEquals(0, plan.weekdays) // an every-N-days plan, as it was
        assertNull(plan.endAt)
        assertTrue(plan.active)

        assertEquals("OO의원", s.clinics.single().name)
        assertEquals(PrescriptionBasis.INFORMED_CONSENT, s.clinics.single().prescriptionBasis)
        assertTrue(s.memos.isEmpty() && s.journal.isEmpty() && s.stock.isEmpty())
        assertNull(s.nextVisitMillis)

        // The next save writes it in the current shape and nothing is lost on the way.
        store.save(s)
        assertEquals(s, store.loaded())
    }

    @Test
    fun aFileFromABuildThatEncryptedItsRecordsIsSetAsideNotOverwritten() {
        // An earlier test build could seal the file (header "HLE1"). This one reads plain JSON only: it must not
        // treat that file as "no records" and write over it.
        val f = tmp.newFile("sealed.json").apply { writeBytes(byteArrayOf(0x48, 0x4C, 0x45, 0x31, 9, 8, 7, 6, 5)) }

        val result = RecordStore(f).load()
        assertTrue(result is RecordStore.Load.Unreadable)
        assertNotNull((result as RecordStore.Load.Unreadable).quarantined)
        assertFalse(f.exists())
    }

    @Test
    fun aWriteThatFailsLeavesTheGoodFileAlone() {
        val f = tmp.newFile("good.json").also { it.delete() }
        val store = RecordStore(f)
        val s = RecordSnapshot(clinics = listOf(Clinic(UUID.randomUUID(), "keep me")))
        store.save(s)

        // A dose dated beyond what a timestamp can hold cannot be written; the file already on disk stays as it was.
        val unwritable = RecordSnapshot(
            doses = listOf(
                DoseEvent(
                    UUID.randomUUID(), Instant.MAX, "Asia/Seoul", Drug.CYPROTERONE,
                    Route.ORAL, 12.5, DoseUnit.MG, 12.5, DoseStatus.ADMINISTERED,
                ),
            ),
        )
        val failed = runCatching { store.save(unwritable) }

        assertTrue("was ${failed.exceptionOrNull()}", failed.exceptionOrNull() is java.io.IOException)
        assertEquals(s, store.loaded())
        assertFalse("no half-written file is left behind", java.io.File(f.parentFile, f.name + ".tmp").exists())
    }
}
