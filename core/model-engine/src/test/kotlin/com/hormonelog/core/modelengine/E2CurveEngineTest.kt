package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.PatchCycle
import com.hormonelog.core.domain.Route
import com.hormonelog.core.evidence.EvidenceBundleV1
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class E2CurveEngineTest {
    private val engine = E2CurveEngine(EvidenceBundleV1.bundle)
    private val t0 = Instant.parse("2026-06-01T09:00:00Z")

    private fun dose(route: Route, drug: Drug, mg: Double, at: Instant, unit: DoseUnit = DoseUnit.MG) = DoseEvent(
        id = UUID.randomUUID(),
        occurredAt = at,
        sourceZoneId = "UTC",
        drug = drug,
        route = route,
        amountEntered = mg,
        enteredUnit = unit,
        normalizedMilligrams = DoseEvent.normalizeMilligrams(mg, unit),
        status = DoseStatus.ADMINISTERED,
    )

    @Test
    fun evFiveMgSingleDosePeaksInTheExpectedRange() {
        val r = engine.curve(listOf(dose(Route.IM_INJECTION, Drug.ESTRADIOL_VALERATE, 5.0, t0)), t0, t0.plus(21, ChronoUnit.DAYS))
        r as EstimateResult.Available
        val peak = r.series.points.maxByOrNull { it.median }!!
        val peakDay = (peak.at.toEpochMilli() - t0.toEpochMilli()) / 86_400_000.0
        assertTrue("Cmax ${peak.median}", peak.median in 240.0..360.0)
        assertTrue("Tmax $peakDay d", peakDay in 1.0..4.0)
        assertTrue(peak.lower < peak.median && peak.median < peak.upper)
    }

    @Test
    fun biweeklyRegimenKeepsTroughAboveBaseline() {
        val doses = (0..3).map { dose(Route.IM_INJECTION, Drug.ESTRADIOL_VALERATE, 10.0, t0.plus((it * 14).toLong(), ChronoUnit.DAYS)) }
        val r = engine.curve(doses, t0.plus(30, ChronoUnit.DAYS), t0.plus(56, ChronoUnit.DAYS))
        r as EstimateResult.Available
        assertTrue(r.series.points.minOf { it.median } > 6.0)
    }

    @Test
    fun noDosesIsUnavailable() {
        assertEquals(
            EstimateResult.Unavailable(ModelUnavailableReason.NO_DATA),
            engine.curve(emptyList(), t0, t0.plus(7, ChronoUnit.DAYS)),
        )
    }

    @Test
    fun gelRouteIsUnavailable() {
        val r = engine.curve(listOf(dose(Route.GEL, Drug.ESTRADIOL_VALERATE, 2.0, t0)), t0, t0.plus(7, ChronoUnit.DAYS))
        assertEquals(EstimateResult.Unavailable(ModelUnavailableReason.ROUTE_UNSUPPORTED), r)
    }

    @Test
    fun aGelDoseIsLeftOutAndReportedWhileTheRestStillDrawsACurve() {
        val inj = dose(Route.IM_INJECTION, Drug.ESTRADIOL_VALERATE, 5.0, t0)
        val gel = dose(Route.GEL, Drug.ESTRADIOL_GEL, 1.5, t0.plus(2, ChronoUnit.DAYS))
        val r = engine.curve(listOf(inj, gel), t0, t0.plus(21, ChronoUnit.DAYS)) as EstimateResult.Available

        assertEquals(listOf(ExcludedDose(gel, DoseExclusion.NO_MODEL)), r.excluded)
        assertTrue(r.series.points.maxOf { it.median } > 200.0)
    }

    @Test
    fun aPatchIsReadAsMicrogramsPerDay() {
        fun patch(strength: Double, unit: DoseUnit) = dose(Route.PATCH, Drug.ESTRADIOL_PATCH, strength, t0, unit)
        fun peak(d: DoseEvent) = (engine.curve(listOf(d), t0, t0.plus(10, ChronoUnit.DAYS)) as EstimateResult.Available).series.points.maxOf { it.median }

        val fifty = peak(patch(50.0, DoseUnit.UG_PER_DAY))
        val hundred = peak(patch(100.0, DoseUnit.UG_PER_DAY))
        assertTrue("a 50 µg/day patch should reach a plausible level, not ~0: $fifty", fifty in 20.0..120.0)
        assertEquals(2.0, (hundred - 5.0) / (fifty - 5.0), 0.01)
        // Strength labelled the old way ("mg/일") still means the same patch.
        assertEquals(fifty, peak(patch(50.0, DoseUnit.MG_PER_DAY)), 1e-9)
    }

    @Test
    fun aPatchOfUnknownStrengthIsReportedNotGuessed() {
        val unknown = dose(Route.PATCH, Drug.ESTRADIOL_PATCH, 1.0, t0, DoseUnit.PATCH)
        val inj = dose(Route.IM_INJECTION, Drug.ESTRADIOL_VALERATE, 5.0, t0)

        assertEquals(
            EstimateResult.Unavailable(ModelUnavailableReason.ROUTE_UNSUPPORTED),
            engine.curve(listOf(unknown), t0, t0.plus(7, ChronoUnit.DAYS)),
        )
        val mixed = engine.curve(listOf(unknown, inj), t0, t0.plus(7, ChronoUnit.DAYS)) as EstimateResult.Available
        assertEquals(DoseExclusion.PATCH_STRENGTH_MISSING, mixed.excluded.single().reason)
    }

    @Test
    fun weeklyAndTwiceWeeklyPatchesDoNotShareAProfile() {
        val twice = dose(Route.PATCH, Drug.ESTRADIOL_PATCH, 50.0, t0, DoseUnit.UG_PER_DAY).copy(patchCycle = PatchCycle.TWICE_WEEKLY)
        val weekly = twice.copy(patchCycle = PatchCycle.WEEKLY)
        fun series(d: DoseEvent) = (engine.curve(listOf(d), t0, t0.plus(7, ChronoUnit.DAYS)) as EstimateResult.Available).series.points.map { it.median }

        assertTrue(series(twice) != series(weekly))
    }

    @Test
    fun cypionateIsRecognisedAsAnInjectableEster() {
        val r = engine.curve(listOf(dose(Route.IM_INJECTION, Drug.ESTRADIOL_CYPIONATE, 5.0, t0)), t0, t0.plus(21, ChronoUnit.DAYS))
        assertTrue(r is EstimateResult.Available)
    }

    @Test
    fun anAntiandrogenTabletDoesNotWidenTheIntervalOfAnInjection() {
        val inj = dose(Route.IM_INJECTION, Drug.ESTRADIOL_VALERATE, 5.0, t0)
        val cpa = dose(Route.ORAL, Drug.CYPROTERONE, 12.5, t0)
        fun width(doses: List<DoseEvent>): Double {
            val p = (engine.curve(doses, t0, t0.plus(7, ChronoUnit.DAYS)) as EstimateResult.Available).series.points[80]
            return (p.upper - p.lower) / p.median
        }
        assertEquals(width(listOf(inj)), width(listOf(inj, cpa)), 1e-9)
    }

    @Test
    fun anAdjustmentScalesOnlyItsOwnRouteGroup() {
        val inj = dose(Route.IM_INJECTION, Drug.ESTRADIOL_VALERATE, 5.0, t0)
        val oral = dose(Route.ORAL, Drug.ESTRADIOL_TABLET, 2.0, t0)
        val adj = mapOf(CalibrationGroup.INJECTION to Adjustment(scale = 2.0))
        val at = t0.plus(3, ChronoUnit.DAYS)

        val injOnly = engine.medianAt(listOf(inj), at)!! - 5.0
        val oralOnly = engine.medianAt(listOf(oral), at)!! - 5.0
        val both = engine.medianAt(listOf(inj, oral), at, adjustments = adj)!! - 5.0
        assertEquals(injOnly * 2.0 + oralOnly, both, 1e-6)
    }

    @Test
    fun deterministic() {
        val d = listOf(dose(Route.IM_INJECTION, Drug.ESTRADIOL_VALERATE, 5.0, t0))
        val a = engine.curve(d, t0, t0.plus(14, ChronoUnit.DAYS))
        val b = engine.curve(d, t0, t0.plus(14, ChronoUnit.DAYS))
        assertEquals(a, b)
    }
}
