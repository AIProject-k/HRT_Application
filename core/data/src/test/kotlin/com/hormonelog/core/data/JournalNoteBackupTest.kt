package com.hormonelog.core.data

import com.hormonelog.core.domain.AppSettings
import com.hormonelog.core.domain.JournalEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.UUID

class JournalNoteBackupTest {
    @Test fun nearLimitNoteSurvivesBackupRoundTripAsANoteOnlyEntry() {
        val note = "가나다라 메모 ".repeat(1000).trim()
        val entry = JournalEntry(id = UUID.randomUUID(), at = Instant.parse("2026-10-24T14:00:00Z"), sourceZoneId = "Asia/Seoul", note = note)
        val bytes = BackupCodec.create(RecordSnapshot(journal = listOf(entry)), AppSettings(), 1_000L)
        val restored = (BackupCodec.open(bytes) as BackupCodec.Opened.Ok).snapshot.journal.single()
        assertEquals(note, restored.note)
        assertNull(restored.condition)
        assertTrue(restored.symptoms.isEmpty())
        assertEquals(JournalEntry.Kind.CONDITION, restored.kind)
    }
}
