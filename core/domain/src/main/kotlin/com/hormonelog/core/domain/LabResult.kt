package com.hormonelog.core.domain

import java.time.Instant
import java.util.UUID

/**
 * One laboratory collection event (a measurement, never a model output). Analyte
 * values keep both the reported value/unit and a canonical value; a missing or
 * unknown conversion leaves [LabAnalyteValue.canonicalValue] null without discarding
 * what the user reported.
 */
data class LabResult(
    val id: UUID,
    val collectedAt: Instant?,
    val sourceZoneId: String?,
    val assay: Assay,
    val analytes: List<LabAnalyteValue> = emptyList(),
    val note: String? = null,
)

data class LabAnalyteValue(
    val analyte: Analyte,
    val reportedValue: Double,
    val reportedUnit: String,
    val canonicalValue: Double?,
) {
    companion object {
        const val E2_CANONICAL_UNIT = "pg/mL"
        const val TT_CANONICAL_UNIT = "ng/dL"

        /** pg/mL for E2, ng/dL for TT; null when the reported unit is unrecognised. */
        fun canonical(analyte: Analyte, reportedValue: Double, reportedUnit: String): Double? {
            val u = reportedUnit.trim().lowercase()
            return when (analyte) {
                Analyte.ESTRADIOL -> when (u) {
                    "pg/ml" -> reportedValue
                    "pmol/l" -> reportedValue / 3.6713
                    else -> null
                }
                Analyte.TOTAL_TESTOSTERONE -> when (u) {
                    "ng/dl" -> reportedValue
                    "nmol/l" -> reportedValue * 28.842
                    else -> null
                }
            }
        }
    }
}

enum class LabIneligibilityReason {
    MISSING_COLLECTION_TIME,
}

data class LabEligibilityDecision(
    val eligible: Boolean,
    val reason: LabIneligibilityReason?,
)

object LabEligibility {
    fun evaluate(lab: LabResult): LabEligibilityDecision =
        if (lab.collectedAt == null) {
            LabEligibilityDecision(false, LabIneligibilityReason.MISSING_COLLECTION_TIME)
        } else {
            LabEligibilityDecision(true, null)
        }
}
