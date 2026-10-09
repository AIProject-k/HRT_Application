package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import com.hormonelog.core.evidence.EvidenceBundleV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class CycleAnalysisTest {
    private val engine = E2CurveEngine(EvidenceBundleV1.bundle)
    private val cycles = CycleAnalysis(engine)
    private val t0 = Instant.parse("2026-06-01T09:00:00Z")

    private fun dose(drug: Drug, route: Route, mg: Double, at: Instant) = DoseEvent(
        UUID.randomUUID(), at, "UTC", drug, route, mg, DoseUnit.MG, mg, DoseStatus.ADMINISTERED,
    )

    private fun weekly(count: Int) = (0 until count).map {
        dose(Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 5.0, t0.plus((it * 7).toLong(), ChronoUnit.DAYS))
    }

    @Test
    fun aWeeklyInjectionHasAPeakAboveItsTrough() {
        val doses = weekly(6)
        val at = t0.plus(37, ChronoUnit.DAYS)

        val s = cycles.stats(doses, at)!!
        assertEquals(t0.plus(35, ChronoUnit.DAYS), s.start)
        assertEquals(t0.plus(42, ChronoUnit.DAYS), s.end)
        assertTrue("peak ${s.peak.median} > trough ${s.trough.median}", s.peak.median > s.trough.median + 20)
        assertTrue(s.peak.lower < s.peak.median && s.peak.median < s.peak.upper)
    }

    @Test
    fun withoutAPlannedNextDoseTheUsualGapIsAssumed() {
        val doses = weekly(4)
        val s = cycles.stats(doses, t0.plus(23, ChronoUnit.DAYS))!!
        assertEquals(t0.plus(21, ChronoUnit.DAYS), s.start)
        assertEquals(t0.plus(28, ChronoUnit.DAYS), s.end)
    }

    @Test
    fun aSingleDoseHasNoCycleToSpeakOf() {
        assertNull(cycles.stats(weekly(1), t0.plus(2, ChronoUnit.DAYS)))
        assertNull(cycles.stats(emptyList(), t0))
    }

    @Test
    fun aDrawJustAfterTheInjectionPeakIsNearThePeak() {
        val doses = weekly(6)
        val p = cycles.position(doses, t0.plus(35, ChronoUnit.DAYS).plus(29, ChronoUnit.HOURS))!!
        assertEquals(CycleSpot.NEAR_PEAK, p.spot)
        assertEquals(29.0, p.hoursSinceDose, 0.01)
    }

    @Test
    fun aDrawJustBeforeTheNextInjectionIsNearTheTrough() {
        val p = cycles.position(weekly(7), t0.plus(41, ChronoUnit.DAYS).plus(12, ChronoUnit.HOURS))!!
        assertEquals(CycleSpot.NEAR_TROUGH, p.spot)
    }

    @Test
    fun aDrawInTheMiddleOfTheFallIsNeitherExtreme() {
        val p = cycles.position(weekly(7), t0.plus(35, ChronoUnit.DAYS).plus(4, ChronoUnit.DAYS))!!
        assertEquals(CycleSpot.MIDDLE, p.spot)
    }

    @Test
    fun anAntiandrogenTabletDoesNotDefineTheCycle() {
        val cpa = (0 until 40).map { dose(Drug.CYPROTERONE, Route.ORAL, 12.5, t0.plus(it.toLong(), ChronoUnit.DAYS)) }
        val s = cycles.stats(weekly(6) + cpa, t0.plus(37, ChronoUnit.DAYS))!!
        assertEquals(t0.plus(35, ChronoUnit.DAYS), s.start)
    }
}
