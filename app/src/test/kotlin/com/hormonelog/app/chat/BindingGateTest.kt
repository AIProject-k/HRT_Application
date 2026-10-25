package com.hormonelog.app.chat

import com.hormonelog.core.domain.Drug
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Model replies that bind the wrong amount, time or item to a drug must never reach storage.
 * The rule path returns nothing for these inputs (two drugs in one message), so the stub model is the one that answers.
 */
class BindingGateTest {
    private val twoDrugsComma = "오늘 10:00 프로기노바 2mg 복용, 오늘 20:00 안쿨 25mg 복용"
    private val twoDrugsNoComma = "오늘 10:00 프로기노바 2mg 안쿨 25mg 20:00 복용"

    private fun answering(vararg doses: ChatCommand) =
        ChatHarness(model = { ChatHarness.structured(ChatHarness.batch("오늘", *doses)) })

    private fun correct() = listOf(ChatHarness.dose("프로기노바", "2mg", "10:00"), ChatHarness.dose("안쿨", "25mg", "20:00"))

    @Test fun correctTwoDrugBatchSavesEveryFieldOnce() {
        val h = answering(*correct().toTypedArray())
        h.send(twoDrugsComma)
        assertEquals(setOf("프로기노바|2.0|2026-10-24 10:00", "안쿨|25.0|2026-10-24 20:00"), records(h))
        assertEquals(2, h.doses.size)
    }

    @Test fun swappedAmountsNeverSave() {
        val h = answering(ChatHarness.dose("프로기노바", "25mg", "10:00"), ChatHarness.dose("안쿨", "2mg", "20:00"))
        h.send(twoDrugsComma)
        assertTrue("saved swapped amounts: ${records(h)}", h.doses.isEmpty())
    }

    @Test fun swappedTimesNeverSave() {
        val h = answering(ChatHarness.dose("프로기노바", "2mg", "20:00"), ChatHarness.dose("안쿨", "25mg", "10:00"))
        h.send(twoDrugsComma)
        assertTrue("saved swapped times: ${records(h)}", h.doses.isEmpty())
    }

    @Test fun droppedItemNeverSavesThePartialBatch() {
        val h = answering(ChatHarness.dose("프로기노바", "2mg", "10:00"))
        h.send(twoDrugsComma)
        assertTrue("saved partial batch: ${records(h)}", h.doses.isEmpty())
    }

    @Test fun duplicatedItemNeverSaves() {
        val h = answering(ChatHarness.dose("프로기노바", "2mg", "10:00"), ChatHarness.dose("프로기노바", "2mg", "10:00"))
        h.send(twoDrugsComma)
        assertTrue("saved duplicate: ${records(h)}", h.doses.isEmpty())
    }

    @Test fun sameDrugSwappedAmountsNeverSave() {
        val h = answering(ChatHarness.dose("프로기노바", "4mg", "10:00"), ChatHarness.dose("프로기노바", "2mg", "20:00"))
        h.send("오늘 10:00 프로기노바 2mg 복용, 오늘 20:00 프로기노바 4mg 복용")
        assertTrue("saved swapped same-drug amounts: ${records(h)}", h.doses.isEmpty())
    }

    @Test fun ambiguousNoCommaLinkNeverAutoSaves() {
        val h = answering(*correct().toTypedArray())
        h.send(twoDrugsNoComma)
        assertTrue("auto-confirmed an ambiguous link: ${records(h)}", h.doses.isEmpty())
    }

    @Test fun failedBatchSaveWritesNothingToDisk() {
        val h = answering(*correct().toTypedArray())
        h.failNextSaves = 1
        h.send(twoDrugsComma)
        assertTrue("disk changed after failed save: ${records(h)}", h.disk.doses.isEmpty())
    }

    private fun records(h: ChatHarness): Set<String> =
        h.doses.map { "${label(it.drug)}|${it.amountEntered}|${ChatHarness.kst(it.occurredAt)}" }.toSet()

    private fun label(drug: Drug): String = if (drug == Drug.ESTRADIOL_TABLET) "프로기노바" else "안쿨"
}
