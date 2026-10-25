package com.hormonelog.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.SamplerConfig
import com.hormonelog.app.ai.LiteRtLmEngine
import com.hormonelog.app.ai.LocalPrompt
import com.hormonelog.app.ai.ModelManager
import com.hormonelog.app.chat.ChatProtocol
import com.hormonelog.app.eval.EVAL_CASES
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Model experiment on synthetic cases, one condition at a time: system prompt, sampler, JSON response format.
 * Each condition writes cacheDir/local-ai-exp-<condition>.json with the raw replies. The host scores them
 * (EvalRawReplayTest) with the same fixed expectations. Gated by -e localAiExperiment yes.
 */
class LocalAiExperimentDeviceTest {
    private data class Condition(val id: String, val system: String, val sampler: SamplerConfig, val json: Boolean)

    /** Each condition changes one thing from short-keys: the JSON constraint, then the sampler. */
    private val conditions = listOf(
        Condition("short-keys", LocalPrompt.SYSTEM_SHORT_KEYS, SamplerConfig(1, 1.0, 0.0), json = false),
        Condition("short-keys-json", LocalPrompt.SYSTEM_SHORT_KEYS, SamplerConfig(1, 1.0, 0.0), json = true),
        Condition("short-keys-official", LocalPrompt.SYSTEM_SHORT_KEYS, SamplerConfig(20, 0.8, 0.7), json = false),
    )

    @Test fun conditionsOnSyntheticCases() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("localAiExperiment") == "yes")
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager = ModelManager(File(context.noBackupFilesDir, "offline-ai"))
        assertTrue("Pinned model must be provisioned and verified", manager.isInstalled())
        val wanted = InstrumentationRegistry.getArguments().getString("conditions")?.split(",")?.map { it.trim() }
        LiteRtLmEngine(manager, File(context.cacheDir, "offline-ai-exp")).use { engine ->
            for (condition in conditions.filter { wanted == null || it.id in wanted }) {
                val rows = JSONArray()
                for (case in EVAL_CASES) {
                    val row = JSONObject().put("id", case.id).put("input", case.input)
                    val start = System.nanoTime()
                    try {
                        val prompt = LocalPrompt.build(ChatProtocol.request(listOf(true to case.input)))
                        row.put("raw", engine.generate(prompt, condition.system, condition.sampler, condition.json) { false })
                    } catch (e: Exception) {
                        row.put("error", e.javaClass.simpleName + ": " + (e.message ?: ""))
                    }
                    row.put("ms", (System.nanoTime() - start) / 1_000_000)
                    rows.put(row)
                }
                File(context.cacheDir, "local-ai-exp-${condition.id}.json").writeText(JSONObject().put("condition", condition.id).put("rows", rows).toString(2))
            }
        }
    }
}
