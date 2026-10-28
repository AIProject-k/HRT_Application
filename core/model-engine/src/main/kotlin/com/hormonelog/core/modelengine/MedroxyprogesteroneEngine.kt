package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import com.hormonelog.core.evidence.EvidenceStage
import com.hormonelog.core.evidence.MpaModelParameters
import java.time.Instant
import kotlin.math.abs
import kotlin.math.exp

private val EARLIEST_RECORD: Instant = Instant.parse("2000-01-01T00:00:00Z")
private val LATEST_RECORD: Instant = Instant.parse("2100-01-01T00:00:00Z")

/**
 * Oral MPA curve: first-order absorption with a lag, then first-order elimination (the Bateman form), summed over the taken
 * doses. It is a different model from the P4 engine, which interpolates measured points, and it shares no code with it.
 *
 * Not modelled, so not claimed: baseline MPA, active metabolites, between-person variation, food and formulation effects,
 * non-linearity, and any effect of MPA on P4, E2 or testosterone. No uncertainty band is drawn, because none was fitted.
 *
 * The shipped build has no validated parameters and its switch is off, so every answer is Unavailable.
 */
class MedroxyprogesteroneEngine(
    private val parameters: MpaModelParameters?,
    private val stage: EvidenceStage,
    private val enabled: Boolean = false,
) {
    init {
        parameters?.let { require(it.isValid()) { "MPA parameters must be finite and positive" } }
    }

    /** Why no curve may be drawn for these records, or null when the gate is open. */
    fun blockedBy(doses: List<DoseEvent>): ModelReason? {
        val hasDoses = doses.any { it.drug == Drug.MEDROXYPROGESTERONE_ACETATE && it.status.wasTaken }
        return blockingReason(stage, enabled, hasDoses) ?: if (parameters == null) ModelReason.EVIDENCE_INSUFFICIENT else null
    }

    /** The MPA curve over [from]..[to]. Records the model refuses are listed with their reason and never drawn. */
    fun curve(doses: List<DoseEvent>, from: Instant, to: Instant, samples: Int = 160): ModelStatus {
        blockedBy(doses)?.let { return ModelStatus.Unavailable(it, stage) }
        val p = checkNotNull(parameters)
        require(to >= from && samples > 0)
        val excluded = mutableListOf<ExcludedDose>()
        val used = mutableListOf<DoseEvent>()
        for (d in doses.filter { it.drug == Drug.MEDROXYPROGESTERONE_ACETATE }) {
            val reason = refusal(d, to, p)
            if (reason == null) used += d else excluded += ExcludedDose(d, reason)
        }
        // A curve built from no usable dose would read as a zero baseline, so it is withheld instead.
        if (used.isEmpty()) {
            return ModelStatus.Unavailable(if (excluded.isEmpty()) ModelReason.NO_DATA else ModelReason.UNSUPPORTED_RECORDS, stage)
        }
        val span = to.toEpochMilli() - from.toEpochMilli()
        val points = (0..samples).map { from.plusMillis((span.toDouble() * it / samples).toLong()) }.map { at ->
            val value = used.sumOf { concentration(hoursBetween(it.occurredAt, at), it.amountEntered, p) }
            // Equal bounds deliberately draw no uncertainty band; none has been fitted or validated.
            EstimatePoint(at, value, value, value)
        }
        return ModelStatus.Available(EstimateSeries(Hormone.MPA, points), excluded, stage)
    }

    /** Why one MPA record is not drawn, or null when it is used. The order decides which reason is reported. */
    private fun refusal(d: DoseEvent, to: Instant, p: MpaModelParameters): DoseExclusion? = when {
        d.route != Route.ORAL -> DoseExclusion.NO_MODEL
        d.enteredUnit != DoseUnit.MG -> DoseExclusion.UNSUPPORTED_UNIT
        !d.amountEntered.isFinite() || d.amountEntered <= 0.0 -> DoseExclusion.INVALID_AMOUNT
        d.occurredAt < EARLIEST_RECORD || d.occurredAt > LATEST_RECORD -> DoseExclusion.ABNORMAL_DATE
        d.occurredAt.isAfter(to) -> DoseExclusion.FUTURE_DOSE
        d.amountEntered > p.linearMaxMgPerDose -> DoseExclusion.OUTSIDE_LINEAR_RANGE
        !d.status.wasTaken -> DoseExclusion.NOT_TAKEN
        else -> null
    }

    /** Concentration in ng/mL [hours] after one dose: F/V · dose · ka/(ka−ke) · (e^(−ke·t) − e^(−ka·t)). mg/L × 1000 = ng/mL. */
    private fun concentration(hours: Double, mg: Double, p: MpaModelParameters): Double {
        val t = hours - p.lagHours
        if (t <= 0.0) return 0.0
        val ka = p.absorptionPerHour
        val ke = p.eliminationPerHour
        val shape = if (abs(ka - ke) < 1e-12) ka * t * exp(-ka * t) else ka / (ka - ke) * (exp(-ke * t) - exp(-ka * t))
        return mg * p.fOverVPerLitre * shape * 1000.0
    }

    private fun hoursBetween(from: Instant, to: Instant): Double = (to.toEpochMilli() - from.toEpochMilli()) / 3_600_000.0
}

/** What a concentration in ng/mL can say: a true zero, a value under the assay's quantitation limit, or a quantified value. */
enum class ConcentrationKind { ZERO, BELOW_LOQ, QUANTIFIED }

/** Classifies a concentration. A value that is not finite or is negative is refused, never classified. */
fun classifyConcentration(ngPerMl: Double, loqNgPerMl: Double): ConcentrationKind {
    require(ngPerMl.isFinite() && ngPerMl >= 0.0) { "concentration must be finite and not negative" }
    return when {
        ngPerMl == 0.0 -> ConcentrationKind.ZERO
        ngPerMl < loqNgPerMl -> ConcentrationKind.BELOW_LOQ
        else -> ConcentrationKind.QUANTIFIED
    }
}
