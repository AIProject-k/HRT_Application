package com.hormonelog.app.chat

import com.hormonelog.app.state.AppState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class ChatContinuationTest {
    private val now = Instant.parse("2026-10-19T10:00:00Z")
    private val zone = ZoneId.of("Asia/Seoul")
    private fun doses(count: Int) = (0 until count).map { index ->
        ChatCommand("dose", drug = "EV", route = "근육주사", amount = "5mg",
            date = LocalDate.of(2020, 1, 1).plusDays(index.toLong()).toString(), time = "15:30")
    }
    private fun input(items: List<ChatCommand>) = items.joinToString("\n") {
        "${it.date} ${it.time} EV 5mg 근육주사 기록해줘"
    }
    @Test fun sixAndFiftyBackfilledDosesAreDecodedAndSavedAsOneBatch() {
        for (count in listOf(6, 50)) {
            val items = doses(count)
            val raw = JSONObject().put("structured_output", JSONObject().put("action", "batch").put("doses", JSONArray().apply {
                items.forEach { put(JSONObject().put("drug", it.drug).put("route", it.route).put("amount", it.amount).put("date", it.date).put("time", it.time)) }
            })).toString()
            val command = ChatProtocol.decode(raw)
            val result = ChatCommands.apply(AppState(), command, listOf(input(items)), UUID.randomUUID(), now, zone)
            assertNotNull(result.message, result.next)
            assertEquals(count, result.next!!.doses.size)
            assertEquals(count, result.next.doses.map { it.id }.distinct().size)
        }
    }
    @Test fun invalidLastItemAndOversizedBatchNeverPartiallySave() {
        val items = doses(50)
        val state = AppState()
        val invalid = ChatCommand("batch", doses = items.dropLast(1) + items.last().copy(amount = ""))
        assertNull(ChatCommands.apply(state, invalid, listOf(input(items)), UUID.randomUUID(), now, zone).next)
        assertNull(ChatCommands.apply(state, ChatCommand("batch", doses = doses(51)), listOf(input(doses(51))), UUID.randomUUID(), now, zone).next)
        assertTrue(state.doses.isEmpty())
    }
    @Test fun completedConversationIsAvailableButCannotReplayItsRecords() {
        var state = AppState()
        var payload = JSONObject()
        var callback: ((Result<String>) -> Unit)? = null
        val chat = ChatSession({ state }, { next, done -> state = next; done(true) },
            { _, text, done -> payload = JSONObject(text); callback = done }, {}, clock = { now })
        val request = "2020-01-01 15:30 EV 5mg 근육주사 기록해줘"
        val answer = """{"structured_output":{"action":"dose","drug":"EV","route":"근육주사","amount":"5mg","date":"2020-01-01","time":"15:30"}}"""
        chat.connect(); chat.send(request); callback!!(Result.success(answer))
        chat.send("고마워")
        assertTrue(payload.getJSONArray("contextMessages").toString().contains("2020-01-01"))
        assertEquals(1, payload.getJSONArray("messages").length())
        requireNotNull(callback)(Result.success(answer))
        assertEquals(1, state.doses.size)
        assertTrue(chat.canSend("다음 기록도 도와줘"))
        chat.reset(); chat.send("처음부터 도와줘")
        assertEquals(0, payload.getJSONArray("contextMessages").length())
    }
    @Test fun longConversationAutomaticallyKeepsAcceptingInput() {
        var count = 0
        var callback: ((Result<String>) -> Unit)? = null
        val chat = ChatSession({ AppState() }, { _, _ -> error("must not save") }, { _, payload, done ->
            count++; assertTrue(payload.toByteArray().size < 128_000); callback = done
        }, {}, clock = { now })
        chat.connect()
        repeat(30) { index ->
            chat.send("도와줘 $index")
            assertTrue(chat.ui.busy)
            callback!!(Result.success("""{"structured_output":{"action":"ask","reply":"어떤 기록인가요?"}}"""))
        }
        assertEquals(30, count)
    }
    @Test fun multilineBacklogLargerThanFifteenHundredCharactersIsAccepted() {
        var payload = ""
        val chat = ChatSession({ AppState() }, { _, _ -> error("must not save") }, { _, text, _ -> payload = text }, {}, clock = { now })
        val text = input(doses(50))
        assertTrue(text.length > 1500)
        chat.connect(); chat.send(text)
        assertEquals(text, JSONObject(payload).getJSONArray("messages").getJSONObject(0).getString("text"))
    }
    @Test fun largeKoreanConversationFitsTransportWithoutDroppingNewestInput() {
        var callback: ((Result<String>) -> Unit)? = null
        var payload = ""
        val chat = ChatSession({ AppState() }, { _, _ -> error("must not save") }, { _, text, done -> payload = text; callback = done }, {}, clock = { now })
        chat.connect()
        repeat(25) { index ->
            val text = "가".repeat(7000) + index
            chat.send(text)
            assertTrue(payload.toByteArray().size < 128_000)
            val messages = JSONObject(payload).getJSONArray("messages")
            assertEquals(text, messages.getJSONObject(messages.length() - 1).getString("text"))
            requireNotNull(callback)(Result.success("""{"structured_output":{"action":"ask","reply":"기록 내용을 알려주세요"}}"""))
        }
    }
    @Test fun fiftyBackfilledRecordsCanBeUndoneTogether() {
        var state = AppState()
        var callback: ((Result<String>) -> Unit)? = null
        val chat = ChatSession({ state }, { next, done -> state = next; done(true) }, { _, _, done -> callback = done }, {}, clock = { now })
        val items = doses(50)
        val raw = JSONObject().put("structured_output", JSONObject().put("action", "batch").put("doses", JSONArray().apply {
            items.forEach { put(JSONObject().put("drug", it.drug).put("route", it.route).put("amount", it.amount).put("date", it.date).put("time", it.time)) }
        })).toString()
        chat.connect(); chat.send(input(items)); requireNotNull(callback)(Result.success(raw))
        assertEquals(50, state.doses.size)
        chat.undo()
        assertTrue(state.doses.isEmpty())
    }
}
