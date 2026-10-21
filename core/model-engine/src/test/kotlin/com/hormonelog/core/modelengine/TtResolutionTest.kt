package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.*
import com.hormonelog.core.evidence.EvidenceBundleV1
import java.time.Instant
import java.util.UUID
import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class TtResolutionTest {
    private val bundle = EvidenceBundleV1.bundle
    private val start = Instant.parse("2026-07-01T09:07:00Z")
    private fun doses() = (0..19).map { day ->
        DoseEvent(UUID.randomUUID(), start.plusSeconds(day * 86400L), "UTC",
            Drug.ESTRADIOL_TABLET, Route.SUBLINGUAL, 2.0, DoseUnit.MG, 2.0, DoseStatus.ADMINISTERED)
    }

    @Test fun fiveMinuteCalculationConvergesAgainstOneMinuteAtOffGridDoseTimes() {
        val doses = doses()
        val from = start.plusSeconds(19 * 86400L - 60)
        val to = from.plusSeconds(86400)
        fun curve(step: Int) = (TtSuppressionEngine(bundle, step).curve(doses, from, to, samples = 96) as EstimateResult.Available).series
        val reference = curve(1)
        val five = curve(5)
        assertTrue(reference.points.zip(five.points).maxOf { (a, b) -> abs(a.median-b.median) } < 0.1)
        val default = (TtSuppressionEngine(bundle).curve(doses, from, to, samples = 96) as EstimateResult.Available).series
        val maxError = reference.points.zip(default.points).maxOf { (a, b) -> abs(a.median-b.median) }
        assertTrue("Default numerical error $maxError exceeds 0.1 ng/dL in this synthetic scenario", maxError < 0.1)
    }

    @Test fun integrationResolutionDoesNotChangeOutputTimesOrFutureDoseCausality() {
        val taken = doses()
        val at = start.plusSeconds(20 * 86400L + 60)
        val future = taken.last().copy(occurredAt = at.plusSeconds(60))
        for (step in listOf(60, 15, 5)) {
            val engine = TtSuppressionEngine(bundle, step)
            assertEquals(engine.estimateAt(taken, at)!!.median, engine.estimateAt(taken + future, at)!!.median, 1e-9)
            val series = (engine.curve(taken, at, at.plusSeconds(3600), samples = 4) as EstimateResult.Available).series
            assertEquals((0..4).map { at.plusSeconds(it * 900L) }, series.points.map { it.at })
        }
    }

    @Test fun invalidIntegrationStepsAreRejected() {
        for (step in listOf(0, -5, 7, 61)) {
            assertThrows(IllegalArgumentException::class.java) { TtSuppressionEngine(bundle, step) }
        }
    }
}
