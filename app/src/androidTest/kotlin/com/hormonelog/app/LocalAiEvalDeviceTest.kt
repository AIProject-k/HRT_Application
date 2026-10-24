package com.hormonelog.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hormonelog.app.ai.*
import com.hormonelog.app.chat.ChatCommand
import com.hormonelog.app.chat.ChatCommands
import com.hormonelog.app.chat.ChatProtocol
import com.hormonelog.app.chat.RuleBasedParser
import com.hormonelog.app.state.AppState
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Synthetic inputs only, run on the pinned model with a fixed clock (2026-10-24 23:00 KST). Writes cacheDir/local-ai-eval.json. */
class LocalAiEvalDeviceTest {
    private class Case(val id: String, val input: String, val expected: Int?)

    private fun batchOf(times: List<String>) = times.joinToString("\n") { "오늘 $it 프로기노바 2mg 복용" }

    private val cases = listOf(
        Case("batch-two-correct", "오늘 10:00 프로기노바 2mg 복용, 오늘 20:00 안쿨 25mg 복용", 2),
        Case("negation", "방금 프로기노바 2mg 먹지 않았어", 0),
        Case("question", "방금 안쿨 25mg 먹었어?", 0),
        Case("plan", "내일 안쿨 25mg 먹을 거야", 0),
        Case("symptom-mixed", "방금 프로기노바 2mg 먹었고 어지러웠어", null),
        Case("lab-e2", "오늘 10:00 E2 100pg/mL 검사 기록해줘", 1),
        Case("memo", "오늘 머리가 아프고 피곤했어 메모해줘", 0),
        Case("body-weight", "몸무게 60kg 기록해줘", 0),
        Case("batch-five", batchOf(listOf("09:00", "12:00", "15:00", "18:00", "21:00")), 5),
        Case("batch-ten", batchOf((8..17).map { "%02d:00".format(it) }), 10),
    )

    @Test fun syntheticSetOnPinnedModel() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("localAiEval") == "yes")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = ModelManager(File(context.noBackupFilesDir, "offline-ai"))
        val verifyStart = System.nanoTime()
        assertTrue("Pinned model must be provisioned and verified", manager.isInstalled())
        val verifyMs = (System.nanoTime() - verifyStart) / 1_000_000
        val now = Instant.parse("2026-10-24T14:00:00Z")
        val zone = ZoneId.of("Asia/Seoul")
        val modelMs = mutableListOf<Long>()
        val rows = JSONArray()
        LiteRtLmEngine(manager, File(context.cacheDir, "offline-ai-eval")).use { engine ->
            for (case in cases) rows.put(evaluate(engine, case, now, zone, modelMs))
        }
        val summary = JSONObject()
            .put("verifyMs", verifyMs)
            .put("cases", cases.size)
            .put("ruleCases", (0 until rows.length()).count { rows.getJSONObject(it).optString("path") == "rule" })
            .put("modelCases", (0 until rows.length()).count { rows.getJSONObject(it).optString("path") == "model" })
            .put("parseFailures", (0 until rows.length()).count { rows.getJSONObject(it).has("parse") })
            .put("wrongSaves", (0 until rows.length()).count { rows.getJSONObject(it).optString("outcome") == "wrong_save" })
            .put("missed", (0 until rows.length()).count { rows.getJSONObject(it).optString("outcome") == "missed" })
            .put("modelMsFirst", modelMs.firstOrNull() ?: JSONObject.NULL)
            .put("modelMsP50", percentile(modelMs, 50))
            .put("modelMsP95", percentile(modelMs, 95))
        val report = JSONObject().put("summary", summary).put("rows", rows)
        File(context.cacheDir, "local-ai-eval.json").writeText(report.toString(2))
        assertEquals(cases.size, rows.length())
    }

    private fun evaluate(engine: LiteRtLmEngine, case: Case, now: Instant, zone: ZoneId, modelMs: MutableList<Long>): JSONObject {
        val row = JSONObject().put("id", case.id).put("input", case.input)
        val payload = ChatProtocol.request(listOf(true to case.input))
        val rule = RuleBasedParser.parse(payload, AppState())
        val command: ChatCommand
        if (rule != null) {
            row.put("path", "rule")
            command = rule
        } else {
            row.put("path", "model")
            val start = System.nanoTime()
            val raw = try { engine.generate(LocalPrompt.build(payload)) { false } } catch (e: Exception) {
                return row.put("error", e.javaClass.simpleName)
            }
            val ms = (System.nanoTime() - start) / 1_000_000
            modelMs += ms
            row.put("ms", ms).put("raw", raw)
            command = try { LlmResponseParser.parse(raw) } catch (e: Exception) {
                return row.put("parse", "fail")
            }
        }
        val decision = ChatCommands.apply(AppState(), command, listOf(case.input), UUID.randomUUID(), now, zone)
        val next = decision.next
        val saved = (next?.doses?.size ?: 0) + (next?.labs?.size ?: 0)
        row.put("saved", saved)
            .put("expected", case.expected ?: JSONObject.NULL)
            .put("doses", JSONArray(next?.doses?.map { "${it.drug} ${it.amountEntered} ${it.occurredAt}" }.orEmpty()))
            .put("message", decision.message)
            .put("outcome", when {
                case.expected == null -> "report"
                saved == case.expected -> "match"
                saved > case.expected -> "wrong_save"
                else -> "missed"
            })
        return row
    }

    private fun percentile(values: List<Long>, p: Int): Any {
        if (values.isEmpty()) return JSONObject.NULL
        val sorted = values.sorted()
        return sorted[((p / 100.0) * sorted.size).let { kotlin.math.ceil(it).toInt() }.coerceIn(1, sorted.size) - 1]
    }
}
