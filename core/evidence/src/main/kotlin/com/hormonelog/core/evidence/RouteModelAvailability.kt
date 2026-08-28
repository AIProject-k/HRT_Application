package com.hormonelog.core.evidence

import com.hormonelog.core.domain.Route

class RouteModelAvailability(private val bundle: EvidenceBundle) {
    fun canRecord(route: Route): Boolean = route in Route.entries

    /** True only when the bundle has fully-sourced parameters for the route. */
    fun canEstimate(route: Route): Boolean = bundle.supports(route)
}
