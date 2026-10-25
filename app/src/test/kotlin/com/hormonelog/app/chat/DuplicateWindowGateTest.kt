package com.hormonelog.app.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The 6-hour window still guards against a re-tap of a saved dose, but items of one batch are not
 * compared with each other; only a clash with a saved record, or the same drug at the same time, stops the batch.
 */
class DuplicateWindowGateTest {
    private val batchTwo = "오늘 12:00 프로기노바 2mg 복용, 오늘 20:00 안쿨 25mg 복용"

    private fun answering(vararg doses: ChatCommand) =
        ChatHarness(model = { ChatHarness.structured(ChatHarness.batch("오늘", *doses)) })

    @Test fun batchItemNearASavedRecordStopsTheWholeBatch() {
        val h = answering(ChatHarness.dose("프로기노바", "2mg", "12:00"), ChatHarness.dose("안쿨", "25mg", "20:00"))
        h.send("오늘 10:00 프로기노바 2mg 복용")
        h.send(batchTwo)
        assertEquals(1, h.doses.size)
    }

    @Test fun batchOfSameDrugSameTimeIsRefused() {
        val h = answering(ChatHarness.dose("프로기노바", "2mg", "10:00"), ChatHarness.dose("프로기노바", "2mg", "10:00"))
        h.send("오늘 10:00 프로기노바 2mg 복용, 오늘 10:00 프로기노바 2mg 복용")
        assertEquals(0, h.doses.size)
    }

    @Test fun batchOfSameDrugHourApartIsSaved() {
        val h = answering(
            ChatHarness.dose("프로기노바", "2mg", "10:00"), ChatHarness.dose("프로기노바", "2mg", "11:00"),
            ChatHarness.dose("프로기노바", "2mg", "12:00"),
        )
        h.send("오늘 10:00 프로기노바 2mg 복용, 오늘 11:00 프로기노바 2mg 복용, 오늘 12:00 프로기노바 2mg 복용")
        assertEquals(3, h.doses.size)
    }
}
