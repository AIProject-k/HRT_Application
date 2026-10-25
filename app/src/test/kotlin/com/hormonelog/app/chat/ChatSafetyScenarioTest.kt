package com.hormonelog.app.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/** Stage 1 and 2 scenarios: intent per clause and value binding. The model stub returns what a wrong model could return. */
class ChatSafetyScenarioTest {
    private val none = emptyList<String>()

    @Test fun plannedDoseIsNotSavedAndNeverCallsTheModel() {
        val h = ChatHarness().send("오늘 10:00 프로기노바 2mg 복용할 거야")
        assertEquals(none, h.doseTimes())
        assertEquals(0, h.modelCalls)
    }

    @Test fun questionDoseIsNotSaved() {
        val h = ChatHarness().send("오늘 10:00 프로기노바 2mg 먹었나요")
        assertEquals(none, h.doseTimes())
        assertEquals(0, h.modelCalls)
    }

    @Test fun quotedAndHypotheticalWordingIsNotSaved() {
        assertEquals(none, ChatHarness().send("예: 방금 프로기노바 2mg 먹었어").doseTimes())
        assertEquals(none, ChatHarness().send("‘방금 프로기노바 2mg 먹었어’라고 기록해줘").doseTimes())
        assertEquals(none, ChatHarness().send("만약 방금 프로기노바 2mg 먹었다면 기록해줘").doseTimes())
    }

    @Test fun negatedClauseBlocksAModelDoseWithTheSameGate() {
        val h = ChatHarness(model = {
            ChatHarness.structured(ChatCommand("dose", drug = "프로기노바", amount = "2", amountUnit = "mg", date = "오늘", time = "10:00"))
        })
        h.send("어제는 프로기노바 못 먹었고 오늘은 2mg 먹었어")
        assertEquals(none, h.doseTimes())
    }

    @Test fun crossedValuesInOneSegmentAreNotSaved() {
        val crossed = ChatHarness(model = {
            ChatHarness.structured(ChatHarness.batch("오늘", ChatHarness.dose("프로기노바", "2", "10:00"), ChatHarness.dose("안쿨", "25", "20:00")))
        })
        crossed.send("오늘 10:00 프로기노바 2mg 안쿨 25mg 오늘 20:00 복용")
        assertEquals(none, crossed.doseTimes())
    }

    @Test fun valuesSeparatedByCommasAreSavedToTheirOwnDrug() {
        val bound = ChatHarness(model = {
            ChatHarness.structured(ChatHarness.batch("오늘", ChatHarness.dose("프로기노바", "2", "10:00"), ChatHarness.dose("안쿨", "25", "20:00")))
        })
        bound.send("오늘 10:00 프로기노바 2mg, 오늘 20:00 안쿨 25mg 복용")
        assertEquals(listOf("2026-10-24 10:00", "2026-10-24 20:00"), bound.doseTimes())
    }

    @Test fun bareUnitAfterTimeAnswerKeepsTheTime() {
        val h = ChatHarness().send("오늘 10:00 프로기노바 2 복용").send("mg")
        assertEquals(listOf("2026-10-24 10:00"), h.doseTimes())
        assertEquals(2.0, h.doses.single().amountEntered, 0.0)
    }

    @Test fun completeNewRecordDoesNotMergeIntoAnOpenRequest() {
        val h = ChatHarness().send("어제 프로기노바 2mg 복용").send("오늘 22:00 프로기노바 2mg 복용")
        assertEquals(listOf("2026-10-24 22:00"), h.doseTimes())
        assertEquals(0, h.modelCalls)
    }

    @Test fun unitAfterFullDateDoesNotTouchTheDate() {
        val h = ChatHarness().send("2026-10-24 10:00 프로기노바 2 복용").send("mg")
        assertEquals(listOf("2026-10-24 10:00"), h.doseTimes())
        assertEquals(2.0, h.doses.single().amountEntered, 0.0)
    }
}
