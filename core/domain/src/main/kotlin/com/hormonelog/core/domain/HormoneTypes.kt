package com.hormonelog.core.domain

enum class Assay {
    LC_MS_MS,
    IMMUNOASSAY,
    UNKNOWN,
}

enum class Route {
    ORAL,
    SUBLINGUAL,
    IM_INJECTION,
    SC_INJECTION,
    PATCH,
    GEL,
}

enum class DoseStatus {
    ADMINISTERED,
    SKIPPED,
    DELAYED,
    CORRECTED,
}

/** Actual product/formulation taken, not a recommendation. */
enum class Drug {
    ESTRADIOL_VALERATE,
    ESTRADIOL_TABLET,
    ESTRADIOL_PATCH,
    SPIRONOLACTONE,
    CYPROTERONE,
}

/** Unit exactly as the user entered it; conversion to mg may not be possible. */
enum class DoseUnit {
    MG,
    MG_PER_DAY,
    PATCH,
}

/** Laboratory analytes recorded as first-class measurements. */
enum class Analyte {
    ESTRADIOL,
    TOTAL_TESTOSTERONE,
}

/** Affects the testosterone baseline used by the TT model (설계서 §3.1). */
enum class GonadalStatus {
    INTACT,
    POST_ORCHIECTOMY,
    UNKNOWN,
}
