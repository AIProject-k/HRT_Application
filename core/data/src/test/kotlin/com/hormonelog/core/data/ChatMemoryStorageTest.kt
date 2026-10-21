package com.hormonelog.core.data

import com.hormonelog.core.domain.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatMemoryStorageTest {
    @get:Rule val temp = TemporaryFolder()
    private val alias = ChatMemory.Alias("내프로", Drug.PROGESTERONE_MICRONIZED, P4Product.UTROGESTAN_SOFT_CAPSULE)
    private val shortcut = ChatMemory.Shortcut("밤약", listOf(ChatMemory.Dose(Drug.PROGESTERONE_MICRONIZED, Route.ORAL, 100.0, P4Product.UTROGESTAN_SOFT_CAPSULE)))
    @Test fun remembersAcrossFileReloadWithoutCreatingDoses() {
        val file = temp.newFile("records.json")
        RecordStore(file).save(RecordSnapshot(chatMemories = listOf(alias, shortcut)))
        val restored = (RecordStore(file).load() as RecordStore.Load.Ok).snapshot
        assertEquals(listOf(alias, shortcut), restored.chatMemories)
        assertTrue(restored.doses.isEmpty())
    }
    @Test fun fullBackupIncludesUserMemories() {
        val snapshot = RecordSnapshot(chatMemories = listOf(alias, shortcut))
        val restored = BackupCodec.open(BackupCodec.create(snapshot, AppSettings(), 0)) as BackupCodec.Opened.Ok
        assertEquals(snapshot.chatMemories, restored.snapshot.chatMemories)
    }
    @Test fun olderRecordsStartWithNoMemories() {
        assertTrue(RecordJson.fromJson(JSONObject("{\"version\":3,\"doses\":[],\"labs\":[]}")).chatMemories.isEmpty())
    }
    @Test fun unknownMemoryIsCarriedWithoutExecutingOrDroppingIt() {
        val root = RecordJson.toJson(RecordSnapshot(chatMemories = listOf(alias)))
        root.getJSONArray("chatMemories").put(JSONObject("{\"type\":\"future\",\"name\":\"keep\"}"))
        val restored = RecordJson.fromJson(root)
        assertEquals(listOf(alias), restored.chatMemories)
        assertEquals(1, restored.carriedCount)
        assertEquals(2, RecordJson.toJson(restored).getJSONArray("chatMemories").length())
    }
    @Test fun invalidImportedDoseIsQuarantinedAsOneMemory() {
        val bad = JSONObject().put("type", "shortcut").put("name", "bad").put("doses", JSONArray().put(
            JSONObject().put("drug", "PROGESTERONE_MICRONIZED").put("route", "IM_INJECTION").put("amountMg", 100)))
        val restored = RecordJson.fromJson(JSONObject().put("chatMemories", JSONArray().put(bad)))
        assertTrue(restored.chatMemories.isEmpty())
        assertEquals(1, restored.carriedCount)
    }
}
