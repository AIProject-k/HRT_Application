package com.hormonelog.app.chat

import com.hormonelog.app.at
import com.hormonelog.app.daily
import com.hormonelog.app.state.AppState
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Everyday wording on the rule path: no model is installed here, so a model call is a miss. */
class EverydayChatTest {
    private data class Saved(val drug: Drug, val route: Route, val mg: Double, val at: String = "2026-10-24 23:00")

    private val doses = listOf(
        "방금 에스트라디올 발레레이트 5mg 피하주사 맞았어" to Saved(Drug.ESTRADIOL_VALERATE, Route.SC_INJECTION, 5.0),
        "방금 발레레이트 5mg 근육 주사 맞았어" to Saved(Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 5.0),
        "방금 EV 5mg 근주 맞았어" to Saved(Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 5.0),
        "방금 EV 5mg 피하 맞았어" to Saved(Drug.ESTRADIOL_VALERATE, Route.SC_INJECTION, 5.0),
        "방금 프로기노바 데포 10mg 근육주사 맞았어" to Saved(Drug.ESTRADIOL_VALERATE, Route.IM_INJECTION, 10.0),
        "방금 사이피오네이트 5mg 근육주사 맞았어" to Saved(Drug.ESTRADIOL_CYPIONATE, Route.IM_INJECTION, 5.0),
        "방금 스피로 100mg 먹었어" to Saved(Drug.SPIRONOLACTONE, Route.ORAL, 100.0),
        "방금 알닥톤 50mg 먹었어" to Saved(Drug.SPIRONOLACTONE, Route.ORAL, 50.0),
        "방금 cpa 12.5mg 먹었어" to Saved(Drug.CYPROTERONE, Route.ORAL, 12.5),
        "방금 프레다 2mg 먹었어" to Saved(Drug.ESTRADIOL_TABLET, Route.ORAL, 2.0),
        "방금 에스트라디올 2mg 혀 밑에 녹였어" to Saved(Drug.ESTRADIOL_TABLET, Route.SUBLINGUAL, 2.0),
        "방금 에스트라디올 2mg 삼켰어" to Saved(Drug.ESTRADIOL_TABLET, Route.ORAL, 2.0),
        "한 시간 전에 에스트라디올 2mg 먹었어" to Saved(Drug.ESTRADIOL_TABLET, Route.ORAL, 2.0, "2026-10-24 22:00"),
        "어젯밤 11시에 에스트라디올 2mg 먹었어" to Saved(Drug.ESTRADIOL_TABLET, Route.ORAL, 2.0, "2026-10-23 23:00"),
    )

    @Test fun everydayDoseWordingSavesTheDoseItSays() {
        val misses = doses.mapNotNull { (text, want) ->
            val h = ChatHarness().send(text)
            val got = h.doses.singleOrNull()?.let { Saved(it.drug, it.route, it.amountEntered, ChatHarness.kst(it.occurredAt)) }
            if (got == want && h.modelCalls == 0) null else "$text -> $got / ${h.lastReply.replace('\n', ' ')}"
        }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }

    @Test fun anHourWordInsideALargerOrRangedNumberIsNotReadAsThatHour() {
        for (word in listOf("열한", "열두", "스물한", "두세", "두 세", "한 두", "세네")) {
            val h = ChatHarness().send("$word 시간 전 에스트라디올 2mg 먹었어")
            assertTrue("$word -> ${h.doses.map { ChatHarness.kst(it.occurredAt) }}", h.doses.isEmpty())
        }
        val yes = ChatHarness().send("네 한 시간 전에 에스트라디올 2mg 먹었어")
        assertEquals(listOf("2026-10-24 22:00"), yes.doses.map { ChatHarness.kst(it.occurredAt) })
    }

    @Test fun aProgynovaTabletFromChatMovesTheOldTabletScheduleAndUndoMovesItBack() {
        val h = ChatHarness()
        h.state = AppState(regimens = listOf(daily(at(2026, 10, 1), Drug.ESTRADIOL_TABLET, Route.ORAL, 2.0, 9 * 60)))
        h.send("방금 프로기노바 2mg 먹었어")
        assertEquals(h.lastReply, listOf(Drug.ESTRADIOL_VALERATE), h.state.regimens.map { it.drug })
        assertTrue(h.chat.ui.canUndo)
        h.chat.undo()
        assertEquals(listOf(Drug.ESTRADIOL_TABLET), h.state.regimens.map { it.drug })
        assertTrue(h.doses.isEmpty())
    }

    @Test fun pillsTakenTogetherAreSavedEachWithItsOwnAmount() {
        for (text in listOf("방금 에스트라디올 2mg, 스피로노락톤 50mg 먹었어", "방금 에스트라디올 2mg이랑 스피로 50mg 먹었어", "30분 전에 에스트라디올 2mg 그리고 스피로노락톤 50mg 복용")) {
            val h = ChatHarness().send(text)
            assertEquals("$text / ${h.lastReply}", setOf(Drug.ESTRADIOL_TABLET to 2.0, Drug.SPIRONOLACTONE to 50.0), h.doses.map { it.drug to it.amountEntered }.toSet())
            assertEquals(text, 1, h.doses.map { it.occurredAt }.distinct().size)
        }
    }

    @Test fun aSentenceThatMixesPillsWithAnInjectionOrLacksATimeSavesNothingByRule() {
        for (text in listOf("방금 EV 5mg 근육주사 맞고, 스피로노락톤 50mg 먹었어", "에스트라디올 2mg, 스피로노락톤 50mg 먹었어",
            "방금 에스트라디올 2mg, 에스트라디올 4mg 먹었어", "방금 에스트라디올 2mg, 스피로노락톤 안 먹었어")) {
            val h = ChatHarness().send(text)
            assertTrue("$text / ${h.lastReply}", h.doses.isEmpty())
        }
    }

    @Test fun labResultsInOneMessageAreSavedTogether() {
        val h = ChatHarness().send("오늘 오전 9시 피검사 E2 150 pg/mL, 테스토스테론 20 ng/dL 나왔어")
        assertEquals(h.lastReply, setOf(Analyte.ESTRADIOL, Analyte.TOTAL_TESTOSTERONE), h.state.labs.single().analytes.map { it.analyte }.toSet())
        assertTrue(ChatHarness().send("오늘 오전 9시 검사 유리 테스토스테론 20 ng/dL 기록").state.labs.isEmpty())
    }

    @Test fun readsAndHelpAnswerWithoutTheModelAndSaveNothing() {
        for (text in listOf("현재 상태", "지금 수치 어때", "내 수치 알려줘", "지금 E2 얼마야?", "다음 주사 언제", "최근 기록", "검사 결과 보여줘", "도움말", "뭐 할 수 있어?")) {
            val h = ChatHarness().send(text)
            assertTrue("$text / ${h.lastReply}", h.modelCalls == 0 && h.doses.isEmpty() && h.state.labs.isEmpty() && h.lastReply.isNotBlank())
        }
        // A read phrase with a record inside it is not a read.
        assertEquals(1, ChatHarness().send("방금 에스트라디올 2mg 먹었어 현재 상태 알려줘").doses.size)
    }

    @Test fun aPillCountIsNeverTurnedIntoMilligrams() {
        val h = ChatHarness().send("방금 에스트라디올 1알 먹었어")
        assertTrue(h.lastReply, h.doses.isEmpty() && h.lastReply.contains("정·알"))
    }
}
