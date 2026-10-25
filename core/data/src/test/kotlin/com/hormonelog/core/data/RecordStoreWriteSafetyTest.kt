package com.hormonelog.core.data

import com.hormonelog.core.domain.JournalEntry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.time.Instant
import java.util.UUID

/** Each failure is injected at one step of save: temp write start, partial write, bad bytes on disk, and the replace. */
class RecordStoreWriteSafetyTest {
    @get:Rule val folder = TemporaryFolder()

    private fun snapshot(note: String) = RecordSnapshot(
        journal = listOf(JournalEntry(id = UUID.nameUUIDFromBytes(note.toByteArray()), at = Instant.parse("2026-10-24T14:00:00Z"), sourceZoneId = "Asia/Seoul", note = note)),
    )

    private fun tmp() = File(folder.root, "records.json.tmp")

    private fun noteOf(file: File) = RecordJson.fromJson(org.json.JSONObject(file.readText())).journal.single().note

    @Test fun saveReplacesTheFileAndLeavesNoTempFile() {
        val file = folder.newFile("records.json")
        val store = RecordStore(file)
        store.save(snapshot("first"))
        store.save(snapshot("second"))
        assertTrue(noteOf(file) == "second")
        assertFalse(tmp().exists())
    }

    @Test fun temp_writeCannotStart_originalUntouched() {
        val file = folder.newFile("records.json")
        val store = RecordStore(file)
        store.save(snapshot("first"))
        val before = file.readBytes()
        tmp().mkdirs()
        assertThrows(IOException::class.java) { store.save(snapshot("second")) }
        assertArrayEquals(before, file.readBytes())
    }

    @Test fun temp_partialWriteThenFailure_originalUntouchedAndTempRemoved() {
        val file = folder.newFile("records.json")
        val partial = RecordStore(file, writeTemp = { target, bytes ->
            FileOutputStream(target).use { it.write(bytes, 0, bytes.size / 2) }
            throw IOException("synthetic: disk full after half the bytes")
        })
        RecordStore(file).save(snapshot("first"))
        val before = file.readBytes()
        assertThrows(IOException::class.java) { partial.save(snapshot("second")) }
        assertArrayEquals(before, file.readBytes())
        assertFalse(tmp().exists())
    }

    @Test fun verify_wrongBytesOnDisk_areRejectedBeforeReplace() {
        val file = folder.newFile("records.json")
        RecordStore(file).save(snapshot("first"))
        val before = file.readBytes()
        val corrupting = RecordStore(file, writeTemp = { target, _ -> target.writeText("{\"corrupt\":true}") })
        assertThrows(IOException::class.java) { corrupting.save(snapshot("second")) }
        assertArrayEquals(before, file.readBytes())
        assertFalse(tmp().exists())
    }

    @Test fun replace_refused_originalUntouchedAndTempRemoved() {
        val file = folder.newFile("records.json")
        RecordStore(file).save(snapshot("first"))
        val before = file.readBytes()
        val refusing = RecordStore(file, replace = { _, _ -> throw IOException("synthetic: rename refused") })
        assertThrows(IOException::class.java) { refusing.save(snapshot("second")) }
        assertArrayEquals(before, file.readBytes())
        assertFalse(tmp().exists())
    }
}
