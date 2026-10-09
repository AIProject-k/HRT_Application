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
    val source: RecordSource = RecordSource.MANUAL,
    val site: InjectionSite? = null,
    /** Only for a patch: how often it is changed. */
    val patchCycle: PatchCycle? = null,
) {
    /**
     * µg/day for a patch, or null when the entered amount cannot be read as one
     * (a bare patch count says nothing about its strength).
     *
     * Older builds labelled a patch's strength "mg/일", so a stored 50 "mg/일" is
     * really 50 µg/day; a value that small can only be a genuine mg amount.
     */
    val patchMicrogramsPerDay: Double? get() = patchMicrograms(amountEntered, enteredUnit)

    companion object {
        private const val LEGACY_PATCH_MG_LABEL_THRESHOLD = 5.0

        /** µg/day read from an entered amount, or null when the unit says nothing about a patch's strength. */
        fun patchMicrograms(amount: Double, unit: DoseUnit): Double? = when (unit) {
            DoseUnit.UG_PER_DAY -> amount
            DoseUnit.MG_PER_DAY -> if (amount >= LEGACY_PATCH_MG_LABEL_THRESHOLD) amount else amount * 1000.0
            DoseUnit.MG, DoseUnit.PATCH -> null
        }

        /** mg-equivalent when the entered unit is a plain mass; null otherwise. */
        fun normalizeMilligrams(amountEntered: Double, unit: DoseUnit): Double? = when (unit) {
            DoseUnit.MG -> amountEntered
            DoseUnit.MG_PER_DAY, DoseUnit.PATCH, DoseUnit.UG_PER_DAY -> null
        }
    }
}

object HistoricalReconstruction {
    fun administrationsFrom(events: List<DoseEvent>): List<DoseEvent> =
        events.filter { it.status.wasTaken }
}
