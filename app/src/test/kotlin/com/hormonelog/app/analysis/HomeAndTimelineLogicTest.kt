package com.hormonelog.app.analysis

import com.hormonelog.app.SEOUL
import com.hormonelog.app.at
import com.hormonelog.app.daily
import com.hormonelog.app.dailyDoses
import com.hormonelog.app.dose
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.lab
import com.hormonelog.app.saturdayDoses
import com.hormonelog.app.settings
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.TimelinePeriod
import com.hormonelog.app.state.TimelineType
import com.hormonelog.app.state.TimelineUi
import com.hormonelog.app.weekly
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HomeLogicTest {
    private val now = at(2026, 10, 5, 16, 30)

    @Test
    fun theNextDoseIsTheEstrogenEvenWhenATabletComesSooner() {
        val ev = weekly(at(2026, 9, 19))
        val cpa = daily(at(2026, 3, 1, 8, 30))
        val doses = saturdayDoses(at(2026, 9, 19), at(2026, 10, 3)) + dailyDoses(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 10, 4))
        val next = HomeLogic.nextDose(listOf(ev, cpa), doses, now, SEOUL)!!
        assertEquals(Drug.ESTRADIOL_VALERATE, next.regimen.drug)
        assertEquals(at(2026, 10, 10, 9), next.at)
        assertFalse(next.overdue)
    }

    @Test
    fun anAntiAndrogenAloneIsStillShownAndTodaysMissingTabletIsOverdue() {
        val cpa = daily(at(2026, 3, 1, 8, 30))
        val doses = dailyDoses(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 10, 4))
        // It is 16:30 on the 5th and this morning's tablet was never recorded.
        val next = HomeLogic.nextDose(listOf(cpa), doses, now, SEOUL)!!
        assertEquals(at(2026, 10, 5, 8, 30), next.at)
        assertTrue(next.overdue)
        // Once it is recorded, tomorrow's is next.
        val taken = doses + dose(at(2026, 10, 5, 8, 40), drug = Drug.CYPROTERONE, route = Route.ORAL, amount = 12.5)
        val after = HomeLogic.nextDose(listOf(cpa), taken, now, SEOUL)!!
        assertEquals(at(2026, 10, 6, 8, 30), after.at)
        assertFalse(after.overdue)
    }

    @Test
    fun aDoseThatShouldHaveBeenTakenIsOverdueNotSkipped() {
        val ev = weekly(at(2026, 9, 19))
        val doses = saturdayDoses(at(2026, 9, 19), at(2026, 10, 3))
        val next = HomeLogic.nextDose(listOf(ev), doses, at(2026, 10, 10, 9, 30), SEOUL)!!
        assertEquals(at(2026, 10, 10, 9), next.at)
        assertTrue(next.overdue)
    }

    @Test
    fun noRunningPlanMeansNoNextDose() {
        assertNull(HomeLogic.nextDose(emptyList(), emptyList(), now, SEOUL))
        val ended = weekly(at(2026, 3, 7), end = at(2026, 9, 18))
        assertNull(HomeLogic.nextDose(listOf(ended), emptyList(), now, SEOUL))
    }

    @Test
    fun theLastLabIsTheNewestRealE2WithADrawTime() {
        val labs = listOf(
            lab(at(2026, 2, 20, 10), e2 = 28.0, tt = 520.0, baseline = true),
            lab(at(2026, 8, 14, 11), e2 = 188.0),
            lab(at(2026, 9, 22, 14), e2 = 168.0, tt = 41.0),
            lab(null, e2 = 410.0),
            lab(at(2026, 10, 1, 10), tt = 40.0),
        )
        val last = HomeLogic.lastLab(labs)!!
        assertEquals(168.0, last.reported, 0.0)
        assertEquals("pg/mL", last.unit)
        assertEquals(at(2026, 9, 22, 14), last.lab.collectedAt)
        assertNull(HomeLogic.lastLab(labs.take(1)))
        assertNull(HomeLogic.lastLab(emptyList()))
    }

    @Test
    fun theBackupNudgeAppearsAfterAMonthOrWhenThereWasNeverOne() {
        val s = settings
        assertNull(HomeLogic.backupNudgeDays(s, hasRecords = false, now = now))
        assertEquals(-1, HomeLogic.backupNudgeDays(s, hasRecords = true, now = now))
        assertNull(HomeLogic.backupNudgeDays(s.copy(lastBackupAtMillis = at(2026, 9, 25).toEpochMilli()), true, now))
        assertEquals(33, HomeLogic.backupNudgeDays(s.copy(lastBackupAtMillis = at(2026, 9, 2, 10).toEpochMilli()), true, now))
    }

    @Test
    fun aHiddenNudgeStaysHiddenUntilItsDayThenComesBack() {
        val old = settings.copy(lastBackupAtMillis = at(2026, 9, 2, 10).toEpochMilli())
        val hidden = old.copy(backupBannerHiddenUntilMillis = at(2026, 10, 12).toEpochMilli())
        assertNull(HomeLogic.backupNudgeDays(hidden, true, now))
        assertNotNull(HomeLogic.backupNudgeDays(hidden, true, at(2026, 10, 13)))
    }
}

