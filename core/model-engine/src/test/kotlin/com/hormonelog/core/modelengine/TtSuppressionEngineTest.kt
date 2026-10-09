package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.Route
import com.hormonelog.core.evidence.EvidenceBundleV1
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtSuppressionEngineTest {
    private val engine = TtSuppressionEngine(EvidenceBundleV1.bundle)
    private val start = Instant.parse("2026-05-01T09:00:00Z")
    private val now = start.plus(90, ChronoUnit.DAYS)

    private fun dose(route: Route, drug: Drug, mg: Double, at: Instant) = DoseEvent(
        id = UUID.randomUUID(), occurredAt = at, sourceZoneId = "UTC", drug = drug, route = route,
        amountEntered = mg, enteredUnit = DoseUnit.MG,
        normalizedMilligrams = DoseEvent.normalizeMilligrams(mg, DoseUnit.MG), status = DoseStatus.ADMINISTERED,
    )

    private fun evBiweekly() = (0..6).map { dose(Route.IM_INJECTION, Drug.ESTRADIOL_VALERATE, 10.0, start.plus((it * 14).toLong(), ChronoUnit.DAYS)) }
    private fun cpaDaily() = (0..89).map { dose(Route.ORAL, Drug.CYPROTERONE, 25.0, start.plus(it.toLong(), ChronoUnit.DAYS)) }

    private fun lastMonthMax(r: EstimateResult): Double {
        r as EstimateResult.Available
        val cutoff = now.minus(30, ChronoUnit.DAYS)
        return r.series.points.filter { it.at.isAfter(cutoff) }.maxOf { it.median }
    }

    @Test
    fun injectableMonotherapyLandsNearTheLiteratureMedian() {
        // Seifert 2025: injectable monotherapy, median total T 17 ng/dL (IQR 10–33).
        val t = lastMonthMax(engine.curve(evBiweekly(), start.plus(30, ChronoUnit.DAYS), now))
        assertTrue("T $t ng/dL", t in 8.0..60.0)
    }

    @Test
    fun addingCyproteroneSuppressesFurtherIntoTheDeepFemaleRange() {
        val e2Only = lastMonthMax(engine.curve(evBiweekly(), start.plus(30, ChronoUnit.DAYS), now))
        val withCpa = lastMonthMax(engine.curve(evBiweekly() + cpaDaily(), start.plus(30, ChronoUnit.DAYS), now))
        assertTrue("withCpa $withCpa should be < e2Only $e2Only", withCpa < e2Only)
        // ENIGI: CPA + estrogen -> ~15–25 ng/dL.
        assertTrue("withCpa $withCpa ng/dL", withCpa < 40.0)
    }

    @Test
    fun postOrchiectomyStartsFromALowBaseline() {
        val r = engine.curve(evBiweekly(), start.plus(30, ChronoUnit.DAYS), now, status = GonadalStatus.POST_ORCHIECTOMY)
        r as EstimateResult.Available
        assertTrue(r.series.points.all { it.median <= 30.0 })
    }

    @Test
    fun nobodyWhoDeclinedToSayGetsACurve() {
        assertEquals(
            EstimateResult.Unavailable(ModelUnavailableReason.HIDDEN_BY_USER),
            engine.curve(evBiweekly(), start.plus(30, ChronoUnit.DAYS), now, status = GonadalStatus.DECLINED),
        )
    }

    @Test
    fun aMeasuredStartingPointReplacesTheLiteratureOneForSomeoneWithTestes() {
        // Before E2 has built up, the curve sits at the person's own baseline.
        val first = start.plus(1, ChronoUnit.DAYS)
        fun early(baselineT: Double?): Double {
            val r = engine.curve(evBiweekly(), start, first, baselineT = baselineT) as EstimateResult.Available
            return r.series.points.first().median
        }
        assertEquals(450.0, early(450.0), 10.0)
        assertEquals(600.0, early(null), 10.0)
        // A number that cannot be a real starting point (a unit slip) is held to a sane range.
        assertEquals(1200.0, early(99999.0), 15.0)
        assertEquals(150.0, early(10.0), 5.0)
    }

    @Test
    fun aMeasuredBaselineDoesNotApplyAfterAnOrchiectomy() {
        val a = engine.curve(evBiweekly(), start, start.plus(1, ChronoUnit.DAYS), status = GonadalStatus.POST_ORCHIECTOMY, baselineT = 900.0) as EstimateResult.Available
        assertTrue(a.series.points.first().median <= 30.0)
    }

    @Test
    fun aMeasuredTestosteroneCorrectionScalesTheCurveAndItsInterval() {
        val from = start.plus(30, ChronoUnit.DAYS)
        val base = engine.curve(evBiweekly(), from, now) as EstimateResult.Available
        val scaled = engine.curve(evBiweekly(), from, now, tAdjustment = TtAdjustment(scale = 1.5, shrink = 0.6)) as EstimateResult.Available

        val b = base.series.points.last()
        val s = scaled.series.points.last()
        assertEquals(b.median * 1.5, s.median, 1e-6)
        assertTrue((s.upper - s.lower) / s.median < (b.upper - b.lower) / b.median)
    }

    @Test
    fun noDosesPropagatesUnavailable() {
        assertEquals(
            EstimateResult.Unavailable(ModelUnavailableReason.NO_DATA),
            engine.curve(emptyList(), start, now),
        )
    }
}
