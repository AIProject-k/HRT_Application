package com.hormonelog.core.modelengine

import com.hormonelog.core.evidence.EvidenceStage

/** Why a model gives no numbers. Each reason is a different fact, so none is folded into another. */
enum class ModelReason {
    /** The evidence has not reached the stage that numbers need. */
    EVIDENCE_INSUFFICIENT,

    /** This build has the model switched off. */
    MODEL_DISABLED,

    /** The records hold no dose the model could use. */
    NO_DATA,

    /** Records exist, but the model refuses every one of them; each refusal is listed with its reason. */
    UNSUPPORTED_RECORDS,
}

/**
 * A model's answer for one record set. The evidence stage travels with either branch, so a screen can say how much
 * evidence stands behind a number, or behind the lack of one.
 */
sealed interface ModelStatus {
    val stage: EvidenceStage

    data class Available(
        val series: EstimateSeries,
        val excluded: List<ExcludedDose>,
        override val stage: EvidenceStage,
    ) : ModelStatus

    data class Unavailable(val reason: ModelReason, override val stage: EvidenceStage) : ModelStatus
}

/**
 * The gate a model passes before it may give numbers. Returns the reason numbers are withheld, or null when the model
 * may run. The evidence is checked before the build's switch, so the stronger reason is the one reported.
 */
fun blockingReason(stage: EvidenceStage, enabled: Boolean, hasDoses: Boolean): ModelReason? = when {
    !hasDoses -> ModelReason.NO_DATA
    stage != EvidenceStage.MODEL_VALIDATED -> ModelReason.EVIDENCE_INSUFFICIENT
    !enabled -> ModelReason.MODEL_DISABLED
    else -> null
}
