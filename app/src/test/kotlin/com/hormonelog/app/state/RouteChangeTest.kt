package com.hormonelog.app.state

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
}
