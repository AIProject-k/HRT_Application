package com.hormonelog.app.chat

import com.hormonelog.app.state.*
import com.hormonelog.core.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class ChatMemoryActionsTest {
    private val now = Instant.parse("2026-10-19T10:00:00Z")
    private val zone = ZoneId.of("Asia/Seoul")
    private val id = UUID.randomUUID()
    private val remember = ChatCommand("remember_doses", memoryName = "밤약", doses = listOf(
        ChatCommand("dose", drug = "유트로게스탄", route = "복용", amount = "100mg")))
    private val instruction = "밤약은 유트로게스탄 100mg 복용으로 기억해줘"
    private fun run(c: ChatCommand, text: String, s: AppState = AppState()) = ChatMemoryActions.apply(s, c, listOf(text), id, now, zone)

    @Test fun teachingShortcutCreatesMemoryNotAnAdministration() {
        val r = run(remember, instruction)
        assertNotNull(r.message, r.next)
        assertTrue(r.next!!.doses.isEmpty())
        assertEquals("밤약", r.next.chatMemories.single().name)
        assertTrue(r.message, r.message.contains("기억"))
    }
    @Test fun shortcutAloneLogsNowAndPreservesKnownProduct() {
        val saved = run(remember, instruction).next!!
        val r = run(ChatCommand("shortcut", memoryName = "밤약"), "밤약", saved)
        val e = r.next!!.doses.single()
        assertEquals(now, e.occurredAt)
        assertEquals(100.0, e.amountEntered, 0.0)
        assertEquals(P4Product.UTROGESTAN_SOFT_CAPSULE, e.p4Context!!.product)
        assertEquals(P4Meal.UNKNOWN, e.p4Context!!.meal)
        assertNull(run(ChatCommand("shortcut", memoryName = "밤약"), "밤약", r.next).next)
    }
    @Test fun shortcutCanUseAnExplicitPastTime() {
        val saved = run(remember, instruction).next!!
        val r = run(ChatCommand("shortcut", memoryName = "밤약", date = "어제", time = "오후 3시"), "어제 오후 3시 밤약", saved)
        assertEquals(Instant.parse("2026-10-18T06:00:00Z"), r.next!!.doses.single().occurredAt)
    }
    @Test fun missingOrInventedDoseCannotBeMemorized() {
        assertNull(run(remember, "밤약은 유트로게스탄으로 기억해줘").next)
        assertNull(run(remember.copy(memoryName = "딴이름"), instruction).next)
    }
    @Test fun ordinaryTakingDoesNotSilentlyTeachAMemory() {
        assertNull(run(remember, "밤약 유트로게스탄 100mg 방금 복용").next)
    }
    @Test fun aliasKeepsProductAndWorksInNormalDoseValidation() {
        val r = run(ChatCommand("remember_alias", memoryName = "내프로", memoryTarget = "유트로게스탄"), "내프로는 유트로게스탄이라고 기억해줘")
        val s = r.next!!
        assertTrue(s.doses.isEmpty())
        val dose = ChatCommands.apply(s, ChatCommand("dose", drug = "내프로", amount = "100mg", time = "방금"), listOf("내프로 100mg 방금 복용"), id, now, zone)
        assertEquals(P4Product.UTROGESTAN_SOFT_CAPSULE, dose.next!!.doses.single().p4Context!!.product)
    }
    @Test fun duplicateNameNeedsExplicitUpdateAndForgetIsSeparateFromDoseDeletion() {
        val s = run(remember, instruction).next!!
        val changed = remember.copy(doses = listOf(ChatCommand("dose", drug = "유트로게스탄", amount = "200mg", route = "복용")))
        assertNull(run(changed, instruction.replace("100mg", "200mg"), s).next)
        val updated = run(changed.copy(action = "update_doses"), "밤약을 유트로게스탄 200mg 복용으로 바꿔줘", s).next!!
        val forgotten = run(ChatCommand("memory_forget", memoryName = "밤약"), "밤약 기억 지워줘", updated).next!!
        assertTrue(forgotten.chatMemories.isEmpty())
        assertTrue(forgotten.doses.isEmpty())
    }
    @Test fun questionsAndMemoryControlCannotExecuteShortcut() {
        val s = run(remember, instruction).next!!
        val c = ChatCommand("shortcut", memoryName = "밤약")
        assertNull(run(c, "밤약은 뭐야?", s).next)
        assertNull(run(c, "밤약 삭제해줘", s).next)
        assertNull(run(c, "밤약 먹어도돼?", s).next)
        assertNull(run(c, "밤약 기억하지마", s).next)
        assertNull(run(c, "밤약 먹을까?", s).next)
        assertNull(run(c, "밤약?", s).next)
        assertNull(run(c, "어제 밤약 먹었어", s).next)
    }
    @Test fun memoryInstructionsCannotAccidentallyBecomeActualDoses() {
        val c = ChatCommand("dose", drug = "유트로게스탄", amount = "100mg", route = "복용", time = "방금")
        assertNull(ChatCommands.apply(AppState(), c, listOf("유트로게스탄 100mg 방금 복용을 밤약이라고 기억해줘"), id, now, zone).next)
    }
    @Test fun deleteAllAndUndoIncludeMemories() {
        val saved = run(remember, instruction).next!!
        val cleared = RecordsReducer.clearAll(saved)
        assertTrue(cleared.chatMemories.isEmpty())
        assertEquals(saved.chatMemories, RecordsReducer.undo(cleared).chatMemories)
    }
    @Test fun cannotRedefineBuiltInDrugNamesOrForgetAgainstInstruction() {
        assertNull(run(ChatCommand("remember_alias", memoryName = "EV", memoryTarget = "안쿨"), "EV는 안쿨이라고 기억해줘").next)
        val s = run(remember, instruction).next!!
        assertNull(run(ChatCommand("memory_forget", memoryName = "밤약"), "밤약 기억은 지우지마", s).next)
    }
    @Test fun singleShortcutCanChangeOnlyItsAmountWithoutRepeatingProduct() {
        val s = run(remember, instruction).next!!
        val c = ChatCommand("update_doses", memoryName = "밤약", doses = listOf(ChatCommand("dose", amount = "200mg")))
        val r = run(c, "밤약 용량을 200mg로 바꿔줘", s)
        assertNotNull(r.message, r.next)
        assertEquals(200.0, (r.next!!.chatMemories.single() as ChatMemory.Shortcut).doses.single().amountMg, 0.0)
        assertTrue(r.next.doses.isEmpty())
    }
    @Test fun changingOneMedicineKeepsTheRestOfTheShortcut() {
        val c = remember.copy(doses = remember.doses + ChatCommand("dose", drug = "안쿨", amount = "25mg"))
        val s = run(c, "밤약은 유트로게스탄 100mg 복용과 안쿨 25mg으로 기억해줘").next!!
        val change = ChatCommand("update_doses", memoryName = "밤약", doses = listOf(ChatCommand("dose", drug = "안쿨", amount = "12.5mg")))
        val r = run(change, "밤약 안쿨 용량을 12.5mg로 바꿔줘", s)
        assertNotNull(r.message, r.next)
        val doses = (r.next!!.chatMemories.single() as ChatMemory.Shortcut).doses
        assertEquals(2, doses.size)
        assertEquals(100.0, doses.first { it.drug == Drug.PROGESTERONE_MICRONIZED }.amountMg, 0.0)
        assertEquals(12.5, doses.first { it.drug == Drug.CYPROTERONE }.amountMg, 0.0)
    }
    @Test fun modelMayPutSingleAmountEditInRootFields() {
        val s = run(remember, instruction).next!!
        val c = ChatCommand("update_doses", memoryName = "밤약", amount = "200mg")
        val r = run(c, "밤약 용량을 200mg로 바꿔줘", s)
        assertNotNull(r.message, r.next)
        assertEquals(200.0, (r.next!!.chatMemories.single() as ChatMemory.Shortcut).doses.single().amountMg, 0.0)
    }
    @Test fun learnedNamesMustNotMatchInsideOtherWords() {
        val aliases = AppState(chatMemories = listOf(ChatMemory.Alias("프로", Drug.PROGESTERONE_MICRONIZED, P4Product.UTROGESTAN_SOFT_CAPSULE)))
        val c = ChatCommand("dose", drug = "프로", amount = "100mg", time = "방금")
        assertNull(ChatCommands.apply(aliases, c, listOf("프로기노바 100mg 방금 복용"), id, now, zone).next)
        assertNotNull(ChatCommands.apply(aliases, c, listOf("방금프로100mg먹었어"), id, now, zone).next)
        val s = run(remember, instruction).next!!
        assertNull(run(ChatCommand("shortcut", memoryName = "밤약"), "밤약상자", s).next)
    }
}
