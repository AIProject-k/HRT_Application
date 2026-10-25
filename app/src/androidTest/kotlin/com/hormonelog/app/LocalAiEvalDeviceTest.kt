package com.hormonelog.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.SamplerConfig
import com.hormonelog.app.ai.LiteRtLmEngine
import com.hormonelog.app.ai.LocalPrompt
import com.hormonelog.app.ai.ModelManager
import com.hormonelog.app.ai.ModelSpec
import com.hormonelog.app.chat.ChatProtocol
import com.hormonelog.app.eval.EVAL_CASES_V2
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Collects raw model replies for the fixed synthetic cases. It does not score them: the host scores the file with
 * the same expectations (EvalScorer), so the device cannot grade its own output. Writes cacheDir/local-ai-eval-raw.json.
 * Gated by -e localAiEval yes so a normal run never loads the model. Without modelFile the pinned Qwen model is used;
 * with modelFile, modelSize and modelSha256 another .litertlm already in the model folder is evaluated the same way.
 * systemNote=batchObject appends one prompt line (one variable at a time); without it the product prompt is used.
 */
class LocalAiEvalDeviceTest {
    private val notes = mapOf(
        "batchObject" to "여러 건이면 action을 batch로 쓰고 doses 배열에 넣는다. 배열([])만 최상위로 출력하지 않는다.",
    )

    @Test fun rawRepliesForSyntheticCases() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("localAiEval") == "yes")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val spec = args.getString("modelFile")?.let {
            ModelSpec(it, "", args.getString("modelSize").orEmpty().toLong(), args.getString("modelSha256").orEmpty())
        } ?: ModelSpec.QWEN
        val note = args.getString("systemNote")?.let { notes.getValue(it) }
        val system = note?.let { LocalPrompt.SYSTEM + "\n" + it }
        val manager = ModelManager(File(context.noBackupFilesDir, "offline-ai"), spec)
        assertTrue("Model must be provisioned and verified", manager.isInstalled())
        val rows = JSONArray()
        LiteRtLmEngine(manager, File(context.cacheDir, "offline-ai-eval")).use { engine ->
            for (case in EVAL_CASES_V2) {
                val row = JSONObject().put("id", case.id).put("input", case.input)
                val payload = ChatProtocol.request(listOf(true to case.input))
                val start = System.nanoTime()
                try {
                    val prompt = LocalPrompt.build(payload)
                    row.put("raw", if (system == null) engine.generate(prompt) { false }
                        else engine.generate(prompt, system, SamplerConfig(1, 1.0, 0.0), json = false) { false })
                } catch (e: Exception) {
                    row.put("error", e.javaClass.simpleName)
                }
                row.put("ms", (System.nanoTime() - start) / 1_000_000)
                rows.put(row)
            }
        }
        File(context.cacheDir, "local-ai-eval-raw.json").writeText(JSONObject()
            .put("model", spec.fileName).put("modelSha256", spec.sha256).put("systemNote", args.getString("systemNote").orEmpty())
            .put("rows", rows).toString(2))
        assertTrue(rows.length() == EVAL_CASES_V2.size)
    }
}
