package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import java.time.Instant

enum class Hormone { E2, TOTAL_T, P4, MPA }

data class EstimatePoint(
    val at: Instant,
    val median: Double,
    val lower: Double,
    val upper: Double,
)

/** A span with no estimate, [from] inclusive and [until] exclusive: a record the model cannot sum still reaches it. */
data class EstimateGap(val from: Instant, val until: Instant) {
    fun covers(at: Instant): Boolean = !at.isBefore(from) && at.isBefore(until)

    /** True when this gap starts between [a] and [b], so a value at one says nothing about the other. */
    fun separates(a: Instant, b: Instant): Boolean = from.isAfter(minOf(a, b)) && !from.isAfter(maxOf(a, b))
}

data class EstimateSeries(
    val hormone: Hormone,
    val points: List<EstimatePoint>,
    val gaps: List<EstimateGap> = emptyList(),
) {
    fun medianAt(instant: Instant): Double? = pointAt(instant)?.median

    /** The nearest point that no gap separates from [instant]; none inside a gap. */
    fun pointAt(instant: Instant): EstimatePoint? {
        if (gaps.any { it.covers(instant) }) return null
        val reachable = if (gaps.isEmpty()) points else points.filter { p -> gaps.none { it.separates(p.at, instant) } }
        return reachable.minByOrNull { kotlin.math.abs(it.at.toEpochMilli() - instant.toEpochMilli()) }
    }

    /** The points cut wherever a gap starts between two neighbours, so a line is never drawn across one. */
    fun segments(): List<List<EstimatePoint>> {
        if (gaps.isEmpty()) return listOf(points)
        val out = mutableListOf<MutableList<EstimatePoint>>()
        for (p in points) {
            val last = out.lastOrNull()?.last()
            if (last == null || gaps.any { it.separates(last.at, p.at) }) out += mutableListOf(p) else out.last() += p
        }
        return out
    }
}

/** Why one recorded dose is not part of the curve; the recorder keeps it regardless. */
enum class DoseExclusion {
    /** The route has no evidence-backed model (gel). */
    NO_MODEL,

    /** The drug and route cannot be paired in any model. */
    UNSUPPORTED_COMBINATION,

    /** A patch whose strength (µg/day) is not known. */
    PATCH_STRENGTH_MISSING,

    /** Oral P4 outside the model's range that cannot reach the shown window above display precision. */
    OUTSIDE_INFLUENCE,

    /** The dose was skipped, so nothing entered the body. */
    NOT_TAKEN,

    /** Dated after the window it is drawn in, so it cannot have acted yet. */
    FUTURE_DOSE,

    /** The amount is missing, zero, negative, or not finite. */
    INVALID_AMOUNT,

    /** The date is outside the range a personal record can have. */
    ABNORMAL_DATE,

    /** The unit cannot be converted to milligrams. */
    UNSUPPORTED_UNIT,

    /** The amount is above the dose range where linearity was checked. */
    OUTSIDE_LINEAR_RANGE,
}

data class ExcludedDose(val dose: DoseEvent, val reason: DoseExclusion)

sealed interface EstimateResult {
    /** [excluded] doses were left out of [series] and are reported rather than hidden. */
    data class Available(val series: EstimateSeries, val excluded: List<ExcludedDose> = emptyList()) : EstimateResult

    data class Unavailable(val reason: ModelUnavailableReason) : EstimateResult
}

enum class ModelUnavailableReason {
    MISSING_EVIDENCE,
    NO_DATA,
    ROUTE_UNSUPPORTED,

    /** A recorded dose's amount or unit is outside the model and still reaches the shown window. */
    UNSUPPORTED_DOSE,

    /** The user chose not to share what the model needs (Total T without gonadal status). */
    HIDDEN_BY_USER,
}
