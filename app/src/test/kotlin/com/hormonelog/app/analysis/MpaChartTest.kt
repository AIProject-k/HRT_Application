package com.hormonelog.app.analysis

import com.hormonelog.app.*
import com.hormonelog.app.feature.common.mpaHeadline
import com.hormonelog.app.feature.flow.TickState
import com.hormonelog.app.state.*
import com.hormonelog.core.domain.*
import com.hormonelog.core.evidence.EvidenceStage
import com.hormonelog.core.modelengine.ModelReason
import com.hormonelog.core.modelengine.ModelStatus
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class MpaChartTest {
    private val now = at(2026, 10, 17, 12)
    private val from = now.minusSeconds(86400)
    private val mpa = dose(now.minusSeconds(3 * 3600), Drug.MEDROXYPROGESTERONE_ACETATE, Route.ORAL, 10.0)
    private val p4 = dose(now.minusSeconds(7200), Drug.PROGESTERONE_MICRONIZED, Route.ORAL, 200.0)
    private val p4Lab = lab(now.minusSeconds(3600)).copy(analytes = listOf(LabAnalyteValue(Analyte.PROGESTERONE, 3.0, "ng/mL", 3.0)))

    private fun curvesFor(doses: List<DoseEvent>, regimens: List<Regimen> = emptyList(), labs: List<LabResult> = emptyList(), to: Instant = now) =
        Analysis.curves(doses, regimens, labs, GonadalStatus.DECLINED, now, from, to, SEOUL)

    @Test fun anMpaOnlyHistoryGivesTheMpaTabItsOwnTicksAndNoOtherCurve() {
        val doses = listOf(mpa)
        val curves = curvesFor(doses)
        assertNull(curves.e2)
        assertNull(curves.tt)
        assertNull(curves.p4)
        val mpaTab = chartModelFor(HormoneSeries.MPA, curves, doses, emptyList(), from, now, now, false)
        assertTrue(mpaTab.isMpa)
        assertNull(mpaTab.curve)
        assertEquals(listOf(TickState.TAKEN), mpaTab.doses.map { it.state })
        assertTrue(chartModelFor(HormoneSeries.E2, curves, doses, emptyList(), from, now, now, false).doses.isEmpty())
    }

    @Test fun aPlannedMpaScheduleIsDrawnAsPlannedTicksOnlyOnTheMpaTab() {
        val schedule = daily(now.minusSeconds(2 * 86400), Drug.MEDROXYPROGESTERONE_ACETATE, Route.ORAL, 10.0, 21 * 60)
        val to = now.plusSeconds(2 * 86400)
        val curves = Analysis.curves(emptyList(), listOf(schedule), emptyList(), GonadalStatus.DECLINED, now, from, to, SEOUL)
        val mpaTab = chartModelFor(HormoneSeries.MPA, curves, emptyList(), emptyList(), from, to, now, false)
        assertTrue(mpaTab.doses.isNotEmpty())
        assertTrue(mpaTab.doses.all { it.state == TickState.PLANNED })
        assertTrue(chartModelFor(HormoneSeries.E2, curves, emptyList(), emptyList(), from, to, now, false).doses.isEmpty())
        assertTrue(chartModelFor(HormoneSeries.P4, curves, emptyList(), emptyList(), from, to, now, false).doses.isEmpty())
    }

    @Test fun eachTabDrawsOnlyItsOwnDrugAndMeasuredP4NeverReachesTheMpaTab() {
        val doses = listOf(p4, mpa)
        val labs = listOf(p4Lab)
        val curves = curvesFor(doses, labs = labs)
        assertNotNull(curves.p4)
        assertEquals(1, chartModelFor(HormoneSeries.P4, curves, doses, labs, from, now, now, false).doses.size)
        assertEquals(1, chartModelFor(HormoneSeries.P4, curves, doses, labs, from, now, now, false).labs.size)
        val mpaTab = chartModelFor(HormoneSeries.MPA, curves, doses, labs, from, now, now, false)
        assertEquals(1, mpaTab.doses.size)
        assertTrue(mpaTab.labs.isEmpty())
    }

    @Test fun aRecordTheModelRefusesStillShowsTheEvidenceReasonFirst() {
        val tooLarge = dose(now.minusSeconds(3600), Drug.MEDROXYPROGESTERONE_ACETATE, Route.ORAL, 50.0)
        val curves = curvesFor(listOf(tooLarge))
        assertEquals(ModelStatus.Unavailable(ModelReason.EVIDENCE_INSUFFICIENT, EvidenceStage.PK_SUMMARY), curves.mpaStatus)
        assertTrue(mpaHeadline(curves).contains("근거 검증 중"))
    }

    @Test fun theMpaLineNamesItsReasonAndNeverShowsANumber() {
        val base = curvesFor(emptyList())
        val reasons = ModelReason.entries.map { mpaHeadline(base.copy(mpaStatus = ModelStatus.Unavailable(it, EvidenceStage.PK_SUMMARY))) }
        assertEquals(ModelReason.entries.size, reasons.toSet().size)
        for (line in reasons + mpaHeadline(null)) {
            assertFalse(line, line.any { it.isDigit() })
            assertTrue(line, line.contains("MPA"))
        }
    }
}
