package com.hormonelog.app.state

import com.hormonelog.app.SEOUL
import com.hormonelog.app.at
import com.hormonelog.app.dayMillis
import com.hormonelog.app.daily
import com.hormonelog.app.dose
import com.hormonelog.app.settings
import com.hormonelog.app.weekly
import com.hormonelog.core.domain.PatchCycle
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.allowedRoutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class ScheduleOpsTest {
    /** Monday 5 October 2026, 16:30 in Seoul. */
    private val now = at(2026, 10, 5, 16, 30)
    private val empty = AppState(settings = settings, screen = Screen.SCHEDULES, stack = listOf(Screen.ME))

    @Test
    fun aNewPlanStartsFromWhatThePersonTakesNowAndFromToday() {
        val s = ScheduleOps.create(empty.copy(doses = listOf(dose(at(2026, 10, 3, 9), amount = 4.0))), now, SEOUL)
        val d = s.scheduleDraft!!
        assertEquals(Screen.SCHEDULE_EDIT, s.screen)
        assertEquals("4", d.amountText)
        assertEquals(setOf(DayOfWeek.MONDAY), d.weekdays)
    }

    @Test
    fun withNoHistoryAPlanStartsEmptyRatherThanWithAGuessedDose() {
        val d = ScheduleOps.create(empty, now, SEOUL).scheduleDraft!!
        assertEquals("", d.amountText)
        assertFalse(d.canSave(dayMillis(2026, 10, 5)))
    }

    @Test
    fun weeklyTakesOneWeekdayAndTwiceAWeekTakesTwo() {
        var s = ScheduleOps.create(empty, now, SEOUL)
        s = ScheduleOps.toggleDay(s, DayOfWeek.FRIDAY)
        assertEquals(setOf(DayOfWeek.FRIDAY), s.scheduleDraft!!.weekdays)

        s = ScheduleOps.setInterval(s, IntervalChoice.TWICE_WEEKLY, now, SEOUL)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), s.scheduleDraft!!.weekdays)
        // A third pick replaces the older of the two.
        s = ScheduleOps.toggleDay(s, DayOfWeek.SATURDAY)
        assertEquals(setOf(DayOfWeek.THURSDAY, DayOfWeek.SATURDAY), s.scheduleDraft!!.weekdays)
        assertNull(s.scheduleDraft!!.weekdaysError)
    }

    @Test
    fun aPatchIsChangedOnItsCycleNotOnAnIntervalChip() {
        var s = ScheduleOps.create(empty, now, SEOUL)
        s = ScheduleOps.setRoute(s, Route.PATCH)
        s = ScheduleOps.setPatchCycle(s, PatchCycle.WEEKLY, now, SEOUL)
        assertEquals(IntervalChoice.WEEKLY, s.scheduleDraft!!.effectiveInterval)
        s = ScheduleOps.setPatchCycle(s, PatchCycle.TWICE_WEEKLY, now, SEOUL)
        assertEquals(IntervalChoice.TWICE_WEEKLY, s.scheduleDraft!!.effectiveInterval)
        assertEquals(2, s.scheduleDraft!!.weekdays.size)
    }

    @Test
    fun changingTheRouteKeepsTheDrugOnlyWhenItFits() {
        var s = ScheduleOps.create(empty, now, SEOUL)
        s = ScheduleOps.setRoute(s, Route.ORAL)
        assertTrue(Route.ORAL in s.scheduleDraft!!.drug.allowedRoutes)
        s = ScheduleOps.setRoute(s, Route.IM_INJECTION)
        assertTrue(Route.IM_INJECTION in s.scheduleDraft!!.drug.allowedRoutes)
    }

    @Test
    fun anEndDayBeforeTheStartDayCannotBeSaved() {
        var s = ScheduleOps.create(empty, now, SEOUL)
        s = ScheduleOps.setAmount(s, "5")
        s = ScheduleOps.draft(s) { it.copy(startMillis = dayMillis(2026, 10, 5), endMillis = dayMillis(2026, 10, 1)) }
        assertTrue(s.scheduleDraft!!.endBeforeStart(dayMillis(2026, 10, 5)))
        assertEquals(s, ScheduleOps.save(s, now, SEOUL))
    }

    @Test
    fun onlyDigitsAndDotsReachTheAmountAndAMalformedNumberIsRefused() {
        val typed = ScheduleOps.setAmount(ScheduleOps.create(empty, now, SEOUL), "5,0abc")
        // Anything that is not a digit or a dot is never stored.
        assertEquals("50", typed.scheduleDraft!!.amountText)
        assertNull(typed.scheduleDraft!!.amountError)
        val bad = ScheduleOps.setAmount(typed, "5.5.5")
        assertNotNull(bad.scheduleDraft!!.amountError)
        assertFalse(bad.scheduleDraft!!.canSave(dayMillis(2026, 10, 5)))
    }

    @Test
    fun savingANewPlanAddsItAndLeavesTheEditor() {
        var s = ScheduleOps.create(empty, now, SEOUL)
        s = ScheduleOps.setAmount(s, "5")
        val saved = ScheduleOps.save(s, now, SEOUL)
        assertEquals(1, saved.regimens.size)
        assertEquals(Screen.SCHEDULES, saved.screen)
        assertNull(saved.scheduleDraft)
        assertNull(saved.confirm)
        assertNotNull(saved.undo)
    }

    @Test
    fun aSecondPlanForTheSameDrugAndRouteAsksFirst() {
        val running = weekly(at(2026, 9, 5))
        var s = ScheduleOps.create(empty.copy(regimens = listOf(running)), now, SEOUL)
        s = ScheduleOps.setAmount(s, "6")
        val asked = ScheduleOps.save(s, now, SEOUL)
        assertEquals(1, asked.regimens.size)
        assertEquals(running, (asked.confirm as Confirm.ReplaceRegimen).existing)
        // Yes: the old plan ends, the new one starts, and the editor closes.
        val done = RecordsReducer.confirmReplace(asked)
        assertEquals(2, done.regimens.size)
        assertEquals(Screen.SCHEDULES, done.screen)
    }

    @Test
    fun aDifferentDrugIsNotAConflict() {
        val running = weekly(at(2026, 9, 5))
        var s = ScheduleOps.create(empty.copy(regimens = listOf(running)), now, SEOUL)
        s = ScheduleOps.setRoute(s, Route.ORAL)
        s = ScheduleOps.setDrug(s, com.hormonelog.core.domain.Drug.CYPROTERONE)
        s = ScheduleOps.setAmount(s, "12.5")
        s = ScheduleOps.setInterval(s, IntervalChoice.DAILY, now, SEOUL)
        val saved = ScheduleOps.save(s, now, SEOUL)
        assertNull(saved.confirm)
        assertEquals(2, saved.regimens.size)
    }

    @Test
    fun endingAPlanTodayLeavesItsPastAlone() {
        val plan = weekly(at(2026, 9, 5))
        val past = dose(at(2026, 9, 5, 9))
        val s = ScheduleOps.endToday(empty.copy(regimens = listOf(plan), doses = listOf(past)), plan.id, now)
        val ended = s.regimens.single()
        assertFalse(ended.active)
        assertEquals(now, ended.endAt)
        assertEquals(1, s.doses.size)
    }

    @Test
    fun editingAnExistingPlanKeepsItsDrugRouteAndAmount() {
        val plan = weekly(at(2026, 9, 5), amount = 5.0)
        var s = ScheduleOps.edit(empty.copy(regimens = listOf(plan)), plan.id, SEOUL)
        assertEquals(IntervalChoice.WEEKLY, s.scheduleDraft!!.interval)
        assertEquals(setOf(DayOfWeek.SATURDAY), s.scheduleDraft!!.weekdays)
        // The editor offers no way to change them, and a save cannot either.
        s = ScheduleOps.draft(s) { it.copy(amountText = "9", timeMinutes = 21 * 60) }
        val saved = ScheduleOps.save(s, now, SEOUL)
        val after = saved.regimens.single()
        assertEquals(5.0, after.amountEntered, 0.0)
        assertEquals(21 * 60, after.timeMinutes)
        assertEquals(plan.startAt, after.startAt)
    }

    @Test
    fun theFirstDatesAreShownBeforeSaving() {
        var s = ScheduleOps.create(empty, now, SEOUL)
        s = ScheduleOps.setAmount(s, "5")
        val dates = ScheduleOps.preview(s.scheduleDraft!!, now, SEOUL)
        assertEquals(5, dates.size)
        assertTrue(dates.zipWithNext().all { (a, b) -> a.isBefore(b) })
        // Today's 09:00 has passed, so a Monday plan's first date is next Monday.
        assertEquals(at(2026, 10, 12, 9), dates.first())
        assertTrue(ScheduleOps.preview(ScheduleOps.create(empty, now, SEOUL).scheduleDraft!!, now, SEOUL).isEmpty())
    }

    @Test
    fun deletingFromTheEditorClosesTheEditorToo() {
        val plan = daily(at(2026, 3, 1))
        val editing = ScheduleOps.edit(empty.copy(regimens = listOf(plan)), plan.id, SEOUL)
        val deleted = ScheduleOps.afterDelete(RecordsReducer.deleteRegimen(editing, plan.id))
        assertEquals(Screen.SCHEDULES, deleted.screen)
        assertNull(deleted.scheduleDraft)
        assertEquals(0, deleted.regimens.size)
    }
}
