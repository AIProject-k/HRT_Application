package com.hormonelog.app.chat

import com.hormonelog.app.ai.LocalPrompt
import com.hormonelog.app.ai.LlmResponseParser
import com.hormonelog.app.state.AppState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LocalChatTest {
    @Test fun plainJsonAndSingleFenceDecodeButMalformedOutputCannotCreateCommands() {
        val json = """{"action":"dose","drug":"안쿨","amount":"25mg","time":"방금"}"""
        assertEquals("안쿨", LlmResponseParser.parse(json).drug)
        assertEquals("dose", LlmResponseParser.parse("```json\n$json\n```").action)
        for (raw in listOf("$json $json", "설명 $json", """{"action":"dose","usual":"true"}""",
            """{"action":"dose","drug":42}""", """{"action":"delete"}""", """{"action":"dose","extra":true}""")) {
            assertThrows(IllegalArgumentException::class.java) { LlmResponseParser.parse(raw) }
        }
        assertEquals("", LlmResponseParser.parse("""{"action":"ask","reply":"약을 두 배로 드세요"}""").reply)
    }

    @Test fun literalNowInDateSlotIsTreatedAsARelativeTimeWithoutInventingAClock() {
        val command=LlmResponseParser.parse("""{"action":"dose","drug":"안쿨","amount":"25mg","date":"방금","time":"방금"}""")
        assertEquals("",command.date)
        assertEquals("방금",command.time)
        val conflicting=LlmResponseParser.parse("""{"action":"dose","date":"방금","time":"21:00"}""")
        assertEquals("방금",conflicting.date)
    }

    @Test fun rulesUseLiteralValuesAndLeaveUnspecifiedTimeMissing() {
        val command = RuleBasedParser.parse(ChatProtocol.request(listOf(true to "어제 저녁 먹고 유트로게스탄 100mg 복용했어")), AppState())!!
        assertEquals("dose", command.action)
        assertEquals("100mg", command.amount)
        assertEquals("어제", command.date)
        assertEquals("", command.time)
        val result = ChatCommands.apply(AppState(), command, listOf("어제 저녁 먹고 유트로게스탄 100mg 복용했어"),
            java.util.UUID.randomUUID(), java.time.Instant.now(), java.time.ZoneId.of("UTC"))
        assertNull(result.next)
        assertTrue(result.message.contains("언제"))
        assertNull(RuleBasedParser.parse(ChatProtocol.request(listOf(true to "EV 5mg과 안쿨 25mg 기록해줘")), AppState()))
    }

    @Test fun rulesHandleCurrentQueriesAndPendingFollowupWithoutAModel() {
        assertEquals("status", RuleBasedParser.parse(ChatProtocol.request(listOf(true to "현재 호르몬 수치 보여줘")), AppState())!!.action)
        val pending = ChatCommand("dose", drug="안쿨", amount="25mg")
        val payload = ChatProtocol.request(listOf(true to "안쿨 25mg 먹었어", false to "언제?", true to "방금"), pending=pending)
        val command = RuleBasedParser.parse(payload, AppState())!!
        assertEquals("안쿨", command.drug)
        assertEquals("방금", command.time)
    }

    @Test fun conflictingDatesTimesAndRoutesRequireInterpretationRatherThanBecomingImplicitNowOrOral() {
        for(input in listOf("안쿨 25mg 오늘 아니 어제 방금 먹었어", "프로기노바 2mg 경구 아니 설하 방금 복용했어",
            "오늘 안쿨 25mg 오후 3시 아니 21:00 먹었어")) {
            assertNull(input,RuleBasedParser.parse(ChatProtocol.request(listOf(true to input)),AppState()))
        }
    }

    @Test fun promptOmitsCompletedHistoryAndUnrelatedMemoryAndNeverTruncatesAnActiveRequest() {
        val payload = ChatProtocol.request(listOf(true to "안쿨 25mg 방금 먹었어"), context=listOf(false to "실측 9876.543 ng/dL"))
        val prompt = LocalPrompt.build(payload)
        assertFalse(prompt.contains("9876.543"))
        assertTrue(prompt.contains("안쿨 25mg"))
        assertThrows(IllegalArgumentException::class.java) { LocalPrompt.build(ChatProtocol.request(listOf(true to "가".repeat(10000)))) }
    }

    @Test fun offlineSessionStillUsesStorageAcknowledgementAndUndo() {
        var state=AppState()
        var acknowledgement: ((Boolean)->Unit)?=null
        val chat=ChatSession({state}, {next, done -> state=next; acknowledgement=done}, {_, input, done ->
            val command=RuleBasedParser.parse(input, state)!!
            done(Result.success(JSONObject().put("structured_output", ChatProtocol.commandJson(command)).toString()))
        }, {}, allowOfflineInput=true)
        chat.send("안쿨 25mg 방금 먹었어")
        assertEquals(1,state.doses.size)
        assertTrue(chat.ui.saving)
        assertFalse(chat.ui.lines.any { it.text.startsWith("저장 완료") })
        acknowledgement!!(true)
        chat.undo(); acknowledgement!!(true)
        assertTrue(state.doses.isEmpty())
    }
}
