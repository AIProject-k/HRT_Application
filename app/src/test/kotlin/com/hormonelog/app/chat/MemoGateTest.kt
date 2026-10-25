package com.hormonelog.app.chat

import com.hormonelog.core.data.BackupCodec
import com.hormonelog.core.data.RecordSnapshot
import com.hormonelog.core.domain.AppSettings
import com.hormonelog.app.state.JournalDraft
import com.hormonelog.app.state.JournalOps
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A long memo typed without any model is kept whole through save, edit, backup and restore. */
class MemoGateTest {
    private val now = Instant.parse("2026-10-24T14:00:00Z")
    private val longNote = "진료 때 물어볼 것 메모입니다. ".repeat(100).trim()

    @Test fun longMemoSurvivesSaveEditBackupAndRestore() {
        assertTrue(longNote.length >= 1500)
        val h = ChatHarness()
        h.chat.editInput(longNote)
        h.chat.saveMemo()
        val id = h.state.journal.single().id

        val edited = JournalOps.draft(JournalOps.edit(h.state, id)) { it.copy(note = longNote + " 수정 끝") }
        val saved = JournalOps.save(edited, now, ChatHarness.SEOUL)
        assertEquals(longNote + " 수정 끝", saved.journal.single().note)

        val bytes = BackupCodec.create(RecordSnapshot(journal = saved.journal), AppSettings(), 1_000L)
        val restored = (BackupCodec.open(bytes) as BackupCodec.Opened.Ok).snapshot.journal.single()
        assertEquals(longNote + " 수정 끝", restored.note)
    }

    @Test fun noteAtTheLimitIsAcceptedWholeAndOneMoreIsRefusedNotCut() {
        val atLimit = "가".repeat(JournalDraft.NOTE_LIMIT)
        assertTrue(JournalDraft(note = atLimit).canSave)
        val over = "가".repeat(JournalDraft.NOTE_LIMIT + 1)
        assertTrue(!JournalDraft(note = over).canSave)
        assertEquals(over, JournalDraft(note = over).note)
    }
}
