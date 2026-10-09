package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.isAntiandrogen
import java.time.Duration
import java.time.Instant

enum class CycleSpot { NEAR_PEAK, NEAR_TROUGH, MIDDLE }

/** Where a moment sits inside its dosing cycle: how long after the dose, and near which extreme. */
data class CyclePosition(val spot: CycleSpot, val hoursSinceDose: Double)

/** The expected highs and lows of one dosing cycle, each with its own interval. */
data class CycleStats(
    val start: Instant,
    val end: Instant,
    val peak: EstimatePoint,
    val trough: EstimatePoint,
)

/**
 * Peaks, troughs and "where in the cycle is this blood draw" for the estrogen the person
 * takes. A cycle runs from one dose of a product to the next dose of the *same* product
 * (route and drug), so a daily tablet next to a fortnightly injection cannot confuse it.
 * Pass the planned doses along with the real ones to see the cycle that has not ended yet.
 */
class CycleAnalysis(private val engine: E2CurveEngine) {

    fun stats(doses: List<DoseEvent>, at: Instant, adjustments: Adjustments = emptyMap()): CycleStats? {
        val (start, end) = window(doses, at) ?: return null
        val points = samples(doses, start, end, adjustments) ?: return null
        return CycleStats(start, end, peak = points.maxBy { it.median }, trough = points.minBy { it.median })
    }

    fun position(doses: List<DoseEvent>, at: Instant, adjustments: Adjustments = emptyMap()): CyclePosition? {
        val (start, end) = window(doses, at) ?: return null
        val points = samples(doses, start, end, adjustments) ?: return null
        val length = Duration.between(start, end).toMillis().toDouble()
        val peakAt = points.maxBy { it.median }.at
        val sinceStart = Duration.between(start, at).toMillis().toDouble()
        val spot = when {
            kotlin.math.abs(Duration.between(peakAt, at).toMillis()) <= NEAR * length -> CycleSpot.NEAR_PEAK
            length - sinceStart <= NEAR * length -> CycleSpot.NEAR_TROUGH
            else -> CycleSpot.MIDDLE
        }
        return CyclePosition(spot, sinceStart / 3_600_000.0)
    }

    private fun samples(doses: List<DoseEvent>, start: Instant, end: Instant, adjustments: Adjustments): List<EstimatePoint>? =
        (engine.curve(doses, start, end, samples = SAMPLES, adjustments = adjustments) as? EstimateResult.Available)
            ?.series?.points?.takeIf { it.isNotEmpty() }

    private fun window(doses: List<DoseEvent>, at: Instant): Pair<Instant, Instant>? {
        val candidates = doses.filter { it.status.wasTaken && !it.drug.isAntiandrogen }
        val left = engine.exclusionsIn(candidates).map { it.dose.id }.toSet()
        val estrogen = candidates.filter { it.id !in left }
        val previous = estrogen.filter { !it.occurredAt.isAfter(at) }.maxByOrNull { it.occurredAt } ?: return null
        val sameProduct = estrogen.filter { it.drug == previous.drug && it.route == previous.route }.sortedBy { it.occurredAt }
        val end = sameProduct.firstOrNull { it.occurredAt.isAfter(at) }?.occurredAt ?: run {
            // No next dose is planned: assume the usual gap between the last few real ones.
            val gaps = sameProduct.filter { !it.occurredAt.isAfter(at) }
                .zipWithNext { a, b -> Duration.between(a.occurredAt, b.occurredAt) }
                .takeLast(4)
                .sorted()
            if (gaps.isEmpty()) return null
            previous.occurredAt.plus(gaps[gaps.size / 2])
        }
        return if (end.isAfter(previous.occurredAt)) previous.occurredAt to end else null
    }

    private companion object {
        const val SAMPLES = 96

        /** Within this share of a cycle of the extreme counts as "near" it. */
        const val NEAR = 0.2
    }
}
