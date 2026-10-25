package com.hormonelog.app.eval

import com.hormonelog.app.chat.ChatCommand
import com.hormonelog.app.chat.ChatLabInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deliberately wrong outputs must fail the scorer. Correct outputs must pass. */
class EvalScorerSelfTest {
    private fun case(id: String) = EVAL_CASES.first { it.id == id }

    private fun dose(drug: String, amount: String, time: String, date: String = "오늘") =
        ChatCommand("dose", drug = drug, amount = amount, amountUnit = "mg", date = date, time = time)

    @Test fun correctModelOutputPasses() {
        val out = EvalScorer.score(case("explicit-date"), dose("프로기노바", "2", "10:00", date = "2026-10-24"), "model")
        assertTrue(out.toString(), out.pass)
        assertEquals(1, out.itemsMatched)
    }

    @Test fun sameCountButSwappedValuesFail() {
        val swapped = ChatCommand("batch", doses = listOf(dose("프로기노바", "4", "10:00"), dose("프로기노바", "2", "20:00")))
        val out = EvalScorer.score(case("same-drug-two"), swapped, "model")
        assertFalse(out.pass)
        assertEquals(0, out.itemsMatched)
    }

    @Test fun duplicateItemIsNotMatchedTwice() {
        val twice = ChatCommand("batch", doses = listOf(dose("프로기노바", "2", "10:00"), dose("프로기노바", "2", "10:00")))
        val out = EvalScorer.score(case("same-drug-two"), twice, "model")
        assertEquals(1, out.itemsMatched)
        assertFalse(out.pass)
    }

    @Test fun parseFailureStaysInTheDenominator() {
        val out = EvalScorer.forced(case("batch-two-correct"), "not json at all")
        assertTrue(out.parseFail)
        assertEquals(2, out.itemsExpected)
        assertEquals(2, out.missedSaved)
        assertFalse(out.pass)
    }

    @Test fun sameTimeOnAnotherDateDoesNotMatch() {
        val out = EvalScorer.score(case("explicit-date"), dose("프로기노바", "2", "10:00", date = "2026-10-23"), "model")
        assertEquals(0, out.itemsMatched)
        assertFalse(out.pass)
    }

    @Test fun wrongLabUnitFails() {
        val wrong = ChatCommand("lab", date = "오늘", time = "10:00", labs = listOf(ChatLabInput("E2", "100", "ng/mL")))
        val out = EvalScorer.score(case("lab-e2"), wrong, "model")
        assertFalse(out.pass)
        assertEquals(1, out.missedSaved)
    }

    @Test fun correctRefusalPasses() {
        val out = EvalScorer.score(case("negation"), dose("프로기노바", "2", "23:00", date = "방금"), "model")
        assertTrue(out.toString(), out.pass)
        assertEquals(0, out.wrongSaved)
    }
}
