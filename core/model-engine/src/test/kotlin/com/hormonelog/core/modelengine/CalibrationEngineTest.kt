package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.Route
import com.hormonelog.core.evidence.EvidenceBundleV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class CalibrationEngineTest {

    private val bundle = EvidenceBundleV1.bundle
    private val engine = CalibrationEngine(bundle)
    private val e2 = E2CurveEngine(bundle)
    private val start = Instant.parse("2026-05-01T09:00:00Z")

    private fun evDoses() = (0..4).map {
        DoseEvent(
            id = UUID.randomUUID(),
            occurredAt = start.plus((it * 14).toLong(), ChronoUnit.DAYS),
            sourceZoneId = "UTC",
            drug = Drug.ESTRADIOL_VALERATE,
            route = Route.IM_INJECTION,
            amountEntered = 10.0,
            enteredUnit = DoseUnit.MG,
            normalizedMilligrams = 10.0,
            status = DoseStatus.ADMINISTERED,
        )
    }

    private fun oralDoses(from: Instant, days: Int) = (0 until days).map {
        DoseEvent(
            id = UUID.randomUUID(),
            occurredAt = from.plus(it.toLong(), ChronoUnit.DAYS),
            sourceZoneId = "UTC",
            drug = Drug.ESTRADIOL_TABLET,
            route = Route.ORAL,
            amountEntered = 2.0,
            enteredUnit = DoseUnit.MG,
            normalizedMilligrams = 2.0,
            status = DoseStatus.ADMINISTERED,
        )
    }

    private fun e2Lab(at: Instant?, measuredPgMl: Double, baseline: Boolean = false) = LabResult(
        id = UUID.randomUUID(),
        collectedAt = at,
        sourceZoneId = "UTC",
        assay = Assay.LC_MS_MS,
        analytes = listOf(LabAnalyteValue(Analyte.ESTRADIOL, measuredPgMl, "pg/mL", measuredPgMl)),
        note = null,
        isBaseline = baseline,
    )

    private fun ttLab(at: Instant, measuredNgDl: Double) = LabResult(
        id = UUID.randomUUID(),
        collectedAt = at,
        sourceZoneId = "UTC",
        assay = Assay.UNKNOWN,
        analytes = listOf(LabAnalyteValue(Analyte.TOTAL_TESTOSTERONE, measuredNgDl, "ng/dL", measuredNgDl)),
    )

    private fun CalibrationResult.injectionScale() = groups.getValue(CalibrationGroup.INJECTION).scale

    @Test
    fun noLabsMeansNoAdjustment() {
        val r = engine.calibrate(evDoses(), emptyList())
        assertEquals(CalibrationLevel.LEVEL_0, r.level)
        assertTrue(r.groups.isEmpty())
        assertTrue(r.adjustments.isEmpty())
        assertTrue(r.includedLabIds.isEmpty())
    }

    @Test
    fun oneLabScalesTowardTheMeasuredValue() {
        val doses = evDoses()
        val at = start.plus(40, ChronoUnit.DAYS)
        val predicted = e2.medianAt(doses, at)!!
        val r = engine.calibrate(doses, listOf(e2Lab(at, predicted * 1.4)))

        assertEquals(CalibrationLevel.LEVEL_1, r.level)
        assertEquals(1, r.includedLabIds.size)
        assertEquals(1.4, r.injectionScale(), 0.05)
    }

    @Test
    fun aSingleLabReproducesItsOwnMeasuredValueOnceCalibrated() {
        // The scale applies to the drug's part only, so the baseline must not leak into the ratio.
        val doses = evDoses()
        val at = start.plus(40, ChronoUnit.DAYS)
        val measured = e2.medianAt(doses, at)!! * 1.3
        val r = engine.calibrate(doses, listOf(e2Lab(at, measured)))

        assertEquals(measured, e2.medianAt(doses, at, adjustments = r.adjustments)!!, 0.01)
    }

    @Test
    fun twoLabsGiveLevelTwoAndGeometricMean() {
        val doses = evDoses()
        val a = start.plus(35, ChronoUnit.DAYS)
        val b = start.plus(52, ChronoUnit.DAYS)
        val pa = e2.medianAt(doses, a)!!
        val pb = e2.medianAt(doses, b)!!
        // ratios 1.2 and 1.8 -> geomean ~1.47
        val r = engine.calibrate(doses, listOf(e2Lab(a, pa * 1.2), e2Lab(b, pb * 1.8)))

        assertEquals(CalibrationLevel.LEVEL_2, r.level)
        assertEquals(2, r.includedLabIds.size)
        assertEquals(1.47, r.injectionScale(), 0.1)
    }

    @Test
    fun moreLabsNarrowTheIntervalButNeverBelowTheirOwnScatter() {
        val doses = evDoses()
        val times = listOf(30L, 38L, 52L).map { start.plus(it, ChronoUnit.DAYS) }
        val labs = times.map { e2Lab(it, e2.medianAt(doses, it)!! * 1.2) }
        val none = (e2.estimateAt(doses, times.last()))!!
        val calibrated = engine.calibrate(doses, labs)
        val after = e2.estimateAt(doses, times.last(), adjustments = calibrated.adjustments)!!

        val widthBefore = (none.upper - none.lower) / none.median
        val widthAfter = (after.upper - after.lower) / after.median
        assertTrue("$widthAfter should be narrower than $widthBefore", widthAfter < widthBefore)
        assertTrue(widthAfter > 0.0)
    }

    @Test
    fun extremeRatiosAreClamped() {
        val doses = evDoses()
        val at = start.plus(40, ChronoUnit.DAYS)
        val predicted = e2.medianAt(doses, at)!!

        val high = engine.calibrate(doses, listOf(e2Lab(at, predicted * 20)))
        assertEquals(CalibrationEngine.SCALE_MAX, high.injectionScale(), 0.0)
        assertTrue(high.groups.getValue(CalibrationGroup.INJECTION).atLimit)

        val low = engine.calibrate(doses, listOf(e2Lab(at, predicted * 0.05)))
        assertEquals(CalibrationEngine.SCALE_MIN, low.injectionScale(), 0.0)
    }

    @Test
    fun ineligibleLabsAreExcludedWithReasons() {
        val doses = evDoses()
        val noTime = e2Lab(null, 200.0)
        val beforeAnyDose = e2Lab(start.minus(5, ChronoUnit.DAYS), 200.0)
        val justStarted = e2Lab(start.plus(5, ChronoUnit.DAYS), 200.0)
        val beforeHrt = e2Lab(start.plus(40, ChronoUnit.DAYS), 28.0, baseline = true)
        val r = engine.calibrate(doses, listOf(noTime, beforeAnyDose, justStarted, beforeHrt))

        assertEquals(CalibrationLevel.LEVEL_0, r.level)
        assertEquals(LabExclusion.NO_COLLECTION_TIME, r.labs.getValue(noTime.id).e2!!.exclusion)
        assertEquals(LabExclusion.NO_PRIOR_DOSE, r.labs.getValue(beforeAnyDose.id).e2!!.exclusion)
        assertEquals(LabExclusion.TOO_EARLY, r.labs.getValue(justStarted.id).e2!!.exclusion)
        assertEquals(LabExclusion.BASELINE, r.labs.getValue(beforeHrt.id).e2!!.exclusion)
    }

    @Test
    fun aLabWithAnUnknownUnitIsNotTakenForPgPerMl() {
        val doses = evDoses()
        val lab = LabResult(
            UUID.randomUUID(), start.plus(40, ChronoUnit.DAYS), "UTC", Assay.UNKNOWN,
            listOf(LabAnalyteValue(Analyte.ESTRADIOL, 734.0, "mystery", null)),
        )
        val r = engine.calibrate(doses, listOf(lab))
        assertEquals(LabExclusion.UNKNOWN_UNIT, r.labs.getValue(lab.id).e2!!.exclusion)
        assertTrue(r.groups.isEmpty())
    }

    @Test
    fun aLabMixedWithUnmodelledExposureIsExcludedForThatStatedReason() {
        val doses = evDoses()
        val at = doses.last().occurredAt.plus(3, ChronoUnit.DAYS)
        val gel = DoseEvent(
            id = UUID.randomUUID(),
            // A route with no evidence-backed model, given between the last injection
            // and the draw, so the measured value carries exposure the prediction cannot.
            occurredAt = at.minus(2, ChronoUnit.DAYS),
            sourceZoneId = "UTC",
            drug = Drug.ESTRADIOL_GEL,
            route = Route.GEL,
            amountEntered = 2.0,
            enteredUnit = DoseUnit.MG,
            normalizedMilligrams = 2.0,
            status = DoseStatus.ADMINISTERED,
        )
        val lab = e2Lab(at, 400.0)

        val clean = engine.calibrate(doses, listOf(lab))
        assertTrue("baseline: the lab is usable on its own", clean.includedLabIds.contains(lab.id))

        val mixed = engine.calibrate(doses + gel, listOf(lab))
        assertTrue(mixed.includedLabIds.isEmpty())
        assertEquals(LabExclusion.MIXED_UNMODELLED, mixed.labs.getValue(lab.id).e2!!.exclusion)
    }

    @Test
    fun aGelDoseFromLongAgoNoLongerSpoilsEveryLab() {
        val doses = evDoses()
        val longAgoGel = DoseEvent(
            UUID.randomUUID(), start.minus(60, ChronoUnit.DAYS), "UTC", Drug.ESTRADIOL_GEL,
            Route.GEL, 2.0, DoseUnit.MG, 2.0, DoseStatus.ADMINISTERED,
        )
        val at = start.plus(40, ChronoUnit.DAYS)
        val lab = e2Lab(at, e2.medianAt(doses, at)!! * 1.1)

        assertTrue(engine.calibrate(doses + longAgoGel, listOf(lab)).includedLabIds.contains(lab.id))
    }

    @Test
    fun changingRouteDoesNotCarryTheOldRoutesCorrectionOver() {
        // Injections first (a lab says "run 40% high"), then a switch to tablets with no lab yet.
        val inj = evDoses().take(3)
        val oralFrom = start.plus(60, ChronoUnit.DAYS)
        val doses = inj + oralDoses(oralFrom, 30)
        val at = start.plus(40, ChronoUnit.DAYS)
        val lab = e2Lab(at, e2.medianAt(inj, at)!! * 1.4)

        val r = engine.calibrate(doses, listOf(lab))

        assertEquals(setOf(CalibrationGroup.INJECTION), r.groups.keys)
        val oralDay = oralFrom.plus(20, ChronoUnit.DAYS)
        assertEquals(
            e2.medianAt(oralDoses(oralFrom, 30), oralDay)!!,
            e2.medianAt(doses.filter { it.route == Route.ORAL }, oralDay, adjustments = r.adjustments)!!,
            1e-6,
        )
    }

    @Test
    fun totalTestosteroneNeedsTwoLabsBeforeItCorrectsAnything() {
        val doses = evDoses()
        val tt = TtSuppressionEngine(bundle)
        val a = start.plus(40, ChronoUnit.DAYS)
        val b = start.plus(55, ChronoUnit.DAYS)
        val ea = tt.medianAt(doses, a)!!
        val eb = tt.medianAt(doses, b)!!

        val one = engine.calibrate(doses, listOf(ttLab(a, ea * 1.5)))
        assertNull(one.tt)
        assertEquals(LabExclusion.TOO_FEW_LABS, one.labs.getValue(one.labs.keys.single()).tt!!.exclusion)

        val two = engine.calibrate(doses, listOf(ttLab(a, ea * 1.5), ttLab(b, eb * 1.5)))
        assertNotNull(two.tt)
        assertEquals(1.5, two.tt!!.scale, 0.05)
        assertEquals(2, two.tt!!.labCount)
    }

    @Test
    fun nobodyWhoDeclinedGetsATestosteroneCalibration() {
        val doses = evDoses()
        val a = start.plus(40, ChronoUnit.DAYS)
        val b = start.plus(55, ChronoUnit.DAYS)
        val r = engine.calibrate(doses, listOf(ttLab(a, 30.0), ttLab(b, 30.0)), status = GonadalStatus.DECLINED)
        assertNull(r.tt)
        assertTrue(r.labs.isEmpty())
    }
}
