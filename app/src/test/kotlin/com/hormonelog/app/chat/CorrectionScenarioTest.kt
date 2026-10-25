package com.hormonelog.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "아니 4mg" changes only the amount of the open dose; the date and time keep their own source. */
class CorrectionScenarioTest {
    @Test fun amountCorrectionKeepsTheTimeOfTheOpenDose() {
        val h = ChatHarness().send("오늘 프로기노바 2mg 복용").send("아니 4mg").send("오늘 오전 10시")
        assertEquals(listOf("2026-10-24 10:00"), h.doseTimes())
        assertEquals(4.0, h.doses.single().amountEntered, 0.0)
        assertEquals(0, h.modelCalls)
    }

    @Test fun correctionWithNothingOpenSavesNothingAndPointsToTheTimeline() {
        val h = ChatHarness().send("아니 4mg")
        assertTrue(h.doses.isEmpty())
        assertEquals(0, h.modelCalls)
        assertTrue(h.lastReply.contains("타임라인"))
    }

    @Test fun negationIsNotAnAmountCorrection() {
        val h = ChatHarness().send("오늘 프로기노바 2mg 복용").send("아니 안 먹었어")
        assertTrue(h.doses.isEmpty())
        assertEquals(0, h.modelCalls)
    }
}
