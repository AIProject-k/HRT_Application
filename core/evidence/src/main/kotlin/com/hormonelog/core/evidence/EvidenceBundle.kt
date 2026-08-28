package com.hormonelog.core.evidence

import com.hormonelog.core.domain.Route

/** Citation metadata for a source that backs one or more model parameters. */
data class EvidenceSource(
    val id: String,
    val title: String,
    val authors: String,
    val year: Int,
    val reference: String,
    val population: String,
    val evidenceLevel: String,
)

/**
 * A single named model parameter with its route/ester scope and the sources that
 * justify it. A parameter is not usable without at least one known source.
 */
data class ModelParameter(
    val id: String,
    /** null = not route-scoped (e.g. TT PK/PD constants). */
    val route: Route?,
    /** e.g. "EV", "EC", "patch_tw"; null when route-wide. */
    val ester: String?,
    val name: String,
    val value: Double,
    val unit: String,
    val evidenceIds: Set<String>,
)

/**
 * Versioned, packaged evidence (설계서 §2/§3.4). Route models read parameters from
 * here — never hard-coded in UI or engine. `supports(route)` gates whether an
 * estimate curve may be shown for that route.
 */
data class EvidenceBundle(
    val version: String,
    val sources: Map<String, EvidenceSource>,
    val parameters: List<ModelParameter>,
) {
    fun supports(route: Route): Boolean {
        val forRoute = parameters.filter { it.route == route }
        return forRoute.isNotEmpty() &&
            forRoute.all { p -> p.evidenceIds.isNotEmpty() && p.evidenceIds.all(sources::containsKey) }
    }

    /** name -> value for a route (optionally a specific ester/variant). */
    fun parametersFor(route: Route, ester: String? = null): Map<String, Double> =
        parameters
            .filter { it.route == route && (ester == null || it.ester == ester || it.ester == null) }
            .associate { it.name to it.value }

    /** ester/variant keys present for a route (e.g. the injectable esters). */
    fun estersFor(route: Route): Set<String> =
        parameters.filter { it.route == route }.mapNotNull { it.ester }.toSet()

    /** name -> value for the non-route-scoped TT PK/PD constants. */
    fun pdParameters(): Map<String, Double> =
        parameters.filter { it.route == null }.associate { it.name to it.value }

    companion object {
        fun empty(version: String): EvidenceBundle = EvidenceBundle(version, emptyMap(), emptyList())
    }
}
