package com.hormonelog.core.evidence

/**
 * Parameters of the oral MPA curve. [fOverVPerLitre] is bioavailability over apparent volume, in 1/L, so a dose in mg
 * times it gives mg/L. [loqNgPerMl] is the assay's lower limit of quantitation, used only to classify a concentration.
 */
data class MpaModelParameters(
    val absorptionPerHour: Double,
    val eliminationPerHour: Double,
    val lagHours: Double,
    val fOverVPerLitre: Double,
    val linearMaxMgPerDose: Double,
    val loqNgPerMl: Double,
) {
    fun isValid(): Boolean =
        listOf(absorptionPerHour, eliminationPerHour, lagHours, fOverVPerLitre, linearMaxMgPerDose, loqNgPerMl).all { it.isFinite() } &&
            absorptionPerHour > 0.0 && eliminationPerHour > 0.0 && lagHours >= 0.0 &&
            fOverVPerLitre > 0.0 && linearMaxMgPerDose > 0.0 && loqNgPerMl > 0.0
}

/** Evidence the MPA engine runs on. No build ships validated parameters, because no quantitative time series is verified. */
object MedroxyprogesteroneEvidence {
    /** Null in every shipped build: the sources give summary values, not a time series to fit or check a curve against. */
    val validated: MpaModelParameters? = null

    /** The bundle's stage. A curve needs MODEL_VALIDATED, so this stays below it until a time series is checked. */
    val stage: EvidenceStage get() = MpaEvidence.bundle.stage
}
