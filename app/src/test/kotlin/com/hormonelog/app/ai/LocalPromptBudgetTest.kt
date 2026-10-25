package com.hormonelog.app.ai

import com.hormonelog.app.chat.ChatProtocol
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPromptBudgetTest {
    private fun payload(lines: Int) = ChatProtocol.request(listOf(true to (1..lines).joinToString("\n") { "오늘 프로기노바 2mg 복용 $it" }))

    @Test fun fiveLinesAreWithinTheModelBudget() {
        assertTrue(LocalPrompt.build(payload(LocalPrompt.MAX_MODEL_LINES)).isNotEmpty())
    }

    @Test fun sixLinesAreRefusedBeforeAnyModelCall() {
        val error = assertThrows(IllegalArgumentException::class.java) { LocalPrompt.build(payload(LocalPrompt.MAX_MODEL_LINES + 1)) }
        assertTrue(error.message.orEmpty().contains("${LocalPrompt.MAX_MODEL_LINES}줄"))
    }
}
