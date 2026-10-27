package com.hormonelog.app.analysis

import com.hormonelog.app.*
import com.hormonelog.app.feature.common.doseSummary
import com.hormonelog.app.state.HormoneSeries
import com.hormonelog.app.state.drugsFor
import com.hormonelog.core.domain.*
import org.junit.Assert.*
import org.junit.Test

class ProveraIntegrationTest {
    private val now = at(2026, 10, 27, 12)
    private val from = now.minusSeconds(86400L * 14)
    private val estradiol = dose(now.minusSeconds(86400L * 3), Drug.ESTRADIOL_TABLET, Route.ORAL, 2.0)
    private val provera = dose(now.minusSeconds(3600), Drug.MEDROXYPROGESTERONE_ACETATE, Route.ORAL, 10.0)

    @Test fun proveraIsOfferedForOralOnlyAndLabelledLikeTheOtherDrugs() {
        assertTrue(Drug.MEDROXYPROGESTERONE_ACETATE in drugsFor(Route.ORAL))
        assertTrue(Route.entries.filter { it != Route.ORAL }.all { Drug.MEDROXYPROGESTERONE_ACETATE !in drugsFor(it) })
        assertEquals("프로베라 10 mg · 경구", doseSummary(Drug.MEDROXYPROGESTERONE_ACETATE, Route.ORAL, 10.0, DoseUnit.MG))
    }

    @Test fun proveraLeavesEstradiolAndTestosteroneCurvesExactlyAsTheyWere() {
        val plain = curves(listOf(estradiol))
        val withProvera = curves(listOf(estradiol, provera))
        assertNotNull(plain.e2)
        assertEquals(plain.e2, withProvera.e2)
        assertEquals(plain.tt, withProvera.tt)
    }

    @Test fun proveraAloneDrawsNoEstradiolTestosteroneOrProgesteroneCurve() {
        val only = curves(listOf(provera))
        assertNull(only.e2)
        assertNull(only.tt)
        assertNull(only.p4)
    }

    @Test fun proveraGetsNoTickOnTheEstradiolChart() {
        val doses = listOf(estradiol, provera)
        val model = chartModelFor(HormoneSeries.E2, curves(doses), doses, emptyList(), from, now, now, false)
        assertEquals(1, model.doses.size)
    }

    private fun curves(doses: List<DoseEvent>): Curves =
        Analysis.curves(doses, emptyList(), emptyList(), GonadalStatus.INTACT, now, from, now, SEOUL)
}
