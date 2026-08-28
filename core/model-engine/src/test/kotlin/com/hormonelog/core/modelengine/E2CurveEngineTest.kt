package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
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
    fun deterministic() {
        val d = listOf(dose(Route.IM_INJECTION, Drug.ESTRADIOL_VALERATE, 5.0, t0))
        val a = engine.curve(d, t0, t0.plus(14, ChronoUnit.DAYS))
        val b = engine.curve(d, t0, t0.plus(14, ChronoUnit.DAYS))
        assertEquals(a, b)
    }
}
