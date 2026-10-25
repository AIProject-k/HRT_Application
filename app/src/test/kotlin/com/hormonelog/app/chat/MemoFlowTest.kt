package com.hormonelog.app.chat

import com.hormonelog.app.state.JournalDraft
import com.hormonelog.app.state.JournalOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class MemoFlowTest {
    private val now = Instant.parse("2026-10-24T14:00:00Z")

    @Test fun failedModelRequestKeepsTheRawTextForAMemoWithoutRetrying() {
        val raw = "오늘 머리가 아프고 피곤했어. 다음 진료 때 얘기할 것"
        val h = ChatHarness()
        h.send(raw)
        assertEquals(1, h.modelCalls)
        assertEquals(raw, h.chat.ui.input)
        h.chat.saveMemo()
        assertEquals(raw, h.state.journal.single().note)
        assertEquals(1, h.modelCalls)
        assertTrue(h.doses.isEmpty())
    }

    @Test fun longMemoEditKeepsEveryCharacter() {
        val long = "어지럼이 있었어요. ".repeat(70).trim()
        val h = ChatHarness()
        h.chat.editInput(long)
        h.chat.saveMemo()
        val id = h.state.journal.single().id
        val edited = JournalOps.draft(JournalOps.edit(h.state, id)) { it.copy(note = it.note + " 수정") }
        val saved = JournalOps.save(edited, now, ChatHarness.SEOUL)
        assertEquals(long + " 수정", saved.journal.single().note)
    }

    @Test fun noteOverTheLimitIsRefusedNotCut() {
        assertTrue(JournalDraft(note = "가".repeat(JournalDraft.NOTE_LIMIT)).canSave)
        val over = JournalDraft(note = "가".repeat(JournalDraft.NOTE_LIMIT + 1))
        assertNotNull(over.noteError)
        assertFalse(over.canSave)
    }
}
