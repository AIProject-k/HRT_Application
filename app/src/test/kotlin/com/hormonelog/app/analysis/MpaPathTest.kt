package com.hormonelog.app.analysis

import com.hormonelog.app.*
import com.hormonelog.app.state.*
import com.hormonelog.core.domain.*
import com.hormonelog.core.evidence.EvidenceStage
import com.hormonelog.core.modelengine.ModelReason
import com.hormonelog.core.modelengine.ModelStatus
import org.junit.Assert.*
import org.junit.Test

class MpaPathTest {
    private val now = at(2026, 10, 17, 12)
    private val mpa = dose(now.minusSeconds(86400), Drug.MEDROXYPROGESTERONE_ACETATE, Route.ORAL, 10.0)
    private val estrogen = dose(now.minusSeconds(2 * 86400), Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 5.0)
    private val progesterone = dose(now.minusSeconds(7200), Drug.PROGESTERONE_MICRONIZED, Route.ORAL, 200.0)

    @Test fun mpaOnlyHistoryDrawsNoE2TtOrP4AndReportsItsOwnStatus() {
        val curves = Analysis.curves(listOf(mpa), emptyList(), emptyList(), GonadalStatus.INTACT, now, now.minusSeconds(86400), now, SEOUL)
        assertNull(curves.e2)
        assertNull(curves.tt)
        assertNull(curves.p4)
        assertTrue(curves.planned.isEmpty())
        val status = Analysis.mpaStatus(listOf(mpa), now) as ModelStatus.Available
        assertTrue(status.relative)
        // A day after one 10 mg tablet, two half-lives on, about a third of its peak is left.
        assertEquals(31.0, status.series.points.last().median, 3.0)
    }

    @Test fun mpaRecordsLeaveTheE2TtAndP4CurvesExactlyAsTheyWere() {
        val from = now.minusSeconds(3 * 86400)
        val without = Analysis.curves(listOf(estrogen, progesterone), emptyList(), emptyList(), GonadalStatus.INTACT, now, from, now, SEOUL)
        val with = Analysis.curves(listOf(estrogen, progesterone, mpa), emptyList(), emptyList(), GonadalStatus.INTACT, now, from, now, SEOUL)
        assertNotNull(without.e2)
        assertEquals(without.e2, with.e2)
        assertEquals(without.tt, with.tt)
        assertEquals(without.p4, with.p4)
        assertEquals(without.planned, with.planned)
    }

    @Test fun aPlannedMpaScheduleIsItsOwnForecastAndNeverAnE2OrP4Plan() {
        val schedule = daily(now, Drug.MEDROXYPROGESTERONE_ACETATE, Route.ORAL, 10.0, 21 * 60)
        val until = now.plusSeconds(2 * 86400)
        val mpaPlan = Analysis.plannedMpa(listOf(schedule), emptyList(), now, until, SEOUL)
        assertTrue(mpaPlan.isNotEmpty())
        assertTrue(mpaPlan.all { it.drug == Drug.MEDROXYPROGESTERONE_ACETATE && it.source == RecordSource.SCHEDULE })
        assertTrue(Analysis.planned(listOf(schedule), emptyList(), now, until, SEOUL).isEmpty())
    }

    @Test fun everyRecordableMpaAmountIsDrawnInPercentInProportionToItsDose() {
        fun percentAnHourAfter(mg: Double): Double {
            val record = dose(now.minusSeconds(3600), Drug.MEDROXYPROGESTERONE_ACETATE, Route.ORAL, mg)
            val status = Analysis.mpaStatus(listOf(record), now) as ModelStatus.Available
            assertTrue(status.relative)
            return status.series.points.last().median
        }
        for (mg in listOf(2.5, 5.0, 20.0)) assertEquals(percentAnHourAfter(10.0) * mg / 10.0, percentAnHourAfter(mg), 1e-9)
    }

    @Test fun aSkippedMpaDoseIsNoData() {
        assertEquals(
            ModelStatus.Unavailable(ModelReason.NO_DATA, EvidenceStage.PK_SUMMARY),
            Analysis.mpaStatus(listOf(mpa.copy(status = DoseStatus.SKIPPED)), now),
        )
    }
}
