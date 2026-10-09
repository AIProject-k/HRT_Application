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
    /** Drawn before HRT started: the personal starting point, never a calibration lab. */
    val isBaseline: Boolean = false,
    val source: RecordSource = RecordSource.MANUAL,
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
                    "pg/ml", "ng/l" -> reportedValue
                    "pmol/l" -> reportedValue / PMOL_PER_PG_E2
                    else -> null
                }
                Analyte.TOTAL_TESTOSTERONE -> when (u) {
                    "ng/dl" -> reportedValue
                    "ng/ml" -> reportedValue * 100.0
                    "nmol/l" -> reportedValue * NGDL_PER_NMOL_T
                    else -> null
                }
            }
        }

        private const val PMOL_PER_PG_E2 = 3.6713
        private const val NGDL_PER_NMOL_T = 28.842
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
