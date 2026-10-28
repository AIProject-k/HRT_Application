package com.hormonelog.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MpaContractTest {
    @Test fun medroxyprogesteroneIsAnOralProgestinNotAnEstrogenOrAnAntiandrogen() {
        val mpa = Drug.MEDROXYPROGESTERONE_ACETATE
        assertEquals(listOf(Route.ORAL), mpa.allowedRoutes)
        assertFalse(mpa.isEstrogen)
        assertFalse(mpa.isAntiandrogen)
    }
}
