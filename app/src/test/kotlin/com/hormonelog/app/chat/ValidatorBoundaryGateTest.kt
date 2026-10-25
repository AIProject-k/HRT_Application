package com.hormonelog.app.chat

import com.hormonelog.app.state.AppState
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Model replies that the final validator must not save, taken from the device runs: two amounts in one message,
 * a memo request answered as a dose, and one drug twice without a separator. Each is sent straight to the validator.
 */
class ValidatorBoundaryGateTest {
    private fun apply(c: ChatCommand, input: String) =
        ChatCommands.apply(AppState(), c, listOf(input), UUID.randomUUID(), Instant.parse("2026-10-24T14:00:00Z"), ZoneId.of("Asia/Seoul"))

    @Test fun twoAmountsInOneMessageAreNotSaved() {
        assertNull(apply(ChatHarness.dose("프로기노바", "4mg", "방금"), "방금 프로기노바 2mg 4mg 복용").next)
    }

    @Test fun memoRequestIsNeverSavedAsADose() {
        assertNull(apply(ChatHarness.dose("프로기노바", "2mg", "방금"), "메모해줘: 방금 프로기노바 2mg 먹었어").next)
    }

    @Test fun sameDrugTwiceWithoutSeparatorIsNotSaved() {
        val c = ChatHarness.batch("오늘", ChatHarness.dose("프로기노바", "2mg", "10:00"), ChatHarness.dose("프로기노바", "2mg", "20:00"))
        assertNull(apply(c, "오늘 10:00 프로기노바 2mg 복용 오늘 20:00 프로기노바 2mg 복용").next)
    }

    @Test fun plainSingleDoseStillSaves() {
        assertNotNull(apply(ChatHarness.dose("프로기노바", "2mg", "방금"), "방금 프로기노바 2mg 먹었어").next)
    }
}
