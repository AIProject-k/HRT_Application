package com.hormonelog.app.chat

import com.hormonelog.app.state.AppState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.time.Instant

class ChatRebuildTest {
    private val now = Instant.parse("2026-10-20T10:00:00Z")
    private var state = AppState()
    private var callback: ((Result<String>) -> Unit)? = null
    private var payload = ""
    private fun session(store: ChatConversationStore? = null) = ChatSession({ state },
        { next, done -> state = next; done(true) },
        { _, data, done -> payload = data; callback = done }, {}, clock = { now }, conversationStore = store)
    private fun reply(raw: String) = requireNotNull(callback)(Result.success("""{"structured_output":$raw}"""))
    private fun firstDose(chat: ChatSession) {
        chat.connect(); chat.send("유트로게스탄 100mg 방금 복용")
        reply("""{"action":"dose","drug":"유트로게스탄","amount":"100mg","time":"방금"}""")
        assertEquals(1, state.doses.size)
    }
    @Test fun interruptedQuestionAndDraftSurviveFileReloadWithoutAutoExecution() {
        val file = Files.createTempDirectory("chat-rebuild").resolve("chat.json").toFile()
        try {
            val chat = session(ChatConversationStore(file))
            chat.connect(); chat.send("유트로게스탄 먹었어")
            reply("""{"action":"dose","drug":"유트로게스탄"}""")
            chat.editInput("100mg")
            chat.flush()
            val restored = session(ChatConversationStore(file))
            assertEquals("100mg", restored.ui.input)
            assertEquals(chat.ui.lines, restored.ui.lines)
            assertFalse(restored.ui.busy)
            assertTrue(state.doses.isEmpty())
            restored.connect(); restored.submit()
            assertTrue(payload.contains("유트로게스탄 먹었어"))
            reply("""{"action":"dose","drug":"유트로게스탄","amount":"100mg"}""")
            restored.send("어제 밤 9시")
            reply("""{"action":"dose","drug":"유트로게스탄","amount":"100mg","date":"어제","time":"밤 9시"}""")
            assertEquals(1, state.doses.size)
            assertTrue(restored.ui.lines.last().text.startsWith("저장 완료"))
        } finally { file.parentFile!!.deleteRecursively() }
    }
    @Test fun localQueryAndDisconnectKeepPendingRecordButDontSendQueriedValues() {
        val chat = session()
        chat.connect(); chat.send("유트로게스탄 먹었어")
        reply("""{"action":"dose","drug":"유트로게스탄"}""")
        chat.showStatus(); chat.disconnect(); chat.connect(); chat.send("100mg 방금")
        assertTrue(JSONObject(payload).getJSONArray("messages").toString().contains("유트로게스탄 먹었어"))
        reply("""{"action":"dose","drug":"유트로게스탄","amount":"100mg","time":"방금"}""")
        assertEquals(1, state.doses.size)
    }
    @Test fun explicitSameDrugFollowupUsesLocalReferenceAndAsksOnlyForNewTime() {
        val chat = session(); firstDose(chat)
        chat.send("같은 약 어제도 먹었어")
        reply("""{"action":"dose","date":"어제"}""")
        assertEquals(1, state.doses.size)
        assertFalse(chat.ui.lines.last().text.contains("제품"))
        chat.send("밤 9시")
        reply("""{"action":"dose","date":"어제","time":"밤 9시"}""")
        assertEquals(2, state.doses.size)
        assertEquals(Instant.parse("2026-10-19T12:00:00Z"), state.doses.minOf { it.occurredAt })
        assertTrue(state.doses.all { it.amountEntered == 100.0 })
    }
    @Test fun thanksOrNegatedReferenceCannotReplayCompletedRecord() {
        val chat = session(); firstDose(chat)
        for (text in listOf("고마워", "같은 약 어제는 안 먹었어")) {
            chat.send(text)
            reply("""{"action":"dose","drug":"유트로게스탄","amount":"100mg","date":"어제","time":"오후 9시"}""")
            assertEquals(1, state.doses.size)
        }
    }
    @Test fun interruptedSaveIsNeverReplayedAfterRestart() {
        val file = Files.createTempDirectory("chat-write").resolve("chat.json").toFile()
        try {
            val store = ChatConversationStore(file)
            val chat = ChatSession({ state }, { next, _ -> state = next }, { _, _, done -> callback = done }, {},
                clock = { now }, conversationStore = store)
            firstDose(chat)
            assertTrue(chat.ui.saving)
            val restored = session(ChatConversationStore(file))
            restored.connect(); restored.send("고마워")
            assertFalse(JSONObject(payload).getJSONArray("messages").toString().contains("방금 복용"))
            reply("""{"action":"dose","drug":"유트로게스탄","amount":"100mg","time":"방금"}""")
            assertEquals(1, state.doses.size)
        } finally { file.parentFile!!.deleteRecursively() }
    }
    @Test fun newConversationClearsPersistedDraftAndReference() {
        val file = Files.createTempDirectory("chat-reset").resolve("chat.json").toFile()
        try {
            val store = ChatConversationStore(file)
            val chat = session(store); firstDose(chat); chat.editInput("남은 문장"); chat.reset()
            val restored = session(ChatConversationStore(file))
            assertTrue(restored.ui.lines.isEmpty()); assertEquals("", restored.ui.input)
            restored.connect(); restored.send("같은 약 어제도 먹었어")
            reply("""{"action":"dose","date":"어제","time":"밤 9시"}""")
            assertEquals(1, state.doses.size)
        } finally { file.parentFile!!.deleteRecursively() }
    }
}
