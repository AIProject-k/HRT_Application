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

/**
 * The oral MPA curve without its height. The sources agree on when the peak comes and how fast the level falls, and
 * disagree on how high it gets, so a curve drawn from this is a share of one [referenceMg] dose's peak, never ng/mL.
 */
data class MpaShape(
    val absorptionPerHour: Double,
    val eliminationPerHour: Double,
    /** The single dose whose peak counts as 100%. */
    val referenceMg: Double,
    val linearMaxMgPerDose: Double,
)

/** Evidence the MPA engine runs on. No build ships validated parameters, because no quantitative time series is verified. */
object MedroxyprogesteroneEvidence {
    /**
     * Half-life 12.1 h (Pfizer single dose; 11.6 and 12.9 h in the other single-dose tables) and a peak 2.7 h after the
     * dose (2.2 to 2.9 h across the tables). Doses above 20 mg are outside every table read, so they are not drawn.
     */
    val shape = MpaShape(absorptionPerHour = 1.18, eliminationPerHour = 0.0573, referenceMg = 10.0, linearMaxMgPerDose = 20.0)

    /** Null in every shipped build: the sources give summary values, not a time series to fit or check a curve against. */
    val validated: MpaModelParameters? = null

    /** The bundle's stage. A curve needs MODEL_VALIDATED, so this stays below it until a time series is checked. */
    val stage: EvidenceStage get() = MpaEvidence.bundle.stage
}
