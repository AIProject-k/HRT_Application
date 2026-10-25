package com.hormonelog.app.eval

import com.hormonelog.app.chat.ChatProtocol
import com.hormonelog.app.chat.RuleBasedParser
import com.hormonelog.app.state.AppState
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rules alone on the whole golden set, with no model. Any case the rules decide must have no wrong save,
 * and the cases the rules leave open are reported, not counted as passes.
 */
class EvalRuleOnlyTest {
    @Test fun rulesNeverSaveAWrongRecordOnTheGoldenSet() {
        val decided = mutableListOf<EvalOutcome>()
        val open = mutableListOf<String>()
        for (case in EVAL_CASES_V2) {
            val rule = RuleBasedParser.parse(ChatProtocol.request(listOf(true to case.input)), AppState())
            if (rule == null) open += case.id else decided += EvalScorer.routed(case, rule, null)
        }
        val wrong = decided.filter { it.wrongSaved > 0 }.map { "${it.id}: ${it.saved}" }
        assertEquals("rules saved a wrong record: $wrong", emptyList<String>(), wrong)
        println("RULE_ONLY decided=${decided.size} open=${open.size} openIds=$open passed=${decided.count { it.pass }}")
        decided.filter { !it.pass }.forEach { println("RULE_ONLY_MISS ${it.id} missed=${it.missedSaved} saved=${it.saved} message=${it.message}") }
    }
}
