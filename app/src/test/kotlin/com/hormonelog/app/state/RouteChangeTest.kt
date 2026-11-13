package com.hormonelog.app.state

import com.hormonelog.app.at
import com.hormonelog.app.daily
import com.hormonelog.app.dose
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteChangeTest {
    @Test fun anInjectedEsterDoesNotSilentlyBecomeATablet() {
        assertEquals(Drug.ESTRADIOL_TABLET, drugAfterRouteChange(Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, Route.ORAL))
        assertEquals(Drug.ESTRADIOL_VALERATE, drugAfterRouteChange(Drug.ESTRADIOL_TABLET, Route.ORAL, Route.IM_INJECTION))
    }

    @Test fun aChosenDrugIsKeptWhileTheRouteKindStaysTheSame() {
        assertEquals(Drug.ESTRADIOL_VALERATE, drugAfterRouteChange(Drug.ESTRADIOL_VALERATE, Route.ORAL, Route.SUBLINGUAL))
        assertEquals(Drug.ESTRADIOL_CYPIONATE, drugAfterRouteChange(Drug.ESTRADIOL_CYPIONATE, Route.IM_INJECTION, Route.SC_INJECTION))
        assertEquals(Drug.ESTRADIOL_PATCH, drugAfterRouteChange(Drug.ESTRADIOL_VALERATE, Route.ORAL, Route.PATCH))
    }

    private val start = at(2026, 11, 1)
    private val plan = daily(start, Drug.ESTRADIOL_TABLET, Route.ORAL, 2.0, 9 * 60)
    private fun after(amount: Double = 2.0, route: Route = Route.ORAL, drug: Drug = Drug.ESTRADIOL_VALERATE, plans: List<com.hormonelog.core.domain.Regimen> = listOf(plan)): List<Drug> {
        val before = AppState(regimens = plans)
        return followValerateTablets(before, before.copy(doses = listOf(dose(at(2026, 11, 13), drug, route, amount)))).regimens.map { it.drug }
    }

    @Test fun aTabletScheduleFollowsTheFirstValerateTabletOfItsRouteAndAmount() {
        assertEquals(listOf(Drug.ESTRADIOL_VALERATE), after())
    }

    @Test fun aScheduleStaysWhenTheRecordIsAnotherAmountRouteOrDrugOrAValerateScheduleAlreadyRuns() {
        assertEquals(listOf(Drug.ESTRADIOL_TABLET), after(amount = 1.0))
        assertEquals(listOf(Drug.ESTRADIOL_TABLET), after(route = Route.SUBLINGUAL))
        assertEquals(listOf(Drug.ESTRADIOL_TABLET), after(route = Route.IM_INJECTION))
        assertEquals(listOf(Drug.ESTRADIOL_TABLET), after(drug = Drug.ESTRADIOL_TABLET))
        val both = listOf(plan, daily(start, Drug.ESTRADIOL_VALERATE, Route.ORAL, 2.0, 21 * 60))
        assertEquals(listOf(Drug.ESTRADIOL_TABLET, Drug.ESTRADIOL_VALERATE), after(plans = both))
    }

    @Test fun aScheduleKeptAfterAnEarlierValerateTabletOrSetByTheChangeItselfStays() {
        val earlier = dose(at(2026, 11, 5), Drug.ESTRADIOL_VALERATE, Route.ORAL, 2.0)
        val now = dose(at(2026, 11, 13), Drug.ESTRADIOL_VALERATE, Route.ORAL, 2.0)
        val kept = AppState(regimens = listOf(plan), doses = listOf(earlier))
        assertEquals(listOf(Drug.ESTRADIOL_TABLET), followValerateTablets(kept, kept.copy(doses = listOf(earlier, now))).regimens.map { it.drug })
        val restored = AppState().let { followValerateTablets(it, it.copy(regimens = listOf(plan), doses = listOf(now))) }
        assertEquals(listOf(Drug.ESTRADIOL_TABLET), restored.regimens.map { it.drug })
    }
}
