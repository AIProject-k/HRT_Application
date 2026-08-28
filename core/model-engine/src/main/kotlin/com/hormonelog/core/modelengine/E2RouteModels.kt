package com.hormonelog.core.modelengine

import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import com.hormonelog.core.evidence.EvidenceBundle

/** Dispatches a dose event to the right E2 route model, pulling parameters from the bundle. */
class E2RouteModels(private val bundle: EvidenceBundle) {

    /** pg/mL contribution of one administered dose [elapsedDays] after it; null = route/combination unsupported. */
    fun contribution(event: DoseEvent, elapsedDays: Double): Double? {
        if (elapsedDays <= 0.0) return 0.0
        // Non-estrogen medications contribute nothing to the E2 curve.
        if (event.drug == Drug.SPIRONOLACTONE || event.drug == Drug.CYPROTERONE) return 0.0
        return when (event.route) {
            Route.IM_INJECTION, Route.SC_INJECTION -> {
                val amount = event.normalizedMilligrams ?: return null
                val ester = esterFor(event.drug) ?: return null
                val p = bundle.parametersFor(event.route, ester)
                val d = p["d"] ?: return null
                E2ThreeCompartmentModel.concentration(elapsedDays, amount, d, p["k1"]!!, p["k2"]!!, p["k3"]!!)
            }
            Route.ORAL -> {
                val amount = event.normalizedMilligrams ?: return null
                val p = bundle.parametersFor(Route.ORAL)
                val s = p["oralScale"] ?: return null
                OralE2Model.concentration(elapsedDays, amount, s, p["oralKa"]!!, p["oralKe"]!!)
            }
            Route.SUBLINGUAL -> {
                val amount = event.normalizedMilligrams ?: return null
                val p = bundle.parametersFor(Route.SUBLINGUAL)
                val s = p["slScale"] ?: return null
                SublingualE2Model.concentration(
                    elapsedDays, amount, s, p["slKa"]!!, p["slKe"]!!, p["slSwallowed"]!!,
                    p["oralScale"]!!, p["oralKa"]!!, p["oralKe"]!!,
                )
            }
            Route.PATCH -> {
                // Patch dose is entered as µg/day; use the twice-weekly variant.
                val p = bundle.parametersFor(Route.PATCH, "tw")
                val d = p["d"] ?: return null
                E2ThreeCompartmentModel.concentration(elapsedDays, event.amountEntered, d, p["k1"]!!, p["k2"]!!, p["k3"]!!)
            }
            Route.GEL -> null
        }
    }

    /** Interval half-width fraction for the worst route present. */
    fun uncertaintyFraction(routes: Set<Route>): Double =
        routes.maxOfOrNull { routeUncertainty(it) } ?: 0.3

    private fun routeUncertainty(route: Route): Double = when (route) {
        Route.IM_INJECTION -> 0.25
        Route.SC_INJECTION -> 0.35
        Route.PATCH -> 0.30
        Route.ORAL -> 0.30
        Route.SUBLINGUAL -> 0.50
        Route.GEL -> 0.60
    }

    private fun esterFor(drug: Drug): String? = when (drug) {
        Drug.ESTRADIOL_VALERATE -> "EV"
        else -> null
    }
}
