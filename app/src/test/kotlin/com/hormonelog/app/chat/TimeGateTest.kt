package com.hormonelog.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Korean date, time and unit answers. The clock is the message arrival time, fixed per test.
 * A follow-up answers only the field it asks for; a time or date is never guessed.
 */
class TimeGateTest {
    private fun doseAt(h: ChatHarness): List<String> = h.doses.map { "${it.amountEntered}mg ${ChatHarness.kst(it.occurredAt)}" }.sorted()

    @Test fun unitAnswerDoesNotTouchTheTime() {
        val h = ChatHarness().send("오늘 10:00 프로기노바 2 복용").send("mg")
        assertEquals(listOf("2.0mg 2026-10-24 10:00"), doseAt(h))
    }

    @Test fun unitAnswerDoesNotTouchTheFullDate() {
        val h = ChatHarness().send("2026-10-24 10:00 프로기노바 2 복용").send("mg")
        assertEquals(listOf("2.0mg 2026-10-24 10:00"), doseAt(h))
    }

    @Test fun unitAnswerAfterRelativeTimeKeepsTheArrivalTime() {
        val h = ChatHarness().send("방금 프로기노바 2 복용").send("mg")
        assertEquals(listOf("2.0mg 2026-10-24 23:00"), doseAt(h))
    }

    @Test fun amPmAnswerFillsOnlyTheHourField() {
        val h = ChatHarness().send("오늘 프로기노바 2mg 복용").send("10시").send("오전")
        assertEquals(listOf("2.0mg 2026-10-24 10:00"), doseAt(h))
    }

    @Test fun confirmationAfterAmPmSavesExactlyOneRecord() {
        val h = ChatHarness().send("10월 24일 10시 프로기노바 2mg 복용").send("오전").send("네")
        assertEquals(listOf("2.0mg 2026-10-24 10:00"), doseAt(h))
    }

    @Test fun confirmationBeforeAmPmSavesExactlyOneRecord() {
        val h = ChatHarness().send("10월 24일 10시 프로기노바 2mg 복용").send("네").send("오전")
        assertEquals(listOf("2.0mg 2026-10-24 10:00"), doseAt(h))
    }

    /** A correction changes only the open request's amount; the time it already has and the time asked later stay. */
    @Test fun correctionChangesOnlyTheOpenAmount() {
        val h = ChatHarness().send("오늘 프로기노바 2mg 복용").send("아니 4mg").send("10시").send("오전")
        assertEquals(listOf("4.0mg 2026-10-24 10:00"), doseAt(h))
    }

    @Test fun correctionAfterASavedRecordIsRefusedNotApplied() {
        val h = ChatHarness().send("오늘 10:00 프로기노바 2mg 복용").send("아니 4mg")
        assertEquals(listOf("2.0mg 2026-10-24 10:00"), doseAt(h))
    }

    @Test fun yesterdayLateNightIsResolvedFromArrivalTime() {
        val h = ChatHarness(start = "2026-10-24T15:10:00Z").send("어제 23:50 프로기노바 2mg 복용")
        assertEquals(listOf("2.0mg 2026-10-24 23:50"), doseAt(h))
    }

    @Test fun justNowAfterMidnightUsesTheNewDay() {
        val h = ChatHarness(start = "2026-10-24T15:10:00Z").send("방금 프로기노바 2mg 복용")
        assertEquals(listOf("2.0mg 2026-10-25 00:10"), doseAt(h))
    }

    @Test fun thirtyMinutesAgoUsesTheArrivalTime() {
        val h = ChatHarness().send("30분 전 프로기노바 2mg 복용")
        assertEquals(listOf("2.0mg 2026-10-24 22:30"), doseAt(h))
    }

    @Test fun leapDayIsAcceptedAndNonLeapDayIsRefused() {
        val leap = ChatHarness().send("2024-02-29 10:00 프로기노바 2mg 복용")
        assertEquals(listOf("2.0mg 2024-02-29 10:00"), doseAt(leap))
        val nonLeap = ChatHarness().send("2026-02-29 10:00 프로기노바 2mg 복용")
        assertTrue("saved a day that does not exist: ${doseAt(nonLeap)}", nonLeap.doses.isEmpty())
    }

    @Test fun vagueTimesAreNeverGuessedIntoARecord() {
        for (text in listOf("오늘 10시쯤 프로기노바 2mg 복용", "오늘 24:00 프로기노바 2mg 복용", "오늘 밤2시 프로기노바 2mg 복용")) {
            val h = ChatHarness().send(text)
            assertTrue("guessed a time from: $text -> ${doseAt(h)}", h.doses.isEmpty())
        }
    }

    @Test fun refusedMessageDoesNotLeakIntoTheNextRecord() {
        val h = ChatHarness().send("오늘 10시쯤 프로기노바 2mg 복용").send("방금 프로기노바 2mg 복용")
        assertEquals(listOf("2.0mg 2026-10-24 23:00"), doseAt(h))
    }
}
