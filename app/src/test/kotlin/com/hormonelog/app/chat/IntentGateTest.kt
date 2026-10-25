package com.hormonelog.app.chat

import com.hormonelog.app.state.AppState
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sentences that describe a dose without proving it happened must never become a record.
 * Each one is sent alone, then a clean dose is sent in a new message, which must save exactly one record.
 */
class IntentGateTest {
    private val notCompleted = listOf(
        "방금 프로기노바 2mg 먹었나요",
        "방금 프로기노바 2mg 먹었어?",
        "방금 프로기노바 2mg 먹었어? 확인해줘",
        "오늘 10:00 프로기노바 2mg 복용할 거야",
        "오늘 10:00 프로기노바 2mg 복용할 예정이야",
        "다음에 프로기노바 2mg 먹을 거야",
        "방금 프로기노바 2mg 먹으려다 안 먹었어",
        "방금 프로기노바 2mg 먹었다고 가정하면",
        "예시: 방금 프로기노바 2mg 먹었어",
        "방금 프로기노바 2mg 복용 못했어",
        "방금 프로기노바 2mg 먹지 않았어",
        "어제는 프로기노바 2mg 못 먹었고 오늘은 먹었어",
    )

    @Test fun uncertainSentencesSaveNothing() {
        for (text in notCompleted) {
            val h = ChatHarness()
            h.send(text)
            assertTrue("saved from: $text", h.doses.isEmpty())
        }
    }

    /** A model that answers "dose" for these sentences must still be stopped by the final validator. */
    @Test fun finalValidatorRejectsModelDoseForUncertainSentences() {
        for (text in notCompleted) {
            val decision = ChatCommands.apply(AppState(), ChatHarness.dose("프로기노바", "2mg", "방금"), listOf(text),
                UUID.randomUUID(), Instant.parse("2026-10-24T14:00:00Z"), ZoneId.of("Asia/Seoul"))
            assertNull("saved from: $text", decision.next)
        }
    }

    @Test fun cleanDoseAfterRefusalSavesExactlyOne() {
        for (text in notCompleted) {
            val h = ChatHarness()
            h.send(text)
            h.send("방금 프로기노바 2mg 복용")
            assertEquals("after: $text", 1, h.doses.size)
            assertEquals("after: $text", "2026-10-24 23:00", ChatHarness.kst(h.doses.single().occurredAt))
        }
    }
}
