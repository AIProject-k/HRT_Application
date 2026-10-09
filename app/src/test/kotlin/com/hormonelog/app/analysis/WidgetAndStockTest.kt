package com.hormonelog.app.analysis

import com.hormonelog.app.SEOUL
import com.hormonelog.app.at
import com.hormonelog.app.daily
import com.hormonelog.app.saturdayDoses
import com.hormonelog.app.settings
import com.hormonelog.app.weekly
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.StockItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class WidgetLogicTest {
    private val ev = weekly(at(2026, 9, 19))
    private val doses = saturdayDoses(at(2026, 9, 19), at(2026, 10, 3))

    private fun content(now: Instant, s: com.hormonelog.core.domain.AppSettings = settings, regimens: List<com.hormonelog.core.domain.Regimen> = listOf(ev)) =
        WidgetLogic.of(regimens, doses, s, now, SEOUL, Fmt(SEOUL, false, now))

    @Test
    fun theNextDoseIsShownWithItsDrugAndAnAnswerButtonWhenItIsClose() {
        val c = content(at(2026, 10, 10, 6))
        assertEquals("호르몬로그 · 다음 투약", c.label)
        assertEquals("10월 10일 (토) 오전 9:00", c.title)
        assertEquals("EV 5 mg · 주사 · 3시간 뒤", c.detail)
        assertEquals("투약 완료", c.action)
        assertEquals(ev.id, c.regimenId)
        assertEquals(at(2026, 10, 10, 9).toEpochMilli(), c.subjectMillis)
    }

    @Test
    fun aDoseDaysAwayHasNoButtonToPressTooEarly() {
        val c = content(at(2026, 10, 5, 16, 30))
        assertEquals("EV 5 mg · 주사 · 5일 뒤", c.detail)
        // No button, so the widget has nothing to answer; the ids it carries are never used without one.
        assertNull(c.action)
    }

    @Test
    fun aDoseThatIsLateSaysSoAndStillOffersTheButton() {
        val c = content(at(2026, 10, 10, 9, 30))
        assertEquals("EV 5 mg · 주사 · 예정 시각이 지났어요", c.detail)
        assertEquals("투약 완료", c.action)
    }

    @Test
    fun inDisguiseOnlyATaskAndATimeShow() {
        val c = content(at(2026, 10, 5, 16, 30), settings.copy(disguiseLauncher = true))
        assertEquals("메모 · 할 일", c.label)
        assertEquals("토요일 오전 9:00", c.title)
        assertNull(c.detail)
        val all = listOfNotNull(c.label, c.title, c.detail, c.action).joinToString(" ")
        for (word in listOf("호르몬", "EV", "mg", "주사", "투약")) assertFalse("disguised widget says '$word': $all", all.contains(word))
    }

    @Test
    fun neutralWordsAlsoApplyWithoutTheDisguise() {
        val today = content(at(2026, 10, 10, 6), settings.copy(neutralNotifications = true))
        assertEquals("오늘 오전 9:00", today.title)
        assertEquals("완료", today.action)
        val tomorrow = content(at(2026, 10, 9, 20), settings.copy(neutralNotifications = true))
        assertEquals("내일 오전 9:00", tomorrow.title)
    }

    @Test
    fun withNoRunningPlanTheWidgetSaysThereIsNothingToDo() {
        val none = content(at(2026, 10, 5), regimens = emptyList())
        assertEquals("예정된 투약이 없어요", none.title)
        assertNull(none.action)
        assertEquals("할 일이 없어요", content(at(2026, 10, 5), settings.copy(disguiseLauncher = true), emptyList()).title)
        assertNull(content(at(2026, 10, 5), settings.copy(disguiseLauncher = true), emptyList()).detail)
    }

    @Test
    fun aPlanThatEndedIsNotShown() {
        val ended = ev.copy(active = false, endAt = at(2026, 10, 1))
        assertEquals("예정된 투약이 없어요", content(at(2026, 10, 5), regimens = listOf(ended)).title)
    }
}

class StockLogicTest {
    private val now = at(2026, 10, 5, 16, 30)
    private val ev = weekly(at(2026, 9, 19))
    private val cpa = daily(at(2026, 3, 1, 8, 30))

    private fun stock(drug: Drug, route: Route, amount: Double, count: Int) =
        StockItem(UUID.randomUUID(), drug, route, amount, DoseUnit.MG, count)

    @Test
    fun theStockLastsAsLongAsTheRunningPlanThatUsesIt() {
        val ampoules = stock(Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 5.0, 3)
        val tablets = stock(Drug.CYPROTERONE, Route.ORAL, 12.5, 18)
        assertEquals(21, StockLogic.daysLeft(ampoules, listOf(ev, cpa), now))
        assertEquals(18, StockLogic.daysLeft(tablets, listOf(ev, cpa), now))
        assertEquals(LocalDate.of(2026, 10, 26), StockLogic.runsOutOn(21, now, SEOUL))
    }

    @Test
    fun withNoMatchingPlanThereIsNoEstimate() {
        val gel = stock(Drug.ESTRADIOL_GEL, Route.GEL, 1.5, 4)
        assertNull(StockLogic.daysLeft(gel, listOf(ev, cpa), now))
        // A plan for another amount is another product.
        val sixMg = stock(Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 6.0, 3)
        assertNull(StockLogic.daysLeft(sixMg, listOf(ev), now))
    }

    @Test
    fun aPlanThatHasEndedDoesNotUseUpAnything() {
        val ampoules = stock(Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 5.0, 3)
        assertNull(StockLogic.daysLeft(ampoules, listOf(ev.copy(active = false, endAt = at(2026, 10, 1))), now))
    }

    @Test
    fun anEmptyBoxIsZeroDays() {
        assertEquals(0, StockLogic.daysLeft(stock(Drug.CYPROTERONE, Route.ORAL, 12.5, 0), listOf(cpa), now))
    }

    @Test
    fun theUsageLineSaysHowOftenOneIsUsed() {
        assertEquals("7일에 1개 사용", StockLogic.usageText(ev, "개"))
        assertEquals("하루에 1정 사용", StockLogic.usageText(cpa, "정"))
        assertNull(StockLogic.usageText(null, "개"))
        // Two days a week is every 3.5 days on average.
        val twice = ev.copy(weekdays = com.hormonelog.core.domain.Regimen.maskOf(listOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.THURSDAY)))
        assertEquals("3.5일에 1개 사용", StockLogic.usageText(twice, "개"))
    }

    @Test
    fun theFasterPlanWinsWhenTwoUseTheSameProduct() {
        val twice = ev.copy(id = UUID.randomUUID(), weekdays = com.hormonelog.core.domain.Regimen.maskOf(listOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.THURSDAY)))
        val item = stock(Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 5.0, 7)
        assertEquals(twice.id, StockLogic.planFor(item, listOf(ev, twice), now)!!.id)
        assertNotNull(StockLogic.planFor(item, listOf(ev), now))
        assertTrue(StockLogic.daysLeft(item, listOf(ev, twice), now)!! <= 24)
    }
}
