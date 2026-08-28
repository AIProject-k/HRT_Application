package com.hormonelog.core.evidence

import com.hormonelog.core.domain.Route
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteModelAvailabilityTest {
    @Test
    fun emptyBundleSupportsNoRoute() {
        val availability = RouteModelAvailability(EvidenceBundle.empty(version = "v1"))
        assertTrue(availability.canRecord(Route.GEL))
        assertFalse(availability.canEstimate(Route.GEL))
    }

    @Test
    fun v1BundleSupportsInjectionAndPatchButNotGel() {
        val availability = RouteModelAvailability(EvidenceBundleV1.bundle)
        assertTrue(availability.canEstimate(Route.IM_INJECTION))
        assertTrue(availability.canEstimate(Route.SC_INJECTION))
        assertTrue(availability.canEstimate(Route.PATCH))
        assertTrue(availability.canEstimate(Route.ORAL))
        assertTrue(availability.canEstimate(Route.SUBLINGUAL))
        assertFalse(availability.canEstimate(Route.GEL))
    }

    @Test
    fun everyActiveParameterHasAKnownSource() {
        val b = EvidenceBundleV1.bundle
        assertTrue(b.parameters.isNotEmpty())
        assertTrue(b.parameters.all { p -> p.evidenceIds.isNotEmpty() && p.evidenceIds.all(b.sources::containsKey) })
    }

    @Test
    fun aParameterWithNoSourceBreaksSupport() {
        val broken = EvidenceBundleV1.bundle.let { b ->
            b.copy(parameters = b.parameters + ModelParameter("orphan", Route.IM_INJECTION, "EV", "x", 1.0, "", emptySet()))
        }
        assertFalse(RouteModelAvailability(broken).canEstimate(Route.IM_INJECTION))
    }
}
