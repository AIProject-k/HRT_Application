package com.hormonelog.core.evidence

/**
 * How far a model's evidence has been checked. Only [MODEL_VALIDATED] may show numbers. The lower stages record
 * what a source supports without turning it into a curve.
 */
enum class EvidenceStage {
    /** Only the citation is known. */
    METADATA_ONLY,

    /** Summary values such as Cmax, Tmax or half-life from a label or review, without the time series. */
    PK_SUMMARY,

    /** Mean time series checked against the source, published or digitised with its error recorded. */
    TIME_SERIES_VERIFIED,

    /** A model was fitted and checked against data it was not fitted to. */
    MODEL_VALIDATED,
}
