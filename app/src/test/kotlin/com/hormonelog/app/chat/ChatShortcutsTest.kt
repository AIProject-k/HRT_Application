package com.hormonelog.app.chat

import com.hormonelog.app.state.AppState
import com.hormonelog.core.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ChatShortcutsTest {
    private val now = Instant.parse("2026-10-19T06:30:00Z")
    private fun chat(s: AppState = AppState()) = ChatSession({ s }, { _, _ -> error("read must not save") },
        { _, _, _ -> error("built-in query must not call Claude") }, {}, clock = { now })

    @Test fun builtInNamesRunOfflineWithoutLearningOrWritingRecords() {
        val s = AppState()
        val chat = chat(s)
        for ((text, prefix) in listOf("현재상태" to "현재 상태", "최근 검사" to "실측 검사", "다음주사" to "다음 주사")) {
            chat.send(text)
            assertEquals(2, chat.ui.lines.size)
            assertEquals(text, chat.ui.lines.first().text)
            assertTrue(chat.ui.lines.last().text.startsWith(prefix))
            assertFalse(chat.ui.connected)
            assertTrue(s.chatMemories.isEmpty())
            chat.reset()
        }
    }

    @Test fun memoryListIncludesPreloadedCommandsBeforeAnyTeaching() {
        val chat = chat()
        chat.listMemories()
        val text = chat.ui.lines.last().text
        for (name in listOf("현재 상태", "최근 검사", "다음 주사")) assertTrue(text, text.contains(name))
        assertTrue(text, text.contains("기본 단축어"))
        assertTrue(ChatMemoryActions.list(AppState(), "다음주사").contains("등록된 다음 주사 일정 조회"))
    }

    @Test fun newPersonalNamesCannotOverrideBuiltInCommands() {
        for (name in listOf("현재상태", "최근 검사", "다음주사")) assertTrue(name, ChatCommands.reservedName(name))
    }

    @Test fun previouslySavedPersonalMemoryKeepsItsMeaning() {
        val memory = ChatMemory.Alias("현재 상태", Drug.PROGESTERONE_MICRONIZED)
        val s = AppState(chatMemories = listOf(memory))
        var calls = 0
        val chat = ChatSession({ s }, { _, _ -> error("must not save") }, { _, _, _ -> calls++ }, {})
        chat.connect(); chat.send("현재상태")
        assertEquals(1, calls)
        assertEquals(listOf(memory), s.chatMemories)
        val update = ChatMemoryActions.apply(s, ChatCommand("update_alias", memoryName = memory.name, memoryTarget = "유트로게스탄"),
            listOf("현재 상태는 유트로게스탄으로 바꿔줘"), java.util.UUID.randomUUID(), now, java.time.ZoneId.of("Asia/Seoul"))
        assertNotNull(update.message, update.next)
    }

    @Test fun longerSentencesAndNegationsAreNotExecutedAsExactShortcuts() {
        val chat = chat()
        for (text in listOf("다음 주사 맞았어", "현재 상태 기억 지워줘", "최근 검사 하지마", "현재 상태 알려줘")) chat.send(text)
        assertTrue(chat.ui.lines.isEmpty())
    }

    @Test fun pendingRequestsCannotBeReplacedByLocalShortcuts() {
        val chat = ChatSession({ AppState() }, { _, _ -> error("must not save") }, { _, _, _ -> }, {})
        chat.connect(); chat.send("투약을 기록하고 싶어")
        assertTrue(chat.ui.busy)
        assertFalse(chat.canSend("현재상태"))
        chat.send("현재상태"); chat.runShortcut(ChatShortcut.INJECTION)
        assertEquals(1, chat.ui.lines.size)
        chat.cancel(); chat.runShortcut(ChatShortcut.INJECTION)
        assertTrue(chat.ui.lines.last().text.startsWith("다음 주사"))
    }

    @Test fun valuesShownByTypedShortcutAreNotSentToTheNextModelRequest() {
        val lab = LabResult(java.util.UUID.randomUUID(), null, null, Assay.UNKNOWN,
            listOf(LabAnalyteValue(Analyte.ESTRADIOL, 123.456, "pmol/L", null)))
        val s = AppState(labs = listOf(lab))
        var payload = ""
        val chat = ChatSession({ s }, { _, _ -> error("must not save") }, { _, data, _ -> payload = data }, {})
        chat.send("최근검사")
        assertTrue(chat.ui.lines.last().text.contains("123.456"))
        chat.connect(); chat.send("도와줘")
        assertFalse(payload.contains("123.456"))
        assertEquals(1, org.json.JSONObject(payload).getJSONArray("messages").length())
    }
}
