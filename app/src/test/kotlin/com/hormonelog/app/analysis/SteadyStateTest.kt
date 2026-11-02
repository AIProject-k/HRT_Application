package com.hormonelog.app.analysis

import com.hormonelog.app.*
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteadyStateTest {
    private val now = at(2026, 11, 2, 21)
    private val fmt = Fmt(SEOUL, true, now)
    private fun daysAgo(d: Long) = now.minusSeconds(d * 86_400)
    private fun injection(d: Long) = dose(daysAgo(d), Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 10.0)
    private fun tablet(d: Long) = dose(daysAgo(d), Drug.ESTRADIOL_TABLET, Route.ORAL, 2.0)

    @Test fun aWeekOfTabletsBetweenRegularInjectionsIsNotASwitch() {
        val doses = (6L..104L step 14).map(::injection) + (7L..14L).map(::tablet)
        assertNull(SteadyState.find(doses, now, fmt))
    }

    @Test fun startingInjectionsAfterTabletsIsASwitch() {
        val doses = (6L..60L).map(::tablet) + injection(5)
        assertTrue(SteadyState.find(doses, now, fmt)!!.text.contains("→"))
    }

    @Test fun goingBackToInjectionsAfterMonthsOfTabletsIsASwitch() {
        val doses = (80L..150L step 14).map(::injection) + (6L..70L).map(::tablet) + injection(4)
        assertTrue(SteadyState.find(doses, now, fmt)!!.text.contains("→"))
    }
}
