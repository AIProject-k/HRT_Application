package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.LabAnalyteValue
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.Route
import com.hormonelog.core.evidence.EvidenceBundleV1
import org.junit.Assert.assertEquals
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

    private fun e2Lab(at: Instant?, measuredPgMl: Double) = LabResult(
        id = UUID.randomUUID(),
        collectedAt = at,
        sourceZoneId = "UTC",
        assay = Assay.LC_MS_MS,
        analytes = listOf(LabAnalyteValue(Analyte.ESTRADIOL, measuredPgMl, "pg/mL", measuredPgMl)),
        note = null,
    )

    @Test
    fun noLabsMeansNoAdjustment() {
        val r = engine.calibrate(evDoses(), emptyList())
        assertEquals(CalibrationLevel.LEVEL_0, r.level)
        assertEquals(1.0, r.exposureScale, 0.0)
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
        assertEquals(1.4, r.exposureScale, 0.05)
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
        assertEquals(1.47, r.exposureScale, 0.1)
    }

    @Test
    fun extremeRatiosAreClamped() {
        val doses = evDoses()
        val at = start.plus(40, ChronoUnit.DAYS)
        val predicted = e2.medianAt(doses, at)!!

        val high = engine.calibrate(doses, listOf(e2Lab(at, predicted * 20)))
        assertEquals(2.0, high.exposureScale, 0.0)

        val low = engine.calibrate(doses, listOf(e2Lab(at, predicted * 0.05)))
        assertEquals(0.5, low.exposureScale, 0.0)
    }

    @Test
    fun ineligibleLabsAreExcludedWithReasons() {
        val doses = evDoses()
        val noTime = e2Lab(null, 200.0)
        val beforeAnyDose = e2Lab(start.minus(5, ChronoUnit.DAYS), 200.0)
        val r = engine.calibrate(doses, listOf(noTime, beforeAnyDose))

        assertEquals(CalibrationLevel.LEVEL_0, r.level)
        assertEquals(1.0, r.exposureScale, 0.0)
        assertTrue(r.excluded[noTime.id]!!.contains("시각"))
        assertTrue(r.excluded[beforeAnyDose.id]!!.contains("이전 투약"))
    }
}
