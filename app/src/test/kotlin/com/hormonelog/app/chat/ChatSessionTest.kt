package com.hormonelog.app.chat

import com.hormonelog.app.state.AppState
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.time.Instant

class ChatSessionTest {
    @Test fun localStatusButtonsReadFreshStateWithoutConnectionProviderOrWrites() {
        var current = AppState()
        val chat = ChatSession({ current }, { _, _ -> error("read must not save") },
            { _, _, _ -> error("local query must not call Claude") }, {})
        chat.showStatus()
        assertTrue(chat.ui.lines.last().text.startsWith("현재 상태"))
        assertFalse(chat.ui.connected)
        val lab = com.hormonelog.core.domain.LabResult(UUID.randomUUID(), null, null,
            com.hormonelog.core.domain.Assay.UNKNOWN, listOf(com.hormonelog.core.domain.LabAnalyteValue(
                com.hormonelog.core.domain.Analyte.ESTRADIOL, 123.456, "pmol/L", null)))
        current = current.copy(labs = listOf(lab))
        chat.showLabs()
        assertTrue(chat.ui.lines.last().text.contains("123.456 pmol/L"))
        assertEquals(listOf(lab), current.labs)
    }

    @Test fun queriedValuesNeverEnterTheNextModelConversation() {
        val lab = com.hormonelog.core.domain.LabResult(UUID.randomUUID(), null, null,
            com.hormonelog.core.domain.Assay.UNKNOWN, listOf(com.hormonelog.core.domain.LabAnalyteValue(
                com.hormonelog.core.domain.Analyte.ESTRADIOL, 123.456, "pmol/L", null)))
        val current = AppState(labs = listOf(lab))
        var payload = ""
        var reply: ((Result<String>) -> Unit)? = null
        val chat = ChatSession({ current }, { _, _ -> error("read must not save") },
            { _, data, done -> payload = data; reply = done }, {})
        chat.connect()
        for (action in listOf("status", "lab_results")) {
            chat.send("현재 결과 알려줘")
            reply!!(Result.success("""{"structured_output":{"action":"$action"}}"""))
            assertTrue(chat.ui.lines.last().text.contains("123.456 pmol/L"))
            chat.send("고마워")
            assertFalse(payload.contains("123.456"))
            assertEquals(1, org.json.JSONObject(payload).getJSONArray("messages").length())
            reply!!(Result.success("""{"structured_output":{"action":"ask","reply":"도움이 되었어요"}}"""))
            chat.reset()
        }
    }

    @Test fun modelChangesAreBlockedWhileRequestingOrSavingAndPreservedByReset() {
        var current = AppState()
        var reply: ((Result<String>) -> Unit)? = null
        val chat = ChatSession({ current }, { next, done -> current = next; done(false) },
            { _, _, done -> reply = done }, {}, onModelChanged = { model ->
                current = current.copy(settings = current.settings.copy(chatModel = model))
            })
        assertEquals(com.hormonelog.core.domain.ChatModel.HAIKU, chat.model)
        chat.selectModel(com.hormonelog.core.domain.ChatModel.OPUS)
        chat.connect(); chat.send("최근 기록 보여줘")
        chat.selectModel(com.hormonelog.core.domain.ChatModel.SONNET)
        assertEquals(com.hormonelog.core.domain.ChatModel.OPUS, chat.model)
        reply!!(Result.success("""{"structured_output":{"action":"recent"}}"""))
        chat.selectModel(com.hormonelog.core.domain.ChatModel.SONNET)
        chat.reset()
        assertEquals(com.hormonelog.core.domain.ChatModel.SONNET, chat.model)
        chat.connect(); chat.send("2020-01-01 15:30 EV 5mg 근육주사 기록해줘")
        reply!!(Result.success("""{"structured_output":{"action":"dose","drug":"EV","route":"근육주사","amount":"5mg","date":"2020-01-01","time":"15:30"}}"""))
        assertTrue(chat.ui.saveFailed)
        chat.selectModel(com.hormonelog.core.domain.ChatModel.HAIKU)
        assertEquals(com.hormonelog.core.domain.ChatModel.SONNET, chat.model)
    }

    private var state = AppState()
    private var response: ((Result<String>) -> Unit)? = null
    private var ack: ((Boolean) -> Unit)? = null
    private var calls = 0
    private val session = ChatSession({ state }, { next, done -> state = next; ack = done },
        { _, _, done -> calls++; response = done }, {})
    private val input = "2020-01-01 15:30 EV 5 mg 근육주사 기록해줘"
    private val result = """{"structured_output":{"action":"dose","drug":"EV","route":"근육주사","amount":"5 mg","date":"2020-01-01","time":"15:30"}}"""
    private fun receive() { session.connect(); session.send(input); response!!(Result.success(result)) }

