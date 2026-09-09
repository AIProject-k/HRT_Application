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
    CORRECTED;

    /**
     * Whether the drug actually entered the body. A late dose still happened, so it
     * belongs in every reconstruction; only [SKIPPED] is absent from the timeline.
     */
    val wasTaken: Boolean get() = this != SKIPPED
}

/** Actual product/formulation taken, not a recommendation. */
enum class Drug {
    ESTRADIOL_VALERATE,
    ESTRADIOL_TABLET,
    ESTRADIOL_PATCH,
    SPIRONOLACTONE,
    CYPROTERONE,
}

/**
 * Routes this formulation can physically be given by. A patch is not injected and an
 * ester in oil is not swallowed, so the recorder must not be able to pair them — a
 * nonsensical pair would otherwise reach the curve engines as if it were real.
 */
val Drug.allowedRoutes: List<Route>
    get() = when (this) {
        Drug.ESTRADIOL_VALERATE -> listOf(Route.IM_INJECTION, Route.SC_INJECTION)
        Drug.ESTRADIOL_TABLET -> listOf(Route.ORAL, Route.SUBLINGUAL)
        Drug.ESTRADIOL_PATCH -> listOf(Route.PATCH)
        Drug.SPIRONOLACTONE, Drug.CYPROTERONE -> listOf(Route.ORAL)
    }

/** Units that make sense for a route; a patch is counted, not weighed. */
val Route.allowedUnits: List<DoseUnit>
    get() = when (this) {
        Route.PATCH -> listOf(DoseUnit.PATCH, DoseUnit.MG_PER_DAY)
        Route.GEL -> listOf(DoseUnit.MG_PER_DAY, DoseUnit.MG)
        else -> listOf(DoseUnit.MG)
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