class TimelineLogicTest {
    private val now = at(2026, 10, 5, 16, 30)
    private val fmt = Fmt(SEOUL, clock24 = false, now = now)

    private val tablets = dailyDoses(LocalDate.of(2026, 8, 20), LocalDate.of(2026, 10, 4), missed = setOf(LocalDate.of(2026, 10, 2)))
    private val injections = saturdayDoses(at(2026, 9, 19), at(2026, 10, 3))
    private val gel = listOf(
        dose(at(2026, 9, 14, 21), drug = Drug.ESTRADIOL_GEL, route = Route.GEL, amount = 1.5),
        dose(at(2026, 9, 21, 21), drug = Drug.ESTRADIOL_GEL, route = Route.GEL, amount = 1.5),
    )
    private val base = AppState(
        settings = settings,
        doses = (tablets + injections + gel).sortedBy { it.occurredAt },
        labs = listOf(lab(at(2026, 9, 22, 14), e2 = 168.0), lab(null, e2 = 410.0)),
    )

    private fun build(ui: TimelineUi = TimelineUi(), s: AppState = base) =
        TimelineLogic.build(s.copy(timeline = ui), fmt, Analysis.calibrate(s.doses, s.labs, s.settings.gonadalStatus))

    @Test
    fun aMedicineTakenDayAfterDayFoldsIntoOneCardForTheMonth() {
        val m = build()
        // The lab with no draw time lands in its own group at the very end.
        assertEquals(listOf("2026-10", "2026-09", "2026-08", "unknown"), m.months.map { it.key })

        val september = m.months.first { it.key == "2026-09" }
        val fold = september.folds.single()
        assertEquals(30, fold.days.size)
        assertTrue(fold.days.all { it.state == FoldState.TAKEN })
        assertTrue("an every-day month says so: ${fold.title}", fold.title.endsWith("· 매일"))
        assertTrue(fold.antiandrogen)
        // The folded tablets are not listed a second time as rows.
        assertTrue(september.days.flatMap { it.rows }.none { it.drug == Drug.CYPROTERONE })
    }

    @Test
    fun aMonthWithOnlyAFewDaysKeepsIndividualRowsAndNamesTheMissedOne() {
        val october = build().months.first { it.key == "2026-10" }
        assertTrue(october.folds.isEmpty())
        val cpaRows = october.days.flatMap { it.rows }.filter { it.drug == Drug.CYPROTERONE }
        assertEquals(4, cpaRows.size)
        val missed = cpaRows.single { it.kind == RowKind.MISS }
        assertTrue(missed.title.endsWith("· 놓침"))
        assertEquals(LocalDate.of(2026, 10, 2), missed.date)
    }

