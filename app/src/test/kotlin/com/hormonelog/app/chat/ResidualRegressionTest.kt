package com.hormonelog.app.chat

import com.hormonelog.app.state.AppState
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for the residual defects R1–R6. Expectations are the correct behaviour, not what the
 * characterization probe observed (ResidualProbeTest records the defects; its assertions are not copied here).
 */
class ResidualRegressionTest {
    private val now = Instant.parse("2026-10-24T14:00:00Z")
    private fun dose(drug: String, amount: String, date: String, time: String) =
        ChatCommand("dose", drug = drug, amount = amount, amountUnit = "mg", date = date, time = time)

    // R1: the same drug twice; the amounts and times are swapped between the two records.
    @Test fun r1_swappedAmountsOfTheSameDrugAreRefused() {
        val input = "오늘 10:00 프로기노바 2mg 복용, 오늘 20:00 프로기노바 4mg 복용"
        val swapped = ChatCommand("batch", doses = listOf(dose("프로기노바", "4", "오늘", "10:00"), dose("프로기노바", "2", "오늘", "20:00")))
        assertNull(ChatCommands.apply(AppState(), swapped, listOf(input), UUID.randomUUID(), now, ChatHarness.SEOUL).next)
    }

    @Test fun r1_correctSameDrugBatchIsSaved() {
        val input = "오늘 10:00 프로기노바 2mg 복용, 오늘 20:00 프로기노바 4mg 복용"
        val correct = ChatCommand("batch", doses = listOf(dose("프로기노바", "2", "오늘", "10:00"), dose("프로기노바", "4", "오늘", "20:00")))
        val next = ChatCommands.apply(AppState(), correct, listOf(input), UUID.randomUUID(), now, ChatHarness.SEOUL).next
        assertNotNull(next)
        assertEquals(2, next!!.doses.size)
    }

    // R4: two different drugs at the same clock time; each record must bind to its own clause.
    @Test fun r4_sharedClockTimeWithDifferentDrugsIsSaved() {
        val input = "오늘 10:00 프로기노바 2mg 복용, 오늘 10:00 안쿨 25mg 복용"
        val correct = ChatCommand("batch", doses = listOf(dose("프로기노바", "2", "오늘", "10:00"), dose("안쿨", "25", "오늘", "10:00")))
        val next = ChatCommands.apply(AppState(), correct, listOf(input), UUID.randomUUID(), now, ChatHarness.SEOUL).next
        assertNotNull(next)
        assertEquals(2, next!!.doses.size)
    }

    // R2: the unit follow-up must not move the time of the relative word in the first message.
    @Test fun r2_unitFollowupDoesNotMoveTheTimeOfTheFirstMessage() {
        val h = ChatHarness().send("방금 프로기노바 2 복용").send("mg", afterSeconds = 600)
        assertEquals(listOf("2026-10-24 23:00"), h.doseTimes())
        assertEquals(2.0, h.doses.single().amountEntered, 0.0)
    }

    // R3: a memo save that fails is retried once; the retry must not add a second memo, and undo must remove it.
    @Test fun r3_memoRetryAfterFailureSavesOnceAndUndoes() {
        val h = ChatHarness()
        h.failNextSaves = 1
        h.chat.editInput("합성 메모")
        h.chat.saveMemo()
        assertTrue(h.state.journal.size <= 1)
        assertEquals("합성 메모", h.chat.ui.input)
        h.chat.saveMemo()
        assertEquals(1, h.state.journal.size)
        assertEquals("합성 메모", h.state.journal.single().note)
        assertTrue(h.chat.ui.canUndo)
        h.chat.undo()
        assertTrue(h.state.journal.isEmpty())
    }

    // R5: a symptom-mixed sentence is refused by the validator; the raw text must stay in the composer for a memo.
    @Test fun r5_refusedSymptomSentenceStaysInTheComposerForAMemo() {
        val raw = "방금 프로기노바 2mg 먹었고 어지러웠어"
        val h = ChatHarness()
        h.chat.editInput(raw)
        h.chat.submit()
        assertTrue(h.doses.isEmpty())
        assertEquals(raw, h.chat.ui.input)
        h.chat.saveMemo()
        assertEquals(raw, h.state.journal.single().note)
        assertTrue(h.doses.isEmpty())
    }

    // R6: no silent cut. Over-length text is kept in the draft and refused for save, not shortened.
    @Test fun r6_overLengthComposerTextIsKeptNotCut() {
        val long = "가".repeat(8001)
        val h = ChatHarness()
        h.chat.editInput(long)
        assertEquals(long, h.chat.ui.input)
        h.chat.submit()
        assertEquals(long, h.chat.ui.input)
        assertTrue(h.state.journal.isEmpty())
        assertTrue(h.doses.isEmpty())
    }

    @Test fun r6_memoAtTheLimitIsSavedWhole() {
        val limit = "가".repeat(8000)
        val h = ChatHarness()
        h.chat.editInput(limit)
        h.chat.saveMemo()
        assertEquals(limit, h.state.journal.single().note)
    }

    @Test fun r6_memoOverTheLimitIsRefusedNotCut() {
        val h = ChatHarness()
        h.chat.editInput("가".repeat(8001))
        h.chat.saveMemo()
        assertTrue(h.state.journal.isEmpty())
        assertEquals(8001, h.chat.ui.input.length)
    }
}
