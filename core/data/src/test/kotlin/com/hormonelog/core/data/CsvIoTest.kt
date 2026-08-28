package com.hormonelog.core.data

import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CsvIoTest {

    private val seoul = ZoneId.of("Asia/Seoul")

    private val sample = """
        type,datetime,drug,route,amount,unit,e2,tt,e2_unit,assay,note
        dose,2026-09-01T09:00,estradiol_valerate,im,10,mg,,,,,데포 주입
        dose,2026-09-15T09:00,estradiol_valerate,im,10,mg,,,,,데포 주입
        dose,2026-09-01T08:00,cyproterone,oral,25,mg,,,,,안드로쿨 반알
        dose,2026-09-02T08:00,안드로쿨,oral,25,mg,,,,,반알
        lab,2026-09-21T10:30,,,,,320,22,pg/mL,lc_ms_ms,투약 20일차
        lab,2026-10-19T11:00,,,,,410,,pg/mL,immunoassay,E2만
        lab,2026-09-01,,,,,,,,,값 없음 → skip
        garbage,line,that,should,be,skipped
    """.trimIndent()

    @Test
    fun parsesDosesAndLabsAndCountsSkips() {
        val r = CsvIo.parse(sample, seoul)

        assertEquals(4, r.doses.size)
        assertEquals(2, r.labs.size)
        // "lab with no e2/tt" + "garbage" row
        assertEquals(2, r.skipped)
    }

    @Test
    fun mapsDrugRouteUnitAndKoreanAliases() {
        val r = CsvIo.parse(sample, seoul)
        val ev = r.doses.first { it.drug == Drug.ESTRADIOL_VALERATE }
        assertEquals(Route.IM_INJECTION, ev.route)
        assertEquals(DoseUnit.MG, ev.enteredUnit)
        assertEquals(10.0, ev.amountEntered, 0.0)
        assertEquals(10.0, ev.normalizedMilligrams!!, 0.0)

        // "안드로쿨" alias → CYPROTERONE
        assertEquals(2, r.doses.count { it.drug == Drug.CYPROTERONE })
    }

    @Test
    fun parsesLabAnalytesWithCanonicalConversion() {
        val r = CsvIo.parse(sample, seoul)
        val full = r.labs.first { it.analytes.size == 2 }
        assertEquals(Assay.LC_MS_MS, full.assay)
        val e2 = full.analytes.first { it.analyte == Analyte.ESTRADIOL }
        assertEquals(320.0, e2.canonicalValue!!, 0.0) // pg/mL is canonical
        val tt = full.analytes.first { it.analyte == Analyte.TOTAL_TESTOSTERONE }
        assertEquals(22.0, tt.canonicalValue!!, 0.0) // ng/dL is canonical

        val e2Only = r.labs.first { it.analytes.size == 1 }
        assertEquals(Analyte.ESTRADIOL, e2Only.analytes.single().analyte)
        assertEquals(Assay.IMMUNOASSAY, e2Only.assay)
    }

    @Test
    fun dateOnlyDatetimeDefaultsToNineAmLocal() {
        val r = CsvIo.parse(
            "type,datetime,drug,route,amount,unit,e2,tt,e2_unit,assay,note\n" +
                "dose,2026-09-01,estradiol_valerate,im,10,mg,,,,,x",
            seoul,
        )
        val at = r.doses.single().occurredAt.atZone(seoul)
        assertEquals(9, at.hour)
        assertEquals(1, at.dayOfMonth)
    }

    @Test
    fun exportThenParseRoundTrips() {
        val parsed = CsvIo.parse(sample, seoul)
        val text = CsvIo.export(parsed.doses, parsed.labs, seoul)
        val again = CsvIo.parse(text, seoul)

        assertEquals(parsed.doses.size, again.doses.size)
        assertEquals(parsed.labs.size, again.labs.size)
        assertEquals(0, again.skipped)

        val a = parsed.doses.sortedBy { it.occurredAt }
        val b = again.doses.sortedBy { it.occurredAt }
        a.zip(b).forEach { (x, y) ->
            assertEquals(x.drug, y.drug)
            assertEquals(x.route, y.route)
            assertEquals(x.amountEntered, y.amountEntered, 0.0)
            assertEquals(x.occurredAt, y.occurredAt)
            assertEquals(x.note, y.note)
        }
    }

    @Test
    fun emptyOrHeaderOnlyInputYieldsNothing() {
        val r = CsvIo.parse("type,datetime,drug,route,amount,unit,e2,tt,e2_unit,assay,note", seoul)
        assertTrue(r.doses.isEmpty())
        assertTrue(r.labs.isEmpty())
        assertEquals(0, r.skipped)

        val blank = CsvIo.parse("", seoul)
        assertTrue(blank.doses.isEmpty())
        assertNull(blank.labs.firstOrNull())
    }
}
