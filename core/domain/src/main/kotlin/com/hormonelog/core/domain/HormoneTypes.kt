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
    ESTRADIOL_CYPIONATE,
    ESTRADIOL_TABLET,
    ESTRADIOL_PATCH,
    ESTRADIOL_GEL,
    SPIRONOLACTONE,
    CYPROTERONE,
}

/** Anti-androgens add nothing to the E2 curve; they only act on the Total T curve. */
val Drug.isAntiandrogen: Boolean
    get() = this == Drug.SPIRONOLACTONE || this == Drug.CYPROTERONE

/**
 * Routes this formulation can physically be given by. A patch is not injected and an
 * ester in oil is not swallowed, so the recorder must not be able to pair them — a
 * nonsensical pair would otherwise reach the curve engines as if it were real.
 */
val Drug.allowedRoutes: List<Route>
    get() = when (this) {
        Drug.ESTRADIOL_VALERATE, Drug.ESTRADIOL_CYPIONATE -> listOf(Route.IM_INJECTION, Route.SC_INJECTION)
        Drug.ESTRADIOL_TABLET -> listOf(Route.ORAL, Route.SUBLINGUAL)
        Drug.ESTRADIOL_PATCH -> listOf(Route.PATCH)
        Drug.ESTRADIOL_GEL -> listOf(Route.GEL)
        Drug.SPIRONOLACTONE, Drug.CYPROTERONE -> listOf(Route.ORAL)
    }

/** Units that make sense for a route; a patch is rated in µg released per day. */
val Route.allowedUnits: List<DoseUnit>
    get() = when (this) {
        Route.PATCH -> listOf(DoseUnit.UG_PER_DAY)
        else -> listOf(DoseUnit.MG)
    }

/**
 * Unit exactly as the user entered it; conversion to mg may not be possible.
 * [MG_PER_DAY] and [PATCH] are legacy: older builds offered them for patches, so
 * stored records may still carry them.
 */
enum class DoseUnit {
    MG,
    MG_PER_DAY,
    PATCH,
    UG_PER_DAY,
}

/** Where a record came from, so a bulk batch (import, sample) can be told apart later. */
enum class RecordSource {
    MANUAL,
    SCHEDULE,
    IMPORT,
    SAMPLE,
}

/** Injection site, for rotation. Only meaningful for [Route.IM_INJECTION] / [Route.SC_INJECTION]. */
enum class InjectionSite {
    LEFT_THIGH,
    RIGHT_THIGH,
    LEFT_GLUTE,
    RIGHT_GLUTE,
    LEFT_ABDOMEN,
    RIGHT_ABDOMEN,
}

/** How often a patch is changed; the two cycles have separate release profiles. */
enum class PatchCycle {
    TWICE_WEEKLY,
    WEEKLY,
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

    /** The user chose not to say. The Total T curve is not drawn at all. */
    DECLINED,
}
