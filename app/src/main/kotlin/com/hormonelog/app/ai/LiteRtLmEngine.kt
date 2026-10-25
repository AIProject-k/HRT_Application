package com.hormonelog.app.ai

import com.google.ai.edge.litertlm.*
import java.io.File
import java.util.concurrent.CancellationException

/** All creation/inference/close calls are serialized by the owning worker. Cancel may run concurrently. */
class LiteRtLmEngine(private val manager: ModelManager, private val cache: File, private val defaultSystem: String = LocalPrompt.SYSTEM) : LlmEngine {
    private var engine: Engine?=null
    @Volatile private var conversation: Conversation?=null

    override fun generate(prompt: String, cancelled: ()->Boolean): String =
        generate(prompt, defaultSystem, SamplerConfig(1,1.0,0.0), json = false, cancelled = cancelled)

    /** One experiment condition at a time. The product path always uses the defaults above. */
    fun generate(prompt: String, system: String, sampler: SamplerConfig, json: Boolean, cancelled: ()->Boolean): String {
        fun checkCancelled() { if(cancelled()) throw CancellationException() }
        checkCancelled()
        var runtime=engine
        if(runtime==null) {
            check(manager.isInstalled()) { "오프라인 AI 모델을 먼저 설치해 주세요." }
            checkCancelled()
            cache.mkdirs()
            val created=Engine(EngineConfig(manager.model.absolutePath,backend=Backend.CPU(),maxNumTokens=4096,cacheDir=cache.absolutePath))
            try { created.initialize() } catch(t: Throwable) { if(created.isInitialized()) created.close(); throw LocalAiException(LocalAiStage.LOAD, t) }
            engine=created; runtime=created
        }
        checkCancelled()
        return runtime.createConversation(ConversationConfig(
            systemInstruction=Contents.of(system), samplerConfig=sampler,
            automaticToolCalling=false,maxOutputToken=768,thinkingConfig=ThinkingConfig(enableThinking=false),
            extraContext=mapOf("enable_thinking" to false), enableResponseFormat=json,
        )).use { current ->
            conversation=current
            try {
                checkCancelled()
                val response=try {
                    if (json) current.sendMessage(prompt,extraContext=mapOf("enable_thinking" to false),maxOutputToken=768,responseFormat=ResponseFormat.json(LocalPrompt.JSON_SCHEMA))
                    else current.sendMessage(prompt,extraContext=mapOf("enable_thinking" to false),maxOutputToken=768)
                } catch(e: CancellationException) { throw e } catch(t: Throwable) { throw LocalAiException(LocalAiStage.INFERENCE, t) }
                checkCancelled()
                require(response.toolCalls.isEmpty()) { "AI 도구 요청은 실행하지 않아요." }
                response.toString()
            } finally { conversation=null }
        }
    }

    override fun cancel() { conversation?.let { runCatching { it.cancelProcess() } } }
    override fun close() { engine?.let { if(it.isInitialized()) it.close() }; engine=null }
}
