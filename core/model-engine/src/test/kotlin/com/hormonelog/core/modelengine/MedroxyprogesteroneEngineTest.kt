package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import com.hormonelog.core.evidence.EvidenceStage
import com.hormonelog.core.evidence.MedroxyprogesteroneEvidence
import com.hormonelog.core.evidence.MpaModelParameters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.util.UUID
import kotlin.math.exp
import kotlin.math.ln

class MedroxyprogesteroneEngineTest {
    private val t0 = Instant.parse("2026-10-17T06:00:00Z")
    private val day = 86400L

    // Synthetic numbers for these tests only. They are not MPA values and must never ship.
    private val synthetic = MpaModelParameters(
        absorptionPerHour = 0.5, eliminationPerHour = 0.1, lagHours = 0.0,
        fOverVPerLitre = 0.05, linearMaxMgPerDose = 40.0, loqNgPerMl = 0.02,
    )
    private val validated = MedroxyprogesteroneEngine(synthetic, EvidenceStage.MODEL_VALIDATED, enabled = true)

    private fun mpa(
        at: Instant = t0,
        mg: Double = 10.0,
        status: DoseStatus = DoseStatus.ADMINISTERED,
        route: Route = Route.ORAL,
        unit: DoseUnit = DoseUnit.MG,
        amount: Double = mg,
    ) = DoseEvent(UUID.randomUUID(), at, "UTC", Drug.MEDROXYPROGESTERONE_ACETATE, route, amount, unit, amount, status)

    private fun series(doses: List<DoseEvent>, from: Instant, to: Instant, samples: Int = 24): EstimateSeries =
        (validated.curve(doses, from, to, samples) as ModelStatus.Available).series

    /** The Bateman form written out once more, so the engine is checked against the formula rather than against itself. */
    private fun closedForm(hours: Double, mg: Double): Double {
        val ka = 0.5
        val ke = 0.1
        return mg * 0.05 * ka / (ka - ke) * (exp(-ke * hours) - exp(-ka * hours)) * 1000.0
    }

    @Test fun theShippedBuildHasNoValidatedParametersAndSaysSo() {
        val shipped = MedroxyprogesteroneEngine(MedroxyprogesteroneEvidence.validated, MedroxyprogesteroneEvidence.stage)
        assertEquals(
            ModelStatus.Unavailable(ModelReason.EVIDENCE_INSUFFICIENT, EvidenceStage.PK_SUMMARY),
            shipped.curve(listOf(mpa()), t0, t0.plusSeconds(day)),
        )
    }

    @Test fun aSwitchedOffModelDrawsNothingEvenWithValidatedNumbers() {
        val off = MedroxyprogesteroneEngine(synthetic, EvidenceStage.MODEL_VALIDATED, enabled = false)
        assertEquals(
            ModelStatus.Unavailable(ModelReason.MODEL_DISABLED, EvidenceStage.MODEL_VALIDATED),
            off.curve(listOf(mpa()), t0, t0.plusSeconds(day)),
        )
    }

    @Test fun aSingleDoseRisesAfterItsLagAndPeaksWhereTheBatemanFormPlacesIt() {
        val values = series(listOf(mpa()), t0, t0.plusSeconds(day)).points.map { it.median }
        assertEquals(0.0, values.first(), 0.0)
        val peakHour = values.indices.maxByOrNull { values[it] }!!
        assertEquals(4, peakHour)
        assertEquals(closedForm(4.0, 10.0), values[peakHour], 1e-9)
    }

    @Test fun dosesAddLinearlyAndSevenDailyDosesAccumulate() {
        val seven = (0 until 7).map { mpa(at = t0.plusSeconds(it * day)) }
        val together = series(seven, t0, t0.plusSeconds(8 * day), samples = 192)
        val separate = seven.map { series(listOf(it), t0, t0.plusSeconds(8 * day), samples = 192) }
        for (i in together.points.indices) {
            assertEquals(separate.sumOf { it.points[i].median }, together.points[i].median, 1e-9)
        }
        val beforeSecond = together.medianAt(t0.plusSeconds(day - 3600))!!
        val beforeSeventh = together.medianAt(t0.plusSeconds(6 * day - 3600))!!
        assertTrue(beforeSeventh > beforeSecond)
    }

