package com.hormonelog.core.modelengine

import java.time.Instant

enum class Hormone { E2, TOTAL_T }

data class EstimatePoint(
    val at: Instant,
    val median: Double,
    val lower: Double,
    val upper: Double,
)

data class EstimateSeries(
    val hormone: Hormone,
    val points: List<EstimatePoint>,
) {
    fun medianAt(instant: Instant): Double? {
        if (points.isEmpty()) return null
        return points.minByOrNull { kotlin.math.abs(it.at.toEpochMilli() - instant.toEpochMilli()) }?.median
    }
}

sealed interface EstimateResult {
    data class Available(val series: EstimateSeries) : EstimateResult
    data class Unavailable(val reason: ModelUnavailableReason) : EstimateResult
    data class Blocked(val reasons: Set<String>) : EstimateResult
}

enum class ModelUnavailableReason {
    MISSING_EVIDENCE,
    NO_DATA,
    ROUTE_UNSUPPORTED,
}
