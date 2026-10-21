package com.hormonelog.app.chat

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hormonelog.app.ai.*
import com.hormonelog.app.state.AppState
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/** Keeps existing ChatSession transactions; replaces the external provider and installer only. */
class LocalChatController(private val app: Application, private val scope: CoroutineScope, private val state: ()->AppState) {
    private val worker=Executors.newSingleThreadExecutor()
    private val dispatcher=worker.asCoroutineDispatcher()
    private val manager=ModelManager(File(app.noBackupFilesDir,"offline-ai"))
    private val downloader=ModelDownloader(manager)
    private val engine: LlmEngine=LiteRtLmEngine(manager,File(app.cacheDir,"offline-ai"))
    private var task: Job?=null
    private var timeout: Job?=null
    private var install: Job?=null
    @Volatile private var current: UUID?=null
    private var checked=false
    @Volatile private var closed=false
    var ui by mutableStateOf(ChatSetupUi(false))
        private set
    lateinit var chat: ChatSession

    fun request(id: UUID,input: String,done: (Result<String>)->Unit) {
        check(current==null && !ui.busy && !closed) { "AI 작업을 마친 뒤 다시 보내주세요." }
        RuleBasedParser.parse(input,state())?.let {
            done(Result.success(JSONObject().put("structured_output",ChatProtocol.commandJson(it)).toString())); return
        }
        if(!chat.ui.connected) {
            done(Result.failure(IllegalStateException("이 문장은 오프라인 AI 모델이 필요해요. 모델을 설치하거나 제품·용량·날짜·시각을 나누어 입력해 주세요."))); return
        }
        val prompt=LocalPrompt.build(input)
        current=id
        task=scope.launch {
            val result=try {
                val text=withContext(dispatcher) { engine.generate(prompt) { current!=id || closed } }
                val command=LlmResponseParser.parse(text)
                Result.success(JSONObject().put("structured_output",ChatProtocol.commandJson(command)).toString())
            } catch(e: CancellationException) { return@launch }
            catch(t: Throwable) { Result.failure(IllegalStateException("기기 내 AI 처리에 실패했어요. 기록은 저장하지 않았어요. 모델 상태와 입력 길이를 확인해 주세요.",t)) }
            if(current==id) { current=null; timeout?.cancel(); done(result) }
        }
        timeout=scope.launch {
            delay(120_000)
            if(current==id) { cancel(id); done(Result.failure(IllegalStateException("AI 응답 시간이 초과됐어요. 기록은 저장하지 않았어요."))) }
        }
    }
    fun cancel(id: UUID) {
        if(current!=id) return
        current=null; engine.cancel(); task?.cancel(); timeout?.cancel()
    }
    fun open() { if(!chat.ui.busy && !chat.ui.saveFailed) ui=ui.copy(visible=true) }
    fun close() { ui=ui.copy(visible=false) }
    fun done() = close()
    fun skip() = close()
    fun resume() {
        if(checked || ui.busy || closed) return
        checked=true
        install=scope.launch {
            val installed=withContext(Dispatchers.IO) { runCatching { manager.isInstalled() }.getOrDefault(false) }
            if(closed) return@launch
            if(installed) { chat.connect(); ui=ui.copy(stage=ChatSetupStage.READY,message="Qwen3-0.6B · 기기 내 CPU 실행") }
        }
    }
    fun pause() {
        chat.suspendRequest()
        if(!closed) worker.execute { engine.close() }
    }
    fun start() {
        if(ui.busy || chat.ui.busy || chat.ui.saveFailed || closed) return
        ui=ui.copy(stage=ChatSetupStage.DOWNLOADING,busy=true,message="모델을 다운로드·검증하고 있어요…")
        chat.disconnect()
        downloader.reset()
        install=scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if(!manager.isInstalled()) downloader.download { count -> scope.launch {
                        if(ui.busy) ui=ui.copy(message="모델 다운로드 ${count/1024/1024} / 328 MiB")
                    } }
                }
                chat.connect()
                ui=ui.copy(stage=ChatSetupStage.READY,busy=false,message="모델 검증 완료 · 첫 메시지에서 AI를 불러와요")
            } catch(e: CancellationException) { throw e }
            catch(t: Throwable) { ui=ui.copy(stage=ChatSetupStage.ERROR,busy=false,message=t.message ?: "모델 설치에 실패했어요. 다시 시도해 주세요.") }
        }
    }
    fun cancelInstall() {
        if(!ui.busy) return
        downloader.cancel()
        install?.cancel()
        ui=ui.copy(message="설치를 취소하고 있어요…")
        install?.invokeOnCompletion { scope.launch {
            ui=ui.copy(stage=ChatSetupStage.INTRO,busy=false,message="설치를 취소했어요. 다시 누르면 받은 부분부터 이어받아요.")
        } }
    }
    fun deleteModel() {
        if(ui.busy || chat.ui.busy || chat.ui.saveFailed) return
        ui=ui.copy(busy=true,message="AI 모델을 삭제하고 있어요…")
        chat.disconnect()
        install=scope.launch {
            try {
                withContext(dispatcher) { engine.close(); manager.delete() }
                ui=ui.copy(stage=ChatSetupStage.INTRO,busy=false,message="AI 모델을 삭제했어요. 투약·검사·대화 기록은 유지돼요.")
            } catch(t: Exception) { ui=ui.copy(stage=ChatSetupStage.ERROR,busy=false,message=t.message.orEmpty()) }
        }
    }
    fun dispose() {
        closed=true; current?.let(::cancel); downloader.cancel(); install?.cancel()
        engine.cancel(); worker.execute { engine.close() }; worker.shutdown()
    }
}
