package com.hormonelog.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hormonelog.app.ai.*
import com.hormonelog.app.chat.ChatProtocol
import com.hormonelog.app.chat.ChatCommands
import com.hormonelog.app.state.AppState
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class LocalAiDeviceTest {
    @Test fun pinnedModelRunsOnCpuAndDoesNotInventAMissingClockTime() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("localAiModel")=="yes")
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val manager=ModelManager(File(context.noBackupFilesDir,"offline-ai"))
        assertTrue("Pinned model must be provisioned and verified",manager.isInstalled())
        val results=JSONArray()
        LiteRtLmEngine(manager,File(context.cacheDir,"offline-ai-test")).use { engine ->
            for (input in listOf("방금 안쿨 25mg 먹었어", "어제 저녁 먹고 유트로게스탄 100mg 복용했어")) {
                val start=System.nanoTime()
                val raw=engine.generate(LocalPrompt.build(ChatProtocol.request(listOf(true to input)))) { false }
                results.put(JSONObject().put("input",input).put("raw",raw).put("elapsedMs",(System.nanoTime()-start)/1_000_000))
                File(context.cacheDir,"local-ai-smoke.json").writeText(results.toString(2))
                val command=LlmResponseParser.parse(raw)
                assertEquals("dose",command.action)
                val decision=ChatCommands.apply(AppState(),command,listOf(input),UUID.randomUUID(),Instant.now(),ZoneId.of("Asia/Seoul"))
                if(input.startsWith("방금")) {
                    assertNotNull("Complete literal administration must pass validation: ${decision.message}",decision.next)
                } else {
                    assertTrue("Missing exact time must stay missing or literal",command.time.isBlank() || input.contains(command.time))
                    assertNull(decision.next)
                }
            }
        }
    }
}
