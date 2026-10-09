package com.hormonelog.app.state

import com.hormonelog.app.SEOUL
import com.hormonelog.app.at
import com.hormonelog.app.dayMillis
import com.hormonelog.app.dose
import com.hormonelog.app.settings
import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.E2Unit
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.RecordSource
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.TUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingOpsTest {
    /** Monday 5 October 2026, 16:30 in Seoul. */
    private val now = at(2026, 10, 5, 16, 30)
    private val fresh = AppState(settings = AppSettings(), screen = Screen.ONBOARDING)

    private fun at(step: Int, block: (OnboardingState) -> OnboardingState = { it }) =
        fresh.copy(onboarding = block(OnboardingState(step = step)))

    @Test
    fun theFirstStepsMoveOnOneByOne() {
        var s = OnboardingOps.next(fresh, now, SEOUL)
        assertEquals(1, s.onboarding.step)
        assertEquals(Screen.ONBOARDING, s.screen)
        s = OnboardingOps.back(OnboardingOps.back(s))
        assertEquals(0, s.onboarding.step)
    }

    @Test
    fun theScheduleStepWaitsForADoseButAPatchNeedsNone() {
        assertEquals(1, OnboardingOps.next(at(1), now, SEOUL).onboarding.step)
        val typed = at(1) { it.copy(amountText = "5") }
        assertEquals(2, OnboardingOps.next(typed, now, SEOUL).onboarding.step)
        val patch = at(1) { it.copy(route = Route.PATCH, drug = Drug.ESTRADIOL_PATCH) }
        assertTrue(OnboardingOps.scheduleReady(patch.onboarding))
        // Beyond the largest dose the recorder accepts, the step stays put.
        assertFalse(OnboardingOps.scheduleReady(at(1) { it.copy(amountText = "9999") }.onboarding))
        assertFalse(OnboardingOps.scheduleReady(at(1) { it.copy(amountText = "0") }.onboarding))
    }

    @Test
    fun skippingTheScheduleLeavesNothingToBackfillAndBackComesBackToIt() {
        val skipped = OnboardingOps.skip(at(1))
        assertTrue(skipped.onboarding.noSchedule)
        assertEquals(3, skipped.onboarding.step)
        assertNull(OnboardingOps.regimenOf(skipped.onboarding, now, SEOUL))
        assertEquals(1, OnboardingOps.back(skipped).onboarding.step)
    }

    @Test
    fun anyOtherStepCanBeSkippedToTheNext() {
        assertEquals(5, OnboardingOps.skip(at(4)).onboarding.step)
        // The last step has nothing after it to skip to.
        assertEquals(OnboardingState.STEPS - 1, OnboardingOps.skip(at(OnboardingState.STEPS - 1)).onboarding.step)
    }

    @Test
    fun changingTheRouteKeepsTheDrugOnlyWhenItFits() {
        val oral = OnboardingOps.setRoute(at(1), Route.ORAL)
        assertEquals(Route.ORAL, oral.onboarding.route)
        assertFalse(oral.onboarding.drug == Drug.ESTRADIOL_VALERATE)
        val back = OnboardingOps.setRoute(oral, Route.IM_INJECTION)
        assertEquals(Drug.ESTRADIOL_VALERATE, back.onboarding.drug)
    }

    @Test
    fun theBackfillQuestionCountsPastDosesAndSkipsDaysAlreadyRecorded() {
        val o = OnboardingState(step = 2, amountText = "5", startMillis = dayMillis(2026, 9, 7))
        val s = fresh.copy(onboarding = o)
        // Mondays 9/7, 9/14, 9/21, 9/28 and this morning.
        assertEquals(5, OnboardingOps.backfillCount(s, now, SEOUL))
        val alreadyThere = s.copy(doses = listOf(dose(at(2026, 9, 14, 20))))
        assertEquals(4, OnboardingOps.backfillCount(alreadyThere, now, SEOUL))
        // Starting today leaves nothing in the past.
        assertEquals(0, OnboardingOps.backfillCount(s.copy(onboarding = o.copy(startMillis = dayMillis(2026, 10, 5))), now, SEOUL))
    }

    @Test
    fun finishingSetsEverythingUpAndGoesHome() {
        val o = OnboardingState(
            step = OnboardingState.STEPS - 1, route = Route.IM_INJECTION, drug = Drug.ESTRADIOL_VALERATE, amountText = "5",
            interval = IntervalChoice.WEEKLY, startMillis = dayMillis(2026, 9, 7), backfill = true,
            gonadal = GonadalStatus.POST_ORCHIECTOMY, e2Unit = E2Unit.PMOL_L, tUnit = TUnit.NMOL_L,
            baselineE2 = "103", baselineT = "18", baselineMillis = at(2026, 2, 20, 9).toEpochMilli(),
        )
        val s = OnboardingOps.next(fresh.copy(onboarding = o), now, SEOUL)

        assertTrue(s.settings.onboardingDone)
        assertEquals(GonadalStatus.POST_ORCHIECTOMY, s.settings.gonadalStatus)
        assertEquals(E2Unit.PMOL_L, s.settings.e2Unit)
        assertEquals(TUnit.NMOL_L, s.settings.tUnit)
        assertEquals(1, s.regimens.size)
        assertEquals(5, s.doses.size)
        assertTrue(s.doses.all { it.source == RecordSource.SCHEDULE })

        val baseline = s.labs.single()
        assertTrue(baseline.isBaseline)
        assertEquals(2, baseline.analytes.size)
        assertEquals(at(2026, 2, 20, 9), baseline.collectedAt)

        assertEquals(Screen.HOME, s.screen)
        assertEquals(emptyList<Screen>(), s.stack)
        assertEquals(OnboardingState(), s.onboarding)
    }

    @Test
    fun finishingWithNoScheduleLeavesNoPlanAndNoDoses() {
        val o = OnboardingState(step = OnboardingState.STEPS - 1, noSchedule = true)
        val s = OnboardingOps.next(fresh.copy(onboarding = o), now, SEOUL)
        assertEquals(Screen.HOME, s.screen)
        assertTrue(s.settings.onboardingDone)
        assertEquals(0, s.regimens.size)
        assertEquals(0, s.doses.size)
    }

    @Test
    fun decliningTheBackfillStillKeepsThePlan() {
        val o = OnboardingState(
            step = OnboardingState.STEPS - 1, amountText = "5", startMillis = dayMillis(2026, 9, 7), backfill = false,
        )
        val s = OnboardingOps.next(fresh.copy(onboarding = o), now, SEOUL)
        assertEquals(1, s.regimens.size)
        assertEquals(0, s.doses.size)
    }

    @Test
    fun aBaselineLabNeedsAValueAndNeverCalibratesAnything() {
        assertNull(OnboardingOps.baselineLab(OnboardingState(), SEOUL))
        assertNull(OnboardingOps.baselineLab(OnboardingState(baselineE2 = "0", baselineT = ""), SEOUL))
        val only = OnboardingOps.baselineLab(OnboardingState(baselineT = "520"), SEOUL)!!
        assertEquals(1, only.analytes.size)
        assertTrue(only.isBaseline)
        assertNull(only.collectedAt)
    }
}

