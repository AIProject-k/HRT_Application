package com.hormonelog.core.data

import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.RecordSource
import com.hormonelog.core.domain.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

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

    private val header = "type,datetime,drug,route,amount,unit,e2,tt,e2_unit,assay,note,status,tt_unit,site,baseline"

    @Test
    fun skippedRowsSayWhichLineAndWhy() {
        val r = CsvIo.parse(
            """
            $header
            dose,2026-09-01T09:00,estradiol_valerate,im,10,mg,,,,,,,,,
            dose,not-a-date,estradiol_valerate,im,10,mg,,,,,,,,,
            dose,2026-09-02T09:00,mystery_drug,im,10,mg,,,,,,,,,
            lab,2026-09-03,,,,,,,,,,,,,
            """.trimIndent(),
            seoul,
        )

        assertEquals(1, r.doses.size)
        assertEquals(
            listOf(SkippedRow(3, SkipReason.BAD_DATE), SkippedRow(4, SkipReason.UNKNOWN_DRUG), SkippedRow(5, SkipReason.NO_VALUE)),
            r.skippedRows,
        )
    }

    @Test
    fun aSkippedDoseStaysSkippedAfterABackupAndRestore() {
        val parsed = CsvIo.parse(
            "$header\ndose,2026-09-01T09:00,estradiol_valerate,im,10,mg,,,,,,skipped,,,\ndose,2026-09-02T09:00,cyproterone,oral,25,mg,,,,,,delayed,,,",
            seoul,
        )
        val back = CsvIo.parse(CsvIo.export(parsed.doses, emptyList(), seoul), seoul)

        assertEquals(listOf(DoseStatus.SKIPPED, DoseStatus.DELAYED), back.doses.sortedBy { it.occurredAt }.map { it.status })
    }

    @Test
    fun aTestosteroneUnitSurvivesTheTrip() {
        val parsed = CsvIo.parse("$header\nlab,2026-09-21T10:30,,,,,,1.2,,,,,nmol/L,,", seoul)
        val back = CsvIo.parse(CsvIo.export(emptyList(), parsed.labs, seoul), seoul)

        val tt = back.labs.single().analytes.single()
        assertEquals("nmol/L", tt.reportedUnit)
        assertEquals(1.2 * 28.842, tt.canonicalValue!!, 1e-9)
    }

    @Test
    fun aNoteWithALineBreakAndACommaSurvivesTheTrip() {
        val parsed = CsvIo.parse("$header\ndose,2026-09-01T09:00,cyproterone,oral,25,mg,,,,,\"반알, 두 번째 줄\n끝\",,,,", seoul)
        assertEquals("반알, 두 번째 줄\n끝", parsed.doses.single().note)

        val back = CsvIo.parse(CsvIo.export(parsed.doses, emptyList(), seoul), seoul)
        assertEquals(parsed.doses.single().note, back.doses.single().note)
        assertEquals(0, back.skipped)
    }

    @Test
    fun importRefusesWhatTheRecorderItselfWouldRefuse() {
        val r = CsvIo.parse(
            """
            $header
            dose,2026-09-01T09:00,estradiol_patch,im,50,,,,,,,,,,
            dose,2026-09-02T09:00,cyproterone,oral,0,mg,,,,,,,,,
            dose,2026-09-03T09:00,cyproterone,oral,-5,mg,,,,,,,,,
            dose,2026-09-04T09:00,cyproterone,oral,NaN,mg,,,,,,,,,
            """.trimIndent(),
            seoul,
        )

        assertTrue(r.doses.isEmpty())
        assertEquals(
            listOf(SkipReason.BAD_COMBINATION, SkipReason.BAD_AMOUNT, SkipReason.BAD_AMOUNT, SkipReason.BAD_AMOUNT),
            r.skippedRows.map { it.reason },
        )
    }

    @Test
    fun aPatchRowWithoutAUnitIsReadAsMicrogramsPerDay() {
        val r = CsvIo.parse("$header\ndose,2026-09-01T09:00,estradiol_patch,patch,50,,,,,,,,,,", seoul)
        assertEquals(DoseUnit.UG_PER_DAY, r.doses.single().enteredUnit)
    }

    @Test
    fun importedRecordsAreMarkedAsImported() {
        val r = CsvIo.parse(sample, seoul)
        assertTrue(r.doses.all { it.source == RecordSource.IMPORT })
        assertTrue(r.labs.all { it.source == RecordSource.IMPORT })
    }

    @Test
    fun theExportOpensCleanlyInAKoreanSpreadsheet() {
        // A UTF-8 byte-order mark is what makes Excel show Hangul instead of mojibake.
        assertTrue(CsvIo.export(emptyList(), emptyList()).startsWith("\uFEFF"))
        assertEquals(0, CsvIo.parse(CsvIo.export(emptyList(), emptyList())).skipped)
    }

    @Test
    fun aDateNoRealLogCouldHoldIsABadDateNotARecordThatBreaksTheScreens() {
        val r = CsvIo.parse(
            """
            $header
            dose,0001-01-01,estradiol_valerate,im,5,mg,,,,,,,,,
            dose,+999999999-12-31T00:00,estradiol_valerate,im,5,mg,,,,,,,,,
            dose,9999-12-31,estradiol_valerate,im,5,mg,,,,,,,,,
            lab,0001-01-01,,,,,150,,pg/mL,,,,,,
            dose,2026-10-01,estradiol_valerate,im,5,mg,,,,,,,,,
            """.trimIndent(),
            seoul,
        )
        assertEquals(1, r.doses.size)
        assertTrue(r.labs.isEmpty())
        assertEquals(List(4) { SkipReason.BAD_DATE }, r.skippedRows.map { it.reason })
    }

    private fun doseWithNote(day: Int, note: String) = DoseEvent(
        UUID.randomUUID(), Instant.parse("2026-09-%02dT00:00:00Z".format(day)), "Asia/Seoul", Drug.ESTRADIOL_VALERATE,
        Route.IM_INJECTION, 5.0, DoseUnit.MG, 5.0, DoseStatus.ADMINISTERED, note = note,
    )

    @Test
    fun aNoteThatASpreadsheetWouldRunAsAFormulaGetsAnApostropheAndGetsItBackOnImport() {
        val notes = listOf("=1+1", "+82 10", "-5kg 감량", "@SUM(A1)", "=HYPERLINK(\"http://example.com\",\"x\")")
        val csv = CsvIo.export(notes.mapIndexed { i, n -> doseWithNote(i + 1, n) }, emptyList(), seoul)

        // No cell in the file starts a formula: a comma (or the quote opening a cell) is never followed by = + - @.
        assertFalse(csv, Regex(""",\"?[=+\-@]""").containsMatchIn(csv))
        // Importing the file gives the notes back exactly as they were written.
        assertEquals(notes, CsvIo.parse(csv, seoul).doses.sortedBy { it.occurredAt }.map { it.note })
    }

    @Test
    fun anOrdinaryNoteIsNotTouched() {
        val csv = CsvIo.export(listOf(doseWithNote(1, "데포 주입"), doseWithNote(2, "it's fine")), emptyList(), seoul)
        assertTrue(csv.contains(",데포 주입,"))
        assertTrue(csv.contains(",it's fine,"))
        assertEquals(listOf("데포 주입", "it's fine"), CsvIo.parse(csv, seoul).doses.sortedBy { it.occurredAt }.map { it.note })
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
