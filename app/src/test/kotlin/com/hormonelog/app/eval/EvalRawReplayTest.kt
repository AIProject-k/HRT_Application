package com.hormonelog.app.eval

import com.hormonelog.app.chat.ChatProtocol
import com.hormonelog.app.chat.RuleBasedParser
import com.hormonelog.app.state.AppState
import java.io.File
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Scores stored raw device replies with the fixed expectations. Every case in EVAL_CASES is in the denominator;
 * a case with no stored reply is counted as a failure. Writes build/reports/eval-replay-<file>.json.
 */
class EvalRawReplayTest {
    @Test fun replaysStoredRawReplies() {
        val dir = File("../docs/chat-review-2026-10-25/evidence/model-raw")
        val files = dir.listFiles { f -> f.extension == "json" }?.sortedBy { it.name }.orEmpty()
        assumeTrue("no stored device replies", files.isNotEmpty())
        val reports = File("build/reports").apply { mkdirs() }
        for (file in files) {
            // Files named v2-* were collected on EVAL_CASES_V2; earlier files were collected on the 15 original cases.
            val cases = if (file.name.startsWith("v2-")) EVAL_CASES_V2 else EVAL_CASES
            val raw = JSONObject(file.readText()).getJSONArray("rows").let { rows ->
                (0 until rows.length()).associate { rows.getJSONObject(it).getString("id") to rows.getJSONObject(it).optString("raw", "") }
            }
            val forced = cases.map { case ->
                val reply = raw[case.id]?.takeIf { it.isNotEmpty() }
                if (reply == null) EvalOutcome(case.id, "missing", false, false, case.doses.size, 0, 0, case.doses.size + case.labs.size, emptyList(), "")
                else EvalScorer.forced(case, reply)
            }
            val routed = cases.map { case ->
                val rule = RuleBasedParser.parse(ChatProtocol.request(listOf(true to case.input)), AppState())
                EvalScorer.routed(case, rule, raw[case.id]?.takeIf { it.isNotEmpty() })
            }
            fun summary(mode: String, outcomes: List<EvalOutcome>) = JSONObject()
                .put("mode", mode)
                .put("cases", outcomes.size)
                .put("pass", outcomes.count { it.pass })
                .put("parseFail", outcomes.count { it.parseFail })
                .put("wrongSavedRecords", outcomes.sumOf { it.wrongSaved })
                .put("missedRecords", outcomes.sumOf { it.missedSaved })
                .put("itemsExpected", outcomes.sumOf { it.itemsExpected })
                .put("itemsMatched", outcomes.sumOf { it.itemsMatched })
                .put("routeCounts", outcomes.groupingBy { it.route }.eachCount().toString())
            val report = JSONObject()
                .put("source", file.name)
                .put("forced", summary("forced-model", forced))
                .put("routed", summary("routed-rules-first", routed))
                .put("perCase", org.json.JSONArray(forced.map { JSONObject().put("id", it.id).put("pass", it.pass).put("parseFail", it.parseFail).put("saved", org.json.JSONArray(it.saved)).put("missed", it.missedSaved).put("wrong", it.wrongSaved).put("itemsMatched", it.itemsMatched).put("itemsExpected", it.itemsExpected) }))
                .put("routedPerCase", org.json.JSONArray(routed.map { JSONObject().put("id", it.id).put("route", it.route).put("pass", it.pass).put("parseFail", it.parseFail).put("saved", org.json.JSONArray(it.saved)).put("missed", it.missedSaved).put("wrong", it.wrongSaved) }))
            File(reports, "eval-replay-${file.nameWithoutExtension}.json").writeText(report.toString(2))
        }
    }
}