    @Test fun successIsShownOnlyAfterDiskAcknowledges() {
        receive()
        assertEquals(1, state.doses.size)
        assertTrue(session.ui.saving)
        assertFalse(session.ui.lines.any { it.text.contains("저장 완료") })
        ack!!(true)
        assertTrue(session.ui.lines.any { it.text.contains("저장 완료") })
    }
    @Test fun writeFailureRetriesStorageWithoutCallingClaudeOrAppendingAgain() {
        receive(); val id = state.doses.single().id
        ack!!(false)
        assertTrue(session.ui.saveFailed)
        session.retrySave(); ack!!(true)
        assertEquals(1, calls)
        assertEquals(id, state.doses.single().id)
        assertFalse(session.ui.saveFailed)
    }
    @Test fun cancellationAndRepeatedCallbacksCannotWrite() {
        session.connect(); session.send(input); val old = response!!
        session.cancel(); old(Result.success(result))
        assertTrue(state.doses.isEmpty())
        receive(); response!!(Result.success(result)); ack!!(true)
        assertEquals(1, state.doses.size)
    }
    @Test fun undoCannotEraseRecordsChangedSinceChatSave() {
        receive(); ack!!(true)
        state = state.copy(doses = state.doses + state.doses.single().copy(id = UUID.randomUUID()))
        session.undo()
        assertEquals(2, state.doses.size)
        assertFalse(session.ui.canUndo)
    }
    @Test fun oldCompletedActionIsNotReplayedWhenUserSaysThanks() {
        receive(); ack!!(true)
        session.send("고마워"); response!!(Result.success(result))
        assertEquals(1, state.doses.size)
    }
    @Test fun nowInClarificationUsesThatMessagesTime() {
        var time = Instant.parse("2026-10-19T00:00:00Z")
        val chat = ChatSession({ state }, { next, done -> state = next; ack = done },
            { _, _, done -> response = done }, {}, clock = { time })
        chat.connect(); chat.send("투약 기록해줘")
        response!!(Result.success("""{"structured_output":{"action":"ask","reply":"투약 내용을 알려주세요"}}"""))
        time = time.plusSeconds(600)
        chat.send("오늘 방금 EV 5 mg 근육주사")
        response!!(Result.success("""{"structured_output":{"action":"dose","drug":"EV","route":"근육주사","amount":"5 mg","date":"오늘","time":"방금"}}"""))
        assertEquals(time, state.doses.single().occurredAt)
    }
    @Test fun alsoAfterSavedDoseKeepsItsTimeWithoutSendingRecordsToClaude() {
        receive(); ack!!(true)
        var payload = ""
        val chat = ChatSession({ state }, { next, done -> state = next; ack = done },
            { _, data, done -> payload = data; response = done }, {})
        // A new conversation must not inherit another session's record.
        chat.connect(); chat.send("안쿨 12.5mg도")
        assertFalse(org.json.JSONObject(payload).getBoolean("previousDoseTimeAvailable"))
        session.send("안쿨 12.5mg도")
        response!!(Result.success("""{"structured_output":{"action":"dose","drug":"안쿨","amount":"12.5mg"}}"""))
        assertEquals(2, state.doses.size)
        assertEquals(1, state.doses.map { it.occurredAt }.distinct().size)
    }
    @Test fun batchUndoRestoresAllNewDosesTogether() {
        session.connect(); session.send("2020-01-01 15:30 EV 5mg 근육주사 맞고 안쿨 12.5mg 먹었어")
        response!!(Result.success("""{"structured_output":{"action":"batch","date":"2020-01-01","time":"15:30","doses":[{"drug":"EV","route":"근육주사","amount":"5mg"},{"drug":"안쿨","amount":"12.5mg"}]}}"""))
        assertEquals(2, state.doses.size); ack!!(true)
        session.undo(); ack!!(true)
        assertTrue(state.doses.isEmpty())
    }
    @Test fun memoryAcknowledgmentAndInvocationDoNotSendDefinitionsBackToModel() {
        var payload = ""
        val chat = ChatSession({ state }, { next, done -> state = next; ack = done },
            { _, data, done -> payload = data; response = done }, {})
        chat.connect(); chat.send("밤약은 유트로게스탄 100mg 복용으로 기억해줘")
        response!!(Result.success("""{"structured_output":{"action":"remember_doses","memoryName":"밤약","doses":[{"drug":"유트로게스탄","amount":"100mg","route":"복용"}]}}"""))
        assertTrue(state.doses.isEmpty())
        assertTrue(chat.ui.saving)
        ack!!(true)
        chat.listMemories()
        chat.send("밤약")
        val names = org.json.JSONObject(payload).getJSONArray("memoryNames")
        assertEquals("shortcut", names.getJSONObject(0).getString("kind"))
        assertFalse(payload.contains("100mg"))
        assertFalse(payload.contains("유트로게스탄"))
        response!!(Result.success("""{"structured_output":{"action":"shortcut","memoryName":"밤약"}}"""))
        assertEquals(1, state.doses.size); ack!!(true)
        chat.undo(); ack!!(true)
        assertTrue(state.doses.isEmpty())
        assertEquals(1, state.chatMemories.size)
    }
}