    @Test
    fun gelStaysARowAndSaysItIsNotOnTheCurve() {
        val rows = build().months.flatMap { it.days }.flatMap { it.rows }.filter { it.kind == RowKind.GEL }
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.sub.contains("곡선 제외") })
    }

    @Test
    fun theMissedFilterShowsOnlyMissedDosesAndNoFolds() {
        val m = build(TimelineUi(type = TimelineType.MISSED))
        assertEquals(1, m.rowCount)
        assertTrue(m.months.all { it.folds.isEmpty() })
        assertEquals(RowKind.MISS, m.months.single().days.single().rows.single().kind)
    }

    @Test
    fun searchingListsEveryMatchingRowInsteadOfFolding() {
        val m = build(TimelineUi(query = "사이프로"))
        assertEquals(tablets.size, m.rowCount)
        assertTrue(m.months.all { it.folds.isEmpty() })
        assertEquals(0, build(TimelineUi(query = "없는검색어")).rowCount)
    }

    @Test
    fun theDrugFilterKeepsOnlyThatDrug() {
        val m = build(TimelineUi(drug = Drug.ESTRADIOL_VALERATE))
        val rows = m.months.flatMap { it.days }.flatMap { it.rows }
        assertEquals(injections.size, rows.size)
        assertTrue(rows.all { it.drug == Drug.ESTRADIOL_VALERATE })
        assertTrue(m.months.all { it.folds.isEmpty() })
    }

    @Test
    fun aShortPeriodDropsOlderRowsAndOlderFoldedMonthsToo() {
        val m = build(TimelineUi(period = TimelinePeriod.MONTH))
        // Thirty days back from 5 October is 5 September: August is out entirely, and so is every row before that day.
        assertTrue("older folded month still shown: ${m.months.map { it.key }}", m.months.none { it.key == "2026-08" })
        val rows = m.months.flatMap { it.days }.flatMap { it.rows }
        assertTrue(rows.all { it.date!! >= LocalDate.of(2026, 9, 5) })
    }

    @Test
    fun aLabWithoutADrawTimeGoesToAGroupAtTheEnd() {
        val m = build(TimelineUi(type = TimelineType.LAB))
        assertEquals(listOf("2026-09", "unknown"), m.months.map { it.key })
        val unknown = m.months.last()
        assertEquals("시간 미상", unknown.label)
        val row = unknown.days.single().rows.single()
        assertEquals("채혈 시각 없음", row.sub)
        assertNull(row.at)
    }

    @Test
    fun aLabRowSaysHowLongAfterTheDoseAndWhetherItShapedTheCurve() {
        val row = build(TimelineUi(type = TimelineType.LAB)).months.first().days.single().rows.single()
        assertEquals("E2 168 pg/mL", row.title)
        assertTrue(row.sub, row.sub.startsWith("투약 후"))
    }

    @Test
    fun adherenceCoversTheTwoNewestMonths() {
        val a = build().adherence
        assertEquals(listOf("10월 투약", "9월 투약"), a.map { it.label })
        // October: the four tablets (one missed) and the injection on the 3rd.
        assertEquals(5, a[0].total)
        assertEquals(4, a[0].taken)
        assertEquals(80, a[0].percent)
        assertEquals("놓침 1회 · 10월 2일", a[0].note)
        assertEquals(100, a[1].percent)
        assertEquals("놓친 기록 없음", a[1].note)
    }

    @Test
    fun theDrugChipsListWhatWasActuallyTakenMostUsedFirst() {
        assertEquals(Drug.CYPROTERONE, build().drugs.first())
        assertTrue(build().drugs.containsAll(listOf(Drug.ESTRADIOL_VALERATE, Drug.ESTRADIOL_GEL)))
    }

    @Test
    fun anEmptyAppHasNoRecordsAndSaysSo() {
        val m = TimelineLogic.build(AppState(settings = settings), fmt, Analysis.calibrate(emptyList(), emptyList(), settings.gonadalStatus))
        assertFalse(m.hasAnyRecord)
        assertTrue(m.months.isEmpty())
        assertTrue(m.adherence.isEmpty())
    }
}
