package com.hormonelog.app.analysis

import com.hormonelog.app.*
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.state.*
import com.hormonelog.core.domain.*
import org.junit.Assert.*
import org.junit.Test

class ProgesteroneIntegrationTest {
    private val now = at(2026, 10, 17, 12)
    private val p4 = dose(now.minusSeconds(7200), Drug.PROGESTERONE_MICRONIZED, Route.ORAL, 200.0)

    @Test fun oralRecorderOffersP4ButOtherRoutesDoNot() {
        assertTrue(Drug.PROGESTERONE_MICRONIZED in drugsFor(Route.ORAL))
        assertTrue(Route.entries.filter { it != Route.ORAL }.all { Drug.PROGESTERONE_MICRONIZED !in drugsFor(it) })
    }

    @Test fun p4OnlyLabSavesAndEditsWithItsOriginalUnitAndZero() {
        val s = RecordsReducer.saveLab(AppState(labDraft = LabDraft(p4 = "31.8", p4Unit = P4Unit.NMOL_L)), now, SEOUL)
        val v = s.labs.single().analytes.single()
        assertEquals(Analyte.PROGESTERONE, v.analyte)
        assertEquals(10.0, v.canonicalValue!!, 1e-8)
        val draft = LabDraft.of(s.labs.single(), E2Unit.PG_ML, TUnit.NG_DL)
        assertEquals("31.8", draft.p4)
        assertEquals(P4Unit.NMOL_L, draft.p4Unit)
        assertNotNull(s.labResult!!.p4)
        assertNull(s.labResult.e2)
        assertTrue(LabDraft(p4 = "0").canSave)
        assertFalse(LabDraft(p4 = "1..2", e2 = "100").canSave)
    }

    @Test fun p4SeriesUsesP4UnitsLabsAndTicksWithoutTheE2Guide() {
        val l = lab(now, e2 = 100.0).copy(analytes = listOf(LabAnalyteValue(Analyte.PROGESTERONE, 3.18, "nmol/L", 1.0)))
        val s = AppState(doses = listOf(p4), labs = listOf(l))
        val curves = Analysis.curves(s.doses, emptyList(), s.labs, GonadalStatus.DECLINED, now, now.minusSeconds(86400), now, SEOUL)
        assertNotNull(curves.p4)
        assertNull(curves.e2)
        val model = chartModelFor(HormoneSeries.P4, curves, s.doses, s.labs, now.minusSeconds(86400), now, now, true)
        assertTrue(model.isP4)
        assertFalse(model.isTotalT)
        assertNull(model.guide)
        assertEquals(1.0, model.labs.single().value, 0.0)
        assertFalse(model.labs.single().used)
        assertEquals(1, model.doses.size)
    }

    @Test fun p4DoesNotMoveEstrogenCycleOrCalibrationAndP4LabsAppearInTimeline() {
        val estrogen = dose(now.minusSeconds(86400))
        assertEquals(estrogen, Analysis.lastEstrogenBefore(listOf(estrogen, p4), now))
        val l = lab(now).copy(analytes = listOf(LabAnalyteValue(Analyte.PROGESTERONE, 3.0, "ng/mL", 3.0)))
        val s = AppState(doses = listOf(p4), labs = listOf(l), settings = settings)
        val cal = Analysis.calibrate(s.doses, s.labs, settings.gonadalStatus)
        assertTrue(cal.groups.isEmpty())
        val timeline = TimelineLogic.build(s, Fmt(SEOUL, true, now), cal)
        assertTrue(timeline.months.flatMap { it.days }.flatMap { it.rows }.any { it.title.contains("P4 3 ng/mL") })
    }

    @Test fun skippedP4AndFutureActualRecordsDoNotCountAsCurrentIntake() {
        val doses = listOf(p4.copy(occurredAt = now.plusSeconds(3600)), p4.copy(status = DoseStatus.SKIPPED))
        assertNull(Analysis.p4At(doses, now))
        assertNull(Analysis.curves(doses, emptyList(), emptyList(), GonadalStatus.INTACT, now, now.minusSeconds(3600), now.plusSeconds(86400), SEOUL).p4)
    }

    @Test fun schedulesForecastP4WithoutCreatingActualDoseRecords() {
        val schedule = daily(now, Drug.PROGESTERONE_MICRONIZED, Route.ORAL, 200.0, 22 * 60)
        val curves = Analysis.curves(emptyList(), listOf(schedule), emptyList(), GonadalStatus.INTACT, now, now, now.plusSeconds(2 * 86400), SEOUL)
        assertNotNull(curves.p4)
        assertNull(curves.e2)
        assertTrue(curves.planned.all { it.source == RecordSource.SCHEDULE })
    }

    @Test fun p4ReportsIncludeTheMeasuredValueAndDoseDraftKeepsTheOralDrug() {
        val l = lab(now).copy(analytes = listOf(LabAnalyteValue(Analyte.PROGESTERONE, 3.0, "ng/mL", 3.0)))
        val s = AppState(doses = listOf(p4), labs = listOf(l), settings = settings)
        assertEquals(Drug.PROGESTERONE_MICRONIZED, RecordsReducer.openDose(s).doseDraft.drug)
        val report = ReportLogic.build(s, Fmt(SEOUL, true, now))
        assertTrue(report.labsNote!!.contains("P4 실측"))
        assertTrue(report.labsNote.contains("3 ng/mL"))
    }

    @Test fun equalReportedNumbersWithDifferentP4UnitsAreNotSilentlyDeduplicated() {
        val l = lab(now).copy(analytes = listOf(LabAnalyteValue(Analyte.PROGESTERONE, 3.0, "ng/mL", 3.0)))
        val other = l.copy(id = java.util.UUID.randomUUID(), analytes = listOf(LabAnalyteValue(Analyte.PROGESTERONE, 3.0, "nmol/L", 3.0 / 3.18)))
        val (restored, duplicates) = RecordsReducer.mergeImported(AppState(labs = listOf(l)), emptyList(), listOf(other))
        assertEquals(0, duplicates)
        assertEquals(2, restored.labs.size)
    }

    @Test fun editingImportedP4DoesNotInventAUnitConversion() {
        val l = lab(now).copy(analytes = listOf(LabAnalyteValue(Analyte.PROGESTERONE, 10.0, "unrecognised", null)))
        val d = LabDraft.of(l, E2Unit.PG_ML, TUnit.NG_DL)
        val saved = RecordsReducer.saveLab(AppState(labs = listOf(l), labDraft = d.copy(note = "메모 수정")), now, SEOUL)
        assertEquals("unrecognised", saved.labs.single().analytes.single().reportedUnit)
        assertNull(saved.labs.single().analytes.single().canonicalValue)
    }
}
