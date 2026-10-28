package com.hormonelog.app.chat

import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Things people are likely to type about Provera, run on the rule path (no model). A None case must save no MPA dose.
 * A Save case must save exactly one oral MPA dose of that amount. Every miss is listed, so one run shows them all.
 */
class ChatUsageCasesTest {
    private sealed interface Expect {
        data class Save(val mg: Double) : Expect
        data object None : Expect

        /** No exact time or amount, so the app asks instead of guessing. */
        data object Ask : Expect
    }

    private val cases: List<Pair<String, Expect>> = listOf(
        "방금 프로베라 5mg 먹었어" to Expect.Save(5.0),
        "방금 프로베라 2.5mg 복용" to Expect.Save(2.5),
        "방금 프로베라 10mg 먹었어" to Expect.Save(10.0),
        "어제 21시 프로베라 5mg 먹었어" to Expect.Save(5.0),
        "방금 프로베라 5mg 먹음" to Expect.Save(5.0),
        "방금 프로베라 5mg 복용함" to Expect.Save(5.0),
        "방금 메드록시프로게스테론 5mg 먹었어" to Expect.Save(5.0),
        "방금 provera 2.5mg 복용" to Expect.Save(2.5),
        "방금 PROVERA 5mg 먹었어" to Expect.Save(5.0),
        "방금 medroxyprogesterone 5mg 먹었어" to Expect.Save(5.0),
        "방금 프로베라5mg먹었어" to Expect.Save(5.0),
        "방금 프로베라 5 mg 먹었어" to Expect.Save(5.0),
        "방금 프로베라 5밀리그램 먹었어" to Expect.Save(5.0),
        "오늘 아침 프로베라 2.5mg 먹었어" to Expect.Ask,
        "어제 저녁 프로베라 5mg 먹었어" to Expect.Ask,
        "오늘 저녁 7시 프로베라 5mg 먹었어" to Expect.Save(5.0),
        "프로베라 5mg 안 먹었어" to Expect.None,
        "프로베라 먹었나요?" to Expect.None,
        "프로베라 먹었어?" to Expect.None,
        "프로베라 5mg 먹을게" to Expect.None,
        "내일 프로베라 5mg 먹을 예정" to Expect.None,
        "프로베라 먹어야 해" to Expect.None,
        "프로베라 5mg 먹었다고 쳐" to Expect.None,
        "친구가 프로베라 5mg 먹었대" to Expect.None,
        "프로베라 먹었다는 얘기 들었어" to Expect.None,
        "프로베라 5mg 먹었는지 기억 안 나" to Expect.None,
        "프로베라 5mg 먹을까" to Expect.None,
        "프로베라 먹으면 좋을까?" to Expect.None,
        "프로베라 5mg 먹었던 것 같아" to Expect.None,
        "프로베라 주사 맞았어" to Expect.None,
        "프로베라 근육주사 5mg 맞았어" to Expect.None,
        "데포프로베라 150mg 맞았어" to Expect.None,
        "데포프로베라 5mg 맞았어" to Expect.None,
        "프로베라 설하로 5mg 먹었어" to Expect.None,
        "프로베라 질정 5mg 넣었어" to Expect.None,
        "프로베라 10mL 먹었어" to Expect.None,
        "프로베라 2알 먹었어" to Expect.None,
        "프로베라 두 알 먹었어" to Expect.None,
        "방금 프로베라 먹었어" to Expect.None,
        "프로베라 0mg 먹었어" to Expect.None,
        "프로베라 -5mg 먹었어" to Expect.None,
        "프로베라 5000mg 먹었어" to Expect.None,
        "프로베라 10 먹었어" to Expect.None,
        "오늘 9시 프로베라 5mg 먹었어" to Expect.Ask,
    )

    private fun mpaDoses(h: ChatHarness) = h.doses.filter { it.drug == Drug.MEDROXYPROGESTERONE_ACETATE }

    @Test fun noUnintendedMpaDoseIsSaved() {
        val saved = cases.mapNotNull { (text, expect) ->
            if (expect is Expect.Save) return@mapNotNull null
            val h = ChatHarness().send(text)
            if (mpaDoses(h).isEmpty()) null else "$text -> saved ${mpaDoses(h).size} / reply: ${h.lastReply.replace('\n', ' ')}"
        }
        assertTrue("${saved.size} unintended saves:\n" + saved.joinToString("\n"), saved.isEmpty())
    }

    @Test fun intendedMpaDosesAreSavedExactlyOnce() {
        val misses = cases.mapNotNull { (text, expect) ->
            if (expect !is Expect.Save) return@mapNotNull null
            val h = ChatHarness().send(text)
            val saved = mpaDoses(h)
            val ok = saved.size == 1 && saved.single().amountEntered == expect.mg && saved.single().route == Route.ORAL
            if (ok) null else "$text -> saved ${saved.size} / reply: ${h.lastReply.replace('\n', ' ')}"
        }
        assertTrue("${misses.size} missed saves:\n" + misses.joinToString("\n"), misses.isEmpty())
    }

    @Test fun aSentenceWithTwoDrugsNeverCrossesTheirAmounts() {
        val h = ChatHarness().send("유트로게스탄 200mg 먹었고 프로베라 5mg 먹었어")
        assertTrue(h.lastReply, h.doses.size <= 2)
        for (d in h.doses) {
            val paired = (d.drug == Drug.PROGESTERONE_MICRONIZED && d.amountEntered == 200.0) ||
                (d.drug == Drug.MEDROXYPROGESTERONE_ACETATE && d.amountEntered == 5.0)
            assertTrue("crossed: $d / ${h.lastReply}", paired)
        }
    }

    @Test fun aSymptomInTheSameSentenceNeverAddsADose() {
        val h = ChatHarness().send("프로베라 5mg 먹었는데 머리 아파")
        assertTrue(h.lastReply, h.doses.size <= 1)
        assertTrue(h.lastReply, h.doses.all { it.drug == Drug.MEDROXYPROGESTERONE_ACETATE && it.amountEntered == 5.0 })
    }

    @Test fun aMissingAmountIsAskedAndTheNextAnswerFillsIt() {
        val h = ChatHarness().send("방금 프로베라 먹었어").send("5mg")
        assertTrue(h.lastReply, mpaDoses(h).size == 1 && mpaDoses(h).single().amountEntered == 5.0)
    }
}
