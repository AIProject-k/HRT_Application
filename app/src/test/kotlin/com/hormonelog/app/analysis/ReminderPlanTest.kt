package com.hormonelog.app.analysis

import com.hormonelog.app.SEOUL
import com.hormonelog.app.at
import com.hormonelog.app.daily
import com.hormonelog.app.dailyDoses
import com.hormonelog.app.dose
import com.hormonelog.app.lab
import com.hormonelog.app.saturdayDoses
import com.hormonelog.app.settings
import com.hormonelog.app.weekly
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReminderPlanTest {
    /** Monday 5 October 2026, 16:30 in Seoul. */
    private val now = at(2026, 10, 5, 16, 30)
    private val fmt = Fmt(SEOUL, clock24 = false, now = now)

    private val ev = weekly(at(2026, 9, 19))
    private val evDoses = saturdayDoses(at(2026, 9, 19), at(2026, 10, 3))
    private val cpa = daily(at(2026, 3, 1, 8, 30))
    private val cpaDoses = dailyDoses(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 10, 4))

    private val on = settings.copy(notifyInjection = true, notifyDaily = true, notifyLab = true, notifyAppointment = true)

    private fun between(
        from: java.time.Instant, until: java.time.Instant, s: com.hormonelog.core.domain.AppSettings = on,
        regimens: List<com.hormonelog.core.domain.Regimen> = listOf(ev, cpa),
        doses: List<com.hormonelog.core.domain.DoseEvent> = evDoses + cpaDoses,
        labs: List<com.hormonelog.core.domain.LabResult> = emptyList(),
        visit: Long? = null,
    ) = ReminderPlan.between(regimens, doses, labs, visit, s, from, until, SEOUL)

    @Test
    fun anInjectionDayRemindsTheEveningBeforeAndOnTheDay() {
        val r = between(now, at(2026, 10, 12), regimens = listOf(ev), doses = evDoses)
        assertEquals(listOf(ReminderKind.INJECTION_EVE, ReminderKind.INJECTION_DAY), r.map { it.kind })
        assertEquals(at(2026, 10, 9, 21), r[0].at)
        assertEquals(at(2026, 10, 10, 9), r[1].at)
        // Both are about the same injection, and "투약 완료" on the day records that injection's own time.
        assertEquals(listOf(at(2026, 10, 10, 9), at(2026, 10, 10, 9)), r.map { it.subject })
    }

    @Test
    fun aDailyTabletRemindsOnTimeAndOnceMoreHalfAnHourLater() {
        val r = between(at(2026, 10, 5, 7), at(2026, 10, 5, 10), regimens = listOf(cpa), doses = cpaDoses)
        assertEquals(listOf(ReminderKind.DAILY, ReminderKind.DAILY_REPEAT), r.map { it.kind })
        assertEquals(listOf(at(2026, 10, 5, 8, 30), at(2026, 10, 5, 9, 0)), r.map { it.at })
        assertEquals(at(2026, 10, 5, 8, 30), r[1].subject)
    }

    @Test
    fun aDoseAlreadyTakenSilencesItsReminders() {
        val takenAlready = cpaDoses + dose(at(2026, 10, 5, 8, 40), drug = Drug.CYPROTERONE, route = Route.ORAL, amount = 12.5)
        val r = between(at(2026, 10, 5, 7), at(2026, 10, 5, 10), regimens = listOf(cpa), doses = takenAlready)
        assertTrue(r.isEmpty())
    }

    @Test
    fun eachKindIsOffUntilTheUserTurnsItOn() {
        val off = settings
        assertTrue(between(now, at(2026, 10, 12), s = off).isEmpty())
        val onlyInjection = between(now, at(2026, 10, 12), s = settings.copy(notifyInjection = true))
        assertTrue(onlyInjection.all { it.kind == ReminderKind.INJECTION_DAY || it.kind == ReminderKind.INJECTION_EVE })
        val onlyDaily = between(now, at(2026, 10, 7), s = settings.copy(notifyDaily = true))
        assertTrue(onlyDaily.all { it.kind == ReminderKind.DAILY || it.kind == ReminderKind.DAILY_REPEAT })
        assertTrue(onlyDaily.isNotEmpty())
    }

    @Test
    fun anEndedPlanRemindsAboutNothing() {
        val ended = ev.copy(active = false, endAt = at(2026, 9, 30))
        assertTrue(between(now, at(2026, 10, 20), regimens = listOf(ended), doses = evDoses).isEmpty())
    }

    @Test
    fun aLabIsDueEightWeeksAfterTheLastRealOneNotTheBaseline() {
        val labs = listOf(lab(at(2026, 2, 20, 10), e2 = 28.0, baseline = true), lab(at(2026, 8, 14, 11), e2 = 188.0))
        val r = between(now, at(2026, 10, 12), regimens = emptyList(), doses = emptyList(), labs = labs)
        assertEquals(1, r.size)
        assertEquals(ReminderKind.LAB, r.single().kind)
        // 8 weeks after 14 August is 9 October.
        assertEquals(at(2026, 10, 9, 9), r.single().at)

        val baselineOnly = between(now, at(2026, 12, 31), regimens = emptyList(), doses = emptyList(), labs = labs.take(1))
        assertTrue(baselineOnly.isEmpty())
    }

    @Test
    fun anAppointmentRemindsTheEveningBefore() {
        val visit = at(2026, 10, 15, 10, 30)
        val r = between(at(2026, 10, 13), at(2026, 10, 15), regimens = emptyList(), doses = emptyList(), visit = visit.toEpochMilli())
        assertEquals(1, r.size)
        assertEquals(ReminderKind.APPOINTMENT, r.single().kind)
        assertEquals(at(2026, 10, 14, 19), r.single().at)
        assertEquals(visit, r.single().subject)
    }

    @Test
    fun theWindowIsOpenAtTheStartAndClosedAtTheEnd() {
        val r = between(at(2026, 10, 10, 9), at(2026, 10, 10, 9, 0), regimens = listOf(ev), doses = evDoses)
        assertTrue(r.isEmpty())
        val inclusive = between(at(2026, 10, 10, 8, 59), at(2026, 10, 10, 9, 0), regimens = listOf(ev), doses = evDoses)
        assertEquals(listOf(ReminderKind.INJECTION_DAY), inclusive.map { it.kind })
    }

    @Test
    fun nextIsTheEarliestOneAhead() {
        val next = ReminderPlan.next(listOf(ev, cpa), evDoses + cpaDoses, emptyList(), null, on, now, SEOUL)!!
        assertEquals(ReminderKind.DAILY, next.kind)
        assertEquals(at(2026, 10, 6, 8, 30), next.at)
        assertNull(ReminderPlan.next(listOf(ev), evDoses, emptyList(), null, settings, now, SEOUL))
    }

    @Test
    fun theSameOccurrenceHasTheSameKeyHoweverOftenItIsWorkedOut() {
        val a = between(now, at(2026, 10, 12), regimens = listOf(ev), doses = evDoses)
        val b = between(at(2026, 10, 7), at(2026, 10, 20), regimens = listOf(ev), doses = evDoses)
        assertEquals(a.map { it.key }.toSet(), b.map { it.key }.toSet().intersect(a.map { it.key }.toSet()))
        assertEquals(a.size, a.map { it.key }.distinct().size)
    }

    @Test
    fun onlyAReminderForADoseCanBeAnsweredWithDone() {
        val day = Reminder(at(2026, 10, 10, 9), ReminderKind.INJECTION_DAY, at(2026, 10, 10, 9), ev)
        val eve = day.copy(kind = ReminderKind.INJECTION_EVE)
        val lab = Reminder(at(2026, 10, 9, 9), ReminderKind.LAB, at(2026, 10, 9, 9))
        assertTrue(day.canComplete)
        assertFalse(eve.canComplete)
        assertFalse(lab.canComplete)
    }

    @Test
    fun injectionsAndPatchesAreDayTypeButTabletsAndGelAreNot() {
        assertTrue(ReminderPlan.isDayType(ev))
        assertTrue(ReminderPlan.isDayType(ev.copy(route = Route.SC_INJECTION)))
        assertTrue(ReminderPlan.isDayType(ev.copy(route = Route.PATCH)))
        assertFalse(ReminderPlan.isDayType(cpa))
        assertFalse(ReminderPlan.isDayType(ev.copy(route = Route.GEL)))
        assertFalse(ReminderPlan.isDayType(ev.copy(route = Route.SUBLINGUAL)))
    }

    // ── the words ─────────────────────────────────────────────

    private fun reminder(kind: ReminderKind, regimen: com.hormonelog.core.domain.Regimen? = ev, subject: java.time.Instant = at(2026, 10, 10, 9)) =
        Reminder(subject, kind, subject, regimen)

    @Test
    fun theFullWordsNameTheDoseAndOfferDoneAndLater() {
        val t = ReminderText.of(reminder(ReminderKind.INJECTION_DAY), neutral = false, fmt = fmt)
        assertEquals("오늘 주사일이에요", t.title)
        assertEquals("EV 5 mg · 주사", t.body)
        assertEquals("투약 완료", t.done)
        assertEquals("나중에", t.later)
    }

    @Test
    fun theNeutralWordsNameNoDrugNoValueAndNotTheApp() {
        for (kind in ReminderKind.entries) {
            val t = ReminderText.of(reminder(kind), neutral = true, fmt = fmt)
            val all = listOfNotNull(t.title, t.body, t.done, t.later).joinToString(" ")
            assertNull("neutral $kind carries a body", t.body)
            for (word in listOf("EV", "mg", "에스트라디올", "주사", "호르몬", "사이프로테론", "검사할")) {
                assertFalse("neutral $kind says '$word': $all", all.contains(word))
            }
        }
        assertEquals("오늘 기록할 항목이 있어요", ReminderText.of(reminder(ReminderKind.INJECTION_DAY), true, fmt).title)
        assertEquals("완료", ReminderText.of(reminder(ReminderKind.DAILY, cpa), true, fmt).done)
    }

    @Test
    fun aPatchIsChangedNotInjected() {
        val patch = ev.copy(drug = Drug.ESTRADIOL_PATCH, route = Route.PATCH, amountEntered = 50.0, enteredUnit = DoseUnit.UG_PER_DAY)
        val t = ReminderText.of(reminder(ReminderKind.INJECTION_DAY, patch), neutral = false, fmt = fmt)
        assertEquals("오늘 패치 교체일이에요", t.title)
        assertEquals("패치 50 µg/일", t.body)
        assertEquals("내일 패치 교체일이에요", ReminderText.of(reminder(ReminderKind.INJECTION_EVE, patch), false, fmt).title)
    }

    @Test
    fun theEveningBeforeAndTheLabAndTheVisitOfferNoButtons() {
        val eve = ReminderText.of(reminder(ReminderKind.INJECTION_EVE), false, fmt)
        assertNull(eve.done)
        assertNull(eve.later)
        val labText = ReminderText.of(reminder(ReminderKind.LAB, null), false, fmt)
        assertEquals("검사할 때가 됐어요", labText.title)
        assertNull(labText.done)
        val visit = ReminderText.of(reminder(ReminderKind.APPOINTMENT, null, at(2026, 10, 15, 10, 30)), false, fmt)
        assertEquals("내일 진료 예약이 있어요", visit.title)
        assertEquals("10월 15일 (목) 오전 10:30", visit.body)
    }

    @Test
    fun aLockedScreenAlwaysGetsTheSameNeutralLine() {
        assertEquals("확인할 일정이 있어요", ReminderText.LOCK_SCREEN_TITLE)
        assertFalse(ReminderText.LOCK_SCREEN_TITLE.contains("투약"))
    }
}
