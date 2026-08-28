package com.hormonelog.core.domain

import java.time.Instant
import java.util.UUID

/**
 * An actual administration event. Immutable after first save except by an explicit
 * edit revision. Both the entered amount/unit and the normalized value are kept so a
 * failed or impossible conversion never silently loses the original record.
 */
data class DoseEvent(
    val id: UUID,
    val occurredAt: Instant,
    val sourceZoneId: String,
    val drug: Drug,
    val route: Route,
    val amountEntered: Double,
    val enteredUnit: DoseUnit,
    val normalizedMilligrams: Double?,
    val status: DoseStatus,
    val note: String? = null,
    val revision: Int = 1,
) {
    companion object {
        /** mg-equivalent when the entered unit is a plain mass; null otherwise. */
        fun normalizeMilligrams(amountEntered: Double, unit: DoseUnit): Double? = when (unit) {
            DoseUnit.MG -> amountEntered
            DoseUnit.MG_PER_DAY, DoseUnit.PATCH -> null
        }
    }
}

object HistoricalReconstruction {
    fun administrationsFrom(events: List<DoseEvent>): List<DoseEvent> =
        events.filter { it.status == DoseStatus.ADMINISTERED || it.status == DoseStatus.CORRECTED }
}
