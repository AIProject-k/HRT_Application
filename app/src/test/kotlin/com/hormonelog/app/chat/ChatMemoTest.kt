package com.hormonelog.app.chat

import com.hormonelog.app.state.JournalDraft
import com.hormonelog.app.state.JournalOps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ChatMemoTest {
    @Test fun memoIsSavedWithoutModelAndWithoutScore() {
        val h = ChatHarness()
        h.chat.editInput("머리가 아프고 피곤했어. 다음 진료 때 얘기할 것")
        h.chat.saveMemo()
        val entry = h.state.journal.single()
        assertEquals("머리가 아프고 피곤했어. 다음 진료 때 얘기할 것", entry.note)
        assertNull(entry.condition)
        assertTrue(entry.symptoms.isEmpty())
        assertTrue(h.doses.isEmpty())
        assertEquals(0, h.modelCalls)
        assertEquals("", h.chat.ui.input)
    }

    @Test fun memoSurvivesANewChat() {
        val h = ChatHarness()
        h.chat.editInput("메모 남김")
        h.chat.saveMemo()
        h.chat.reset()
        assertEquals("메모 남김", h.state.journal.single().note)
    }

    @Test fun longMemoIsKeptWhole() {
        val long = "어지럼이 자주 있었어요. ".repeat(60).trim()
        val h = ChatHarness()
        h.chat.editInput(long)
        h.chat.saveMemo()
        assertEquals(long, h.state.journal.single().note)
    }

    @Test fun reEditingANoteOnlyEntryAddsNoScore() {
        val h = ChatHarness()
        h.chat.editInput("메모")
        h.chat.saveMemo()
        val id = h.state.journal.single().id
        val saved = JournalOps.save(JournalOps.edit(h.state, id), Instant.parse("2026-10-24T14:00:00Z"), ChatHarness.SEOUL)
        assertNull(saved.journal.single().condition)
    }

    @Test fun emptyConditionFormCannotBeSaved() {
        assertFalse(JournalDraft().canSave)
    }
}
