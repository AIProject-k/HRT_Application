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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.util.UUID

class RecordStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = RecordStore(tmp.newFile("records.json").also { it.delete() })

    @Test
    fun missingFileLoadsEmpty() {
        assertEquals(RecordStore.Snapshot(), store().load())
    }

    @Test
    fun corruptFileLoadsEmpty() {
        val f = tmp.newFile("bad.json").apply { writeText("{ not json") }
        assertEquals(RecordStore.Snapshot(), RecordStore(f).load())
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
        assertEquals(s, store.load())
    }
}
