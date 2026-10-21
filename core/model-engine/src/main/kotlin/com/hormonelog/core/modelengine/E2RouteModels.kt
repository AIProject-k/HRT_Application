package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.PatchCycle
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.isEstrogen
import com.hormonelog.core.evidence.EvidenceBundle

/**
 * Personal calibration is kept per group of routes: what one person absorbs from an
 * injection says little about what they absorb from a tablet, so a route change never
 * inherits the other route's correction. Gel has no model, hence no group.
 */
enum class CalibrationGroup { INJECTION, ORAL, PATCH }

fun Route.calibrationGroup(): CalibrationGroup? = when (this) {
    Route.IM_INJECTION, Route.SC_INJECTION -> CalibrationGroup.INJECTION
    Route.ORAL, Route.SUBLINGUAL -> CalibrationGroup.ORAL
    Route.PATCH -> CalibrationGroup.PATCH
    Route.GEL -> null
}

/** Dispatches a dose event to the right E2 route model, pulling parameters from the bundle. */
class E2RouteModels(bundle: EvidenceBundle) {

    // Evidence is immutable for this engine: do not rebuild maps for every dose at every time step.
    private val supported = Route.entries.filter(bundle::supports).toSet()
    private val routeParameters = Route.entries.associateWith { bundle.parametersFor(it) }
    private val injectionParameters = listOf(Route.IM_INJECTION, Route.SC_INJECTION).associateWith { route ->
        listOf("EV", "EC").associateWith { bundle.parametersFor(route, it) }
    }
    private val patchParameters = listOf("tw", "ow").associateWith { bundle.parametersFor(Route.PATCH, it) }

    /**
     * Why [event] cannot be put on the curve, or null when it can. Anti-androgens never can
     * (they add no E2) but that is not an exclusion worth reporting, so they answer null.
     */
    fun exclusionOf(event: DoseEvent): DoseExclusion? {
        if (!event.drug.isEstrogen) return null
        if (event.route !in supported) return DoseExclusion.NO_MODEL
        return when (event.route) {
            Route.IM_INJECTION, Route.SC_INJECTION ->
                if (esterFor(event.drug) == null || event.normalizedMilligrams == null) DoseExclusion.UNSUPPORTED_COMBINATION else null
            Route.ORAL, Route.SUBLINGUAL ->
                if (event.drug != Drug.ESTRADIOL_TABLET || event.normalizedMilligrams == null) DoseExclusion.UNSUPPORTED_COMBINATION else null
            Route.PATCH ->
                if (event.drug != Drug.ESTRADIOL_PATCH) {
                    DoseExclusion.UNSUPPORTED_COMBINATION
                } else if (event.patchMicrogramsPerDay == null) {
                    DoseExclusion.PATCH_STRENGTH_MISSING
                } else {
                    null
                }
            Route.GEL -> DoseExclusion.NO_MODEL
        }
    }

    /** pg/mL contribution of one administered dose [elapsedDays] after it; null = route/combination unsupported. */
    fun contribution(event: DoseEvent, elapsedDays: Double): Double? {
        if (elapsedDays <= 0.0) return 0.0
        // Non-estrogen medications contribute nothing to the E2 curve.
        if (!event.drug.isEstrogen) return 0.0
        if (exclusionOf(event) != null) return null
        return when (event.route) {
            Route.IM_INJECTION, Route.SC_INJECTION -> {
                val amount = event.normalizedMilligrams ?: return null
                val ester = esterFor(event.drug) ?: return null
                val p = injectionParameters.getValue(event.route).getValue(ester)
                val d = p["d"] ?: return null
                E2ThreeCompartmentModel.concentration(elapsedDays, amount, d, p["k1"]!!, p["k2"]!!, p["k3"]!!)
            }
            Route.ORAL -> {
                val amount = event.normalizedMilligrams ?: return null
                val p = routeParameters.getValue(Route.ORAL)
                val s = p["oralScale"] ?: return null
                OralE2Model.concentration(elapsedDays, amount, s, p["oralKa"]!!, p["oralKe"]!!)
            }
            Route.SUBLINGUAL -> {
                val amount = event.normalizedMilligrams ?: return null
                val p = routeParameters.getValue(Route.SUBLINGUAL)
                val s = p["slScale"] ?: return null
                SublingualE2Model.concentration(
                    elapsedDays, amount, s, p["slKa"]!!, p["slKe"]!!, p["slSwallowed"]!!,
                    p["oralScale"]!!, p["oralKa"]!!, p["oralKe"]!!,
                )
            }
            Route.PATCH -> {
                // Strength is µg/day; the change cycle picks the twice-weekly or weekly profile.
                val micrograms = event.patchMicrogramsPerDay ?: return null
                val variant = if (event.patchCycle == PatchCycle.WEEKLY) "ow" else "tw"
                val p = patchParameters.getValue(variant)
                val d = p["d"] ?: return null
                E2ThreeCompartmentModel.concentration(elapsedDays, micrograms, d, p["k1"]!!, p["k2"]!!, p["k3"]!!)
            }
            Route.GEL -> null
        }
    }

    /** Interval half-width fraction before any personal calibration narrows it. */
    fun routeUncertainty(route: Route): Double = when (route) {
        Route.IM_INJECTION -> 0.25
        Route.SC_INJECTION -> 0.35
        Route.PATCH -> 0.30
        Route.ORAL -> 0.30
        Route.SUBLINGUAL -> 0.50
        Route.GEL -> 0.60
    }

    private fun esterFor(drug: Drug): String? = when (drug) {
        Drug.ESTRADIOL_VALERATE -> "EV"
        Drug.ESTRADIOL_CYPIONATE -> "EC"
        else -> null
    }
}
