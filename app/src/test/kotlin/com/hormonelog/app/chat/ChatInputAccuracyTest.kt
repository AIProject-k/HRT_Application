package com.hormonelog.app.chat

import com.hormonelog.core.domain.Drug
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatInputAccuracyTest {
    private val none = emptyList<String>()
    private fun batch(vararg doses: ChatCommand) = ChatHarness.batch("오늘", *doses)

    @Test fun negatedDoseIsNotSaved() {
        assertEquals(none, ChatHarness().send("방금 프로기노바 2mg 복용 못했어").doseTimes())
    }

    @Test fun questionAboutDoseIsNotSaved() {
        assertEquals(none, ChatHarness().send("방금 프로기노바 2mg 먹었어?").doseTimes())
    }

    @Test fun unreadDateIsNotSavedAsToday() {
        assertEquals(none, ChatHarness().send("10월 23일 방금 프로기노바 2mg 복용").doseTimes())
        assertEquals(none, ChatHarness().send("내일 방금 프로기노바 2mg 복용").doseTimes())
    }

    @Test fun swappedDoseCandidatesAreNotSaved() {
        val swapped = ChatHarness(model = {
            ChatHarness.structured(batch(ChatHarness.dose("프로기노바", "25", "20:00"), ChatHarness.dose("안쿨", "2", "10:00")))
        })
        assertEquals(none, swapped.send("오늘 10:00 프로기노바 2mg 복용, 오늘 20:00 안쿨 25mg 복용").doseTimes())
    }

    @Test fun negationDoesNotPoisonTheNextRequest() {
        val h = ChatHarness().send("방금 프로기노바 2mg 복용하지 않았어").send("방금 프로기노바 2mg 복용")
        assertEquals(listOf("2026-10-24 23:00"), h.doseTimes())
        assertEquals(0, h.modelCalls)
    }

    @Test fun explicitKoreanDateWithMorningTimeIsSaved() {
        val h = ChatHarness().send("2026년 10월 24일 오전 10시 프로기노바 2mg 복용")
        assertEquals(listOf("2026-10-24 10:00"), h.doseTimes())
        assertEquals(0, h.modelCalls)
    }

    @Test fun dottedDateIsSaved() {
        assertEquals(listOf("2026-10-24 10:00"), ChatHarness().send("2026.10.24 10:00 프로기노바 2mg 복용").doseTimes())
    }

    @Test fun twentyFourHourTimeWithoutMinutesIsSaved() {
        assertEquals(listOf("2026-10-24 22:00"), ChatHarness().send("오늘 22시 프로기노바 2mg 복용").doseTimes())
    }

    @Test fun eveningTimeIsSaved() {
        assertEquals(listOf("2026-10-24 22:00"), ChatHarness().send("오늘 저녁 10시 프로기노바 2mg 복용").doseTimes())
    }

    @Test fun relativeMinutesCrossMidnightExactly() {
        assertEquals(listOf("2026-10-25 23:40"), ChatHarness(start = "2026-10-25T15:10:00Z").send("30분 전 프로기노바 2mg 복용").doseTimes())
    }

    @Test fun koreanMilligramUnitIsSaved() {
        val h = ChatHarness().send("방금 프로기노바 2밀리그램 복용")
        assertEquals(listOf("2026-10-24 23:00"), h.doseTimes())
        assertEquals(2.0, h.doses.single().amountEntered, 0.0)
    }

    @Test fun yearlessDateAsksForYearAndSavesOnlyAfterConfirmation() {
        val h = ChatHarness().send("10월 24일 오전 10시 프로기노바 2mg 복용")
        assertEquals(none, h.doseTimes())
        h.send("네")
        assertEquals(listOf("2026-10-24 10:00"), h.doseTimes())
        assertEquals(0, h.modelCalls)
    }

    @Test fun shortTimeAnswerFillsOnlyTheMissingField() {
        val h = ChatHarness().send("오늘 프로기노바 2mg 복용").send("10시").send("오전")
        assertEquals(listOf("2026-10-24 10:00"), h.doseTimes())
        assertEquals(0, h.modelCalls)
    }

    @Test fun unitAnswerFillsOnlyTheDoseAmount() {
        val h = ChatHarness().send("방금 프로기노바 2 복용").send("mg")
        assertEquals(listOf("2026-10-24 23:00"), h.doseTimes())
        assertEquals(2.0, h.doses.single().amountEntered, 0.0)
    }

    @Test fun newDrugRequestStartsFreshWithoutMergingIntoThePendingOne() {
        val h = ChatHarness().send("오늘 프로기노바 2mg 복용").send("방금 안쿨 25mg 복용")
        assertEquals(listOf(Drug.CYPROTERONE), h.doses.map { it.drug })
        assertEquals(0, h.modelCalls)
    }

    @Test fun plainJustNowDoseIsSavedWithoutModel() {
        val h = ChatHarness().send("방금 프로기노바 2mg 복용")
        assertEquals(listOf("2026-10-24 23:00"), h.doseTimes())
        assertEquals(0, h.modelCalls)
    }

    @Test fun correctlyBoundBatchIsSaved() {
        val bound = ChatHarness(model = {
            ChatHarness.structured(batch(ChatHarness.dose("프로기노바", "2", "10:00"), ChatHarness.dose("안쿨", "25", "20:00")))
        })
        assertEquals(listOf("2026-10-24 10:00", "2026-10-24 20:00"),
            bound.send("오늘 10:00 프로기노바 2mg 복용, 오늘 20:00 안쿨 25mg 복용").doseTimes())
    }

    @Test fun cancelEndsOnlyTheOpenRequest() {
        val h = ChatHarness().send("오늘 프로기노바 2mg 복용")
        h.chat.cancel()
        assertEquals(listOf("2026-10-24 23:00"), h.send("방금 프로기노바 2mg 복용").doseTimes())
    }

    @Test fun explicitPmTimeWithHalfHourIsSaved() {
        assertEquals(listOf("2026-10-24 22:30"), ChatHarness().send("오늘 오후 10시 반 프로기노바 2mg 복용").doseTimes())
    }

    @Test fun yesterdayWithClockTimeIsSaved() {
        assertEquals(listOf("2026-10-23 10:00"), ChatHarness().send("어제 10:00 프로기노바 2mg 복용").doseTimes())
    }

    @Test fun impossibleOrFutureDatesAndUnsupportedUnitsAreRefused() {
        assertEquals(none, ChatHarness().send("2026-10-25 10:00 프로기노바 2mg 복용").doseTimes())
        assertEquals(none, ChatHarness().send("2026-02-30 10:00 프로기노바 2mg 복용").doseTimes())
        assertEquals(none, ChatHarness().send("방금 프로기노바 1정 복용").doseTimes())
    }

    @Test fun negatedWordingWithEatIsNeverSaved() {
        assertEquals(none, ChatHarness().send("방금 프로기노바 2mg 먹지 않았어").doseTimes())
    }
}