    @Test fun aMissedDoseAddsNothingAndAnEditedDoseCountsAsTaken() {
        val taken = mpa(at = t0)
        val missed = mpa(at = t0.plusSeconds(day), status = DoseStatus.SKIPPED)
        val edited = mpa(at = t0.plusSeconds(2 * day), status = DoseStatus.CORRECTED)
        val to = t0.plusSeconds(3 * day)
        val onlyTaken = validated.curve(listOf(taken), t0, to) as ModelStatus.Available
        val withMissed = validated.curve(listOf(taken, missed), t0, to) as ModelStatus.Available
        assertEquals(onlyTaken.series, withMissed.series)
        assertEquals(listOf(ExcludedDose(missed, DoseExclusion.NOT_TAKEN)), withMissed.excluded)
        val withEdited = validated.curve(listOf(taken, edited), t0, to) as ModelStatus.Available
        assertFalse(withEdited.excluded.any { it.dose == edited })
        assertTrue(withEdited.series.medianAt(t0.plusSeconds(2 * day + 3600))!! > onlyTaken.series.medianAt(t0.plusSeconds(2 * day + 3600))!!)
    }

    @Test fun everyBoundaryInputIsRefusedWithItsReasonAndNeverDrawn() {
        val valid = mpa()
        val to = t0.plusSeconds(day)
        fun refusedFor(d: DoseEvent): DoseExclusion? {
            val answer = validated.curve(listOf(valid, d), t0, to) as ModelStatus.Available
            return answer.excluded.singleOrNull { it.dose == d }?.reason
        }
        assertEquals(DoseExclusion.NO_MODEL, refusedFor(mpa(route = Route.SUBLINGUAL)))
        assertEquals(DoseExclusion.UNSUPPORTED_UNIT, refusedFor(mpa(unit = DoseUnit.UG_PER_DAY)))
        assertEquals(DoseExclusion.INVALID_AMOUNT, refusedFor(mpa(amount = Double.NaN)))
        assertEquals(DoseExclusion.INVALID_AMOUNT, refusedFor(mpa(amount = Double.POSITIVE_INFINITY)))
        assertEquals(DoseExclusion.INVALID_AMOUNT, refusedFor(mpa(amount = -5.0)))
        assertEquals(DoseExclusion.INVALID_AMOUNT, refusedFor(mpa(amount = 0.0)))
        assertEquals(DoseExclusion.ABNORMAL_DATE, refusedFor(mpa(at = Instant.parse("1800-01-01T00:00:00Z"))))
        assertEquals(DoseExclusion.FUTURE_DOSE, refusedFor(mpa(at = t0.plusSeconds(2 * day))))
        assertEquals(DoseExclusion.OUTSIDE_LINEAR_RANGE, refusedFor(mpa(mg = 50.0)))
    }

    @Test fun aDayWithNoUsableMpaDoseIsWithheldNotDrawnAsZero() {
        assertEquals(
            ModelStatus.Unavailable(ModelReason.NO_DATA, EvidenceStage.MODEL_VALIDATED),
            validated.curve(listOf(mpa(status = DoseStatus.SKIPPED)), t0, t0.plusSeconds(day)),
        )
        assertEquals(
            ModelStatus.Unavailable(ModelReason.UNSUPPORTED_RECORDS, EvidenceStage.MODEL_VALIDATED),
            validated.curve(listOf(mpa(at = t0.plusSeconds(2 * day))), t0, t0.plusSeconds(day)),
        )
    }

    @Test fun progesteroneRecordsAreNotMpaAndNeverReachThisModel() {
        val p4 = DoseEvent(UUID.randomUUID(), t0, "UTC", Drug.PROGESTERONE_MICRONIZED, Route.ORAL, 200.0, DoseUnit.MG, 200.0, DoseStatus.ADMINISTERED)
        assertEquals(
            ModelStatus.Unavailable(ModelReason.NO_DATA, EvidenceStage.MODEL_VALIDATED),
            validated.curve(listOf(p4), t0, t0.plusSeconds(day)),
        )
    }

    @Test fun noRangeIsDrawnAroundAnyPoint() {
        assertTrue(series(listOf(mpa()), t0, t0.plusSeconds(day)).points.all { it.lower == it.median && it.upper == it.median })
    }

    @Test fun zeroAndBelowTheQuantificationLimitAreDifferentStatements() {
        assertEquals(ConcentrationKind.ZERO, classifyConcentration(0.0, 0.02))
        assertEquals(ConcentrationKind.BELOW_LOQ, classifyConcentration(0.01, 0.02))
        assertEquals(ConcentrationKind.QUANTIFIED, classifyConcentration(0.5, 0.02))
    }

    @Test fun nonFiniteOrNegativeConcentrationsAreRejectedNotClassified() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, -0.1)) {
            assertTrue(value.toString(), runCatching { classifyConcentration(value, 0.02) }.isFailure)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidParametersAreRefusedWhenTheEngineIsBuilt() {
        MedroxyprogesteroneEngine(synthetic.copy(absorptionPerHour = Double.NaN), EvidenceStage.MODEL_VALIDATED, enabled = true)
    }
}
