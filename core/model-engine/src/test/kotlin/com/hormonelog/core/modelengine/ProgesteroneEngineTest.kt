package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.*
import com.hormonelog.core.evidence.EvidenceBundle
import com.hormonelog.core.evidence.ProgesteroneEvidence
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ProgesteroneEngineTest {
    private val t = Instant.parse("2026-10-17T00:00:00Z")
    private val engine = ProgesteroneEngine(ProgesteroneEvidence.bundle)
    private fun dose(mg: Double = 200.0, at: Instant = t) = DoseEvent(
        UUID.randomUUID(), at, "UTC", Drug.PROGESTERONE_MICRONIZED, Route.ORAL,
        mg, DoseUnit.MG, mg, DoseStatus.ADMINISTERED,
    )

    @Test fun observedPointsSubtractTheStudyBaselineOncePerDose() {
        for ((hour, total) in listOf(0 to 0.13, 1 to 4.25, 2 to 11.75, 4 to 8.37, 6 to 2.0, 8 to 1.64)) {
            assertEquals(total - 0.13, engine.estimateAt(listOf(dose()), t.plusSeconds(hour * 3600L))!!.median, 1e-8)
        }
    }

    @Test fun interpolatesAndScalesWithinTheSupportedDoseRange() {
        assertEquals((4.25 + 11.75) / 2 - 0.13, engine.estimateAt(listOf(dose()), t.plusSeconds(5400))!!.median, 1e-8)
        assertEquals((11.75 - 0.13) / 2, engine.estimateAt(listOf(dose(100.0)), t.plusSeconds(7200))!!.median, 1e-8)
    }

    @Test fun tailIsContinuousPositiveAndDecays() {
        val eight = engine.estimateAt(listOf(dose()), t.plusSeconds(8 * 3600))!!.median
        val later = engine.estimateAt(listOf(dose()), t.plusSeconds(9 * 3600))!!.median
        assertTrue(later > 0 && later < eight)
        assertEquals(eight, engine.estimateAt(listOf(dose()), t.plusSeconds(8 * 3600 + 1))!!.median, 0.001)
    }

    @Test fun repeatedDosesSumWithoutInventingAConfidenceInterval() {
        val at = t.plusSeconds(26 * 3600)
        val first = engine.estimateAt(listOf(dose()), at)!!.median
        val second = engine.estimateAt(listOf(dose(at = t.plusSeconds(24 * 3600))), at)!!.median
        val sum = engine.estimateAt(listOf(dose(), dose(at = t.plusSeconds(24 * 3600))), at)!!
        assertEquals(first + second, sum.median, 1e-8)
        assertEquals(sum.median, sum.lower, 0.0)
        assertEquals(sum.median, sum.upper, 0.0)
    }

    @Test fun missingEvidenceUnsupportedDoseOrRouteRefuseTheEstimate() {
        assertNull(ProgesteroneEngine(EvidenceBundle.empty("test")).estimateAt(listOf(dose()), t))
        val missing = ProgesteroneEvidence.bundle.copy(parameters = ProgesteroneEvidence.bundle.parameters.filter { it.name != "hour2" })
        assertNull(ProgesteroneEngine(missing).estimateAt(listOf(dose()), t))
        for (mg in listOf(50.0, 400.0, Double.NaN)) assertNull(engine.estimateAt(listOf(dose(mg)), t))
        assertNull(engine.estimateAt(listOf(dose().copy(route = Route.SUBLINGUAL)), t))
        assertNull(engine.estimateAt(listOf(dose(), dose(400.0)), t))
    }

    @Test fun noProgesteroneSkippedAndFutureDosesDoNotCreateAPresentEstimate() {
        assertNull(engine.estimateAt(emptyList(), t))
        assertNull(engine.estimateAt(listOf(dose().copy(status = DoseStatus.SKIPPED)), t))
        assertNull(engine.estimateAt(listOf(dose(at = t.plusSeconds(3600))), t))
    }

    @Test fun denseChartRetainsTwoHourPeaksEvenAcrossNinetyDays() {
        val series = (engine.curve(listOf(dose()), t, t.plusSeconds(90L * 86400)) as EstimateResult.Available).series
        assertEquals(11.62, series.points.first { it.at == t.plusSeconds(7200) }.median, 1e-8)
    }

    @Test fun p4DoesNotChangeE2OrTOrCalibration() {
        val e = dose(5.0).copy(drug = Drug.ESTRADIOL_VALERATE, route = Route.IM_INJECTION)
        val all = listOf(e, dose())
        val e2 = E2CurveEngine(com.hormonelog.core.evidence.EvidenceBundleV1.bundle)
        val tt = TtSuppressionEngine(com.hormonelog.core.evidence.EvidenceBundleV1.bundle)
        val at = t.plusSeconds(7200)
        assertEquals(e2.estimateAt(listOf(e), at), e2.estimateAt(all, at))
        assertEquals(tt.estimateAt(listOf(e), at, GonadalStatus.INTACT), tt.estimateAt(all, at, GonadalStatus.INTACT))
        assertTrue(e2.exclusionsIn(all).isEmpty())
        assertNull(e2.estimateAt(listOf(dose()), at))
        assertNull(tt.estimateAt(listOf(dose()), at, GonadalStatus.INTACT))
        val lab = LabResult(UUID.randomUUID(), at, "UTC", Assay.LC_MS_MS,
            listOf(LabAnalyteValue(Analyte.ESTRADIOL, 200.0, "pg/mL", 200.0)))
        val calibration = CalibrationEngine(com.hormonelog.core.evidence.EvidenceBundleV1.bundle)
        assertEquals(calibration.calibrate(listOf(e), listOf(lab)), calibration.calibrate(all, listOf(lab)))
    }
}