class NavTest {
    @Test
    fun pushingStacksAndBackUnstacksOneScreenAtATime() {
        var s = AppState()
        s = Nav.push(s, Screen.SECURITY)
        s = Nav.push(s, Screen.RESTORE)
        assertEquals(listOf(Screen.HOME, Screen.SECURITY), s.stack)
        s = Nav.back(s)
        assertEquals(Screen.SECURITY, s.screen)
        s = Nav.back(s)
        assertEquals(Screen.HOME, s.screen)
        assertFalse(Nav.canGoBack(s))
    }

    @Test
    fun aQuestionClosesBeforeASheetAndASheetBeforeAScreen() {
        var s = Nav.push(AppState(), Screen.MEMOS).copy(sheet = Sheet.MemoEditor, confirm = Confirm.ClearAll)
        s = Nav.back(s)
        assertNull(s.confirm)
        assertEquals(Sheet.MemoEditor, s.sheet)
        s = Nav.back(s)
        assertEquals(Sheet.None, s.sheet)
        assertEquals(Screen.MEMOS, s.screen)
        s = Nav.back(s)
        assertEquals(Screen.HOME, s.screen)
    }

    @Test
    fun backFromAnotherTabGoesHomeBeforeLeavingTheApp() {
        val timeline = Nav.tab(AppState(), Screen.TIMELINE)
        assertTrue(Nav.canGoBack(timeline))
        assertEquals(Screen.HOME, Nav.back(timeline).screen)
    }

    @Test
    fun switchingTabsDropsTheStackAndAnyOpenSheet() {
        val s = Nav.tab(Nav.push(AppState(), Screen.SECURITY).copy(sheet = Sheet.Lab), Screen.FLOW)
        assertEquals(emptyList<Screen>(), s.stack)
        assertEquals(Sheet.None, s.sheet)
    }

    @Test
    fun onboardingOwnsItsBackButton() {
        assertFalse(Nav.canGoBack(AppState(screen = Screen.ONBOARDING)))
    }
}
