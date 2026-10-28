package com.hormonelog.app.chat

import com.hormonelog.core.domain.Drug
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Korean chat cases for Provera (MPA) on the rule-based path, which needs no model. A refusal case must save no MPA dose;
 * a save case must save exactly the event it names. These are not measures of any on-device model.
 */
class ProveraChatCasesTest {
    private fun sent(vararg texts: String): ChatHarness {
        val h = ChatHarness()
        texts.forEach { h.send(it) }
        return h
    }

    private fun mpaDoses(h: ChatHarness) = h.doses.filter { it.drug == Drug.MEDROXYPROGESTERONE_ACETATE }

    @Test fun negationsQuestionsAndPlansSaveNoDose() {
        for (text in listOf(
            "프로베라 5mg 못 먹었어", "프로베라 5mg 먹었어?", "프로베라 5mg 먹을 거야", "내일 프로베라 10mg 예정",
            "프로베라 복용 안 함", "프로베라 5mg 복용하는 게 좋을까?", "프로베라 5mg 먹었다는 예시",
        )) {
            val h = sent(text)
            assertTrue("saved for: $text", h.doses.isEmpty())
        }
    }

    @Test fun aClearOralDoseIsSavedOnceWithItsOwnAmount() {
        for ((text, mg) in listOf("방금 프로베라 2.5mg 복용" to 2.5, "어제 21시 프로베라 5mg 먹었어" to 5.0, "방금 프로베라 10mg 먹었어" to 10.0)) {
            val h = sent(text)
            assertEquals("$text -> ${h.lastReply}", 1, mpaDoses(h).size)
            assertEquals("$text -> ${h.lastReply}", mg, mpaDoses(h).single().amountEntered, 0.0)
        }
    }

    @Test fun aSentenceWithTwoDrugsNeverCrossesAmountsBetweenThem() {
        val h = sent("유트로게스탄 200mg, 프로베라 5mg")
        for (d in h.doses) {
            val paired = (d.drug == Drug.PROGESTERONE_MICRONIZED && d.amountEntered == 200.0) ||
                (d.drug == Drug.MEDROXYPROGESTERONE_ACETATE && d.amountEntered == 5.0)
            assertTrue("crossed pair: $d", paired)
        }
    }

    @Test fun injectionSublingualDepotAndUnitlessAmountsSaveNoOralMpaDose() {
        for (text in listOf("프로베라 주사", "데포프로베라 맞았어", "데포프로베라 5mg 맞았어", "프로베라 설하 5mg", "프로베라 10mL", "프로베라 10")) {
            val h = sent(text)
            assertTrue("saved for: $text", mpaDoses(h).isEmpty())
        }
    }

    @Test fun aUnitGivenInTheNextTurnFillsOnlyThatSlot() {
        val h = sent("방금 프로베라 5 먹었어", "mg")
        assertEquals("unit reply: ${h.lastReply}", 1, mpaDoses(h).size)
        assertEquals(5.0, mpaDoses(h).single().amountEntered, 0.0)
    }

    @Test fun aDatedDoseWaitsForTheDateToBeConfirmedBeforeItIsSaved() {
        val h = sent("10월 23일 오전 9시 프로베라 10mg 복용")
        assertTrue(h.lastReply, h.lastReply.contains("기록할까요"))
        assertTrue(mpaDoses(h).isEmpty())
        h.send("네")
        assertEquals(h.lastReply, 1, mpaDoses(h).size)
        assertEquals(10.0, mpaDoses(h).single().amountEntered, 0.0)
    }

    @Test fun aReceivedPrescriptionIsNotADose() {
        val h = sent("방금 프로베라 5mg 받았어")
        assertTrue(h.lastReply, mpaDoses(h).isEmpty())
    }

    @Test fun aSymptomInTheSameSentenceNeverInventsAnotherDose() {
        val h = sent("프로베라 5mg 먹었고 어지러워")
        assertTrue(mpaDoses(h).size <= 1)
        assertTrue(mpaDoses(h).all { it.amountEntered == 5.0 })
    }
}
