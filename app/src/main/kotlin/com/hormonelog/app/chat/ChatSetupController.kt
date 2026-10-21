package com.hormonelog.app.chat

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import com.hormonelog.app.platform.TermuxChat
import kotlinx.coroutines.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ChatSetupStage { INTRO, DOWNLOADING, APK, PERMISSION, BOOTSTRAP, INSTALLING, LOGIN, VERIFYING, READY, ERROR }
data class ChatSetupUi(val visible: Boolean, val stage: ChatSetupStage = ChatSetupStage.INTRO,
    val message: String = "", val busy: Boolean = false, val loginUrl: String? = null)

/** Device-specific installation state is deliberately excluded from medical backups. */
class ChatSetupController(private val app: Application, private val scope: CoroutineScope, private val chat: ChatSession) {
    private val prefs = app.getSharedPreferences("chat_setup", Context.MODE_PRIVATE)
    private val assets by lazy { ChatSetupAssets(app) }
    private val apk = File(app.cacheDir, "chat-installer/termux.apk")
    private var task: Job? = null
    private var poll: Job? = null
    private var refreshing = false
    private var continueAfterRefresh = false
    private var apkPrompt = false
    private var openedUrl: String? = null
    private var foreground = false
    private var lastCodeHash: String? = null
    var ui by mutableStateOf(ChatSetupUi(!prefs.getBoolean("introSeen", false) || prefs.getBoolean("pending", false)))
        private set

    fun open() {
        if (chat.ui.busy || chat.ui.saveFailed) return
        ui = ui.copy(visible = true)
    }
    fun skip() {
        val cancelLogin = ui.stage == ChatSetupStage.LOGIN
        prefs.edit { putBoolean("introSeen", true); putBoolean("pending", false); putBoolean("enabled", false) }
        task?.cancel(); poll?.cancel(); openedUrl = null
        continueAfterRefresh = false
        chat.disconnect()
        ui = ChatSetupUi(false)
        if (cancelLogin && TermuxChat.installed(app) && TermuxChat.permitted(app)) scope.launch {
            runCatching { command(assets.command("cancel_login"), 25_000) }
        }
    }
    fun done() { ui = ui.copy(visible = false, loginUrl = null) }
    fun close() { if (prefs.getBoolean("enabled", false)) done() else skip() }
    private fun fail(message: String, stage: ChatSetupStage = ChatSetupStage.ERROR) {
        prefs.edit { putBoolean("pending", false) }
        poll?.cancel()
        chat.disconnect()
        ui = ui.copy(stage = stage, busy = false, message = message, loginUrl = null)
    }
    private fun launch(intent: Intent) = app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    fun openAppSettings() = launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${app.packageName}".toUri()))

    fun start() {
        if (ui.busy) return
        prefs.edit { putBoolean("introSeen", true); putBoolean("pending", true) }
        chat.disconnect()
        if (!TermuxChat.installed(app)) { downloadTermux(); return }
        if (!TermuxChat.permitted(app)) { ui = ui.copy(stage = ChatSetupStage.PERMISSION, message = "Termux 명령 실행 권한을 허용해 주세요."); return }
        refresh(true)
    }
    fun permissionResult(granted: Boolean) {
        if (granted) refresh(true) else fail("명령 실행 권한이 없어 Claude 채팅을 켜지 않았어요. 앱 권한에서 허용한 뒤 다시 시작해 주세요.")
    }
    fun resume() {
        foreground = true
        scope.launch {
            delay(400)
            if (foreground && ui.stage == ChatSetupStage.LOGIN && prefs.getBoolean("pending", false)) {
                val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = clipboard.primaryClip?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.text?.toString()
                val code = text?.let { ChatSetupReport.matchingCode(it, ui.loginUrl) }
                if (code != null) {
                    val hash = MessageDigest.getInstance("SHA-256").digest(code.toByteArray()).joinToString("") { "%02x".format(it) }
                    if (hash != lastCodeHash) { lastCodeHash = hash; submitCode(code) }
                }
            }
        }
        if (TermuxChat.installed(app) && prefs.getBoolean("pending", false) && ui.stage == ChatSetupStage.APK) { start(); return }
        if (ui.stage == ChatSetupStage.APK && !apkPrompt && app.packageManager.canRequestPackageInstalls()) { installApk(); return }
        if (TermuxChat.installed(app) && TermuxChat.permitted(app) &&
            (prefs.getBoolean("enabled", false) || prefs.getBoolean("pending", false)) && !ui.busy && !chat.ui.busy) refresh(ui.visible && prefs.getBoolean("pending", false))
        else if (!TermuxChat.installed(app) || !TermuxChat.permitted(app)) chat.disconnect()
    }
    fun pause() { foreground = false }

    fun prepareTermux() {
        prefs.edit { putBoolean("pending", true) }
        (app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("호르몬로그 연결 준비", ChatSetupAssets.bootstrap))
        val intent = app.packageManager.getLaunchIntentForPackage("com.termux")
        if (intent == null) fail("Termux를 먼저 설치해 주세요.") else {
            ui = ui.copy(message = "준비 명령을 복사했어요. Termux 초기화가 끝나면 입력창을 길게 눌러 붙여넣고 Enter를 누르세요. 완료되면 앱으로 돌아와요.")
            launch(intent)
        }
    }

    private fun packageInfo() = termuxRelease(Build.SUPPORTED_ABIS.toList())
    private fun digest(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; md.update(buffer, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    private fun downloadTermux() {
        val info = packageInfo() ?: run { fail("이 기기는 자동 설치가 지원되는 64비트 ARM/x86 환경이 아니에요. 기록 기능은 계속 사용할 수 있어요."); return }
        ui = ui.copy(stage = ChatSetupStage.DOWNLOADING, busy = true, message = "공식 Termux 0.118.3을 내려받고 있어요…")
        task = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    apk.parentFile?.mkdirs()
                    if (!apk.isFile || digest(apk) != info.second) {
                        val tmp = File(apk.parentFile, "termux.part")
                        val url = "https://github.com/termux/termux-app/releases/download/v0.118.3/termux-app_v0.118.3+github-debug_${info.first}.apk"
                        val connection = URL(url).openConnection() as HttpURLConnection
                        connection.connectTimeout = 30_000; connection.readTimeout = 30_000
                        try {
                            connection.inputStream.use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
                            check(connection.url.protocol == "https" && digest(tmp) == info.second)
                            check(tmp.renameTo(apk))
                        } finally { connection.disconnect(); tmp.delete() }
                    }
                }
                ui = ui.copy(stage = ChatSetupStage.APK, busy = false, message = "파일 검증을 마쳤어요. Android 설치 승인을 진행해 주세요.")
                installApk()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { fail("Termux 다운로드·파일 검증에 실패했어요. 네트워크와 저장 공간을 확인하고 다시 시도해 주세요.") }
        }
    }
    fun installApk() {
        if (!apk.isFile) { start(); return }
        if (!app.packageManager.canRequestPackageInstalls()) {
            apkPrompt = false
            launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${app.packageName}".toUri()))
            return
        }
        try {
            check(digest(apk) == packageInfo()?.second)
            apkPrompt = true
            launch(Intent(Intent.ACTION_VIEW).setDataAndType(FileProvider.getUriForFile(app, "${app.packageName}.files", apk), "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) { fail("Termux 설치 창을 열지 못했어요. 설치 권한을 확인하고 다시 시도해 주세요.") }
    }

    private suspend fun command(script: String, timeout: Long = 70_000): String = suspendCancellableCoroutine { continuation ->
        val id = UUID.randomUUID()
        continuation.invokeOnCancellation { TermuxChat.cancel(id) }
        try {
            TermuxChat.setup(app, id, script, timeout) { result ->
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
    }
    private fun refresh(interactive: Boolean) {
        continueAfterRefresh = continueAfterRefresh || interactive
        if (refreshing) return
        refreshing = true
        scope.launch {
            try {
                val report = ChatSetupReport.parse(command(assets.command("probe")))
                val proceed = continueAfterRefresh
                continueAfterRefresh = false
                if (ui.visible || prefs.getBoolean("enabled", false)) apply(report, proceed)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (ui.visible) fail("Termux의 최초 연결 준비가 필요해요. 준비 명령을 한 번 실행해 주세요.", ChatSetupStage.BOOTSTRAP)
                else chat.disconnect()
            } finally { refreshing = false; continueAfterRefresh = false }
        }
    }
    private fun apply(report: ChatSetupReport, interactive: Boolean) {
        if (interactive && !foreground) { ui = ui.copy(busy = false); return }
        if (!report.installed(assets.id)) {
            chat.disconnect()
            if (report.running || task?.isActive == true) {
                ui = ui.copy(stage = ChatSetupStage.INSTALLING, busy = true, message = phase(report.phase))
                if (poll?.isActive != true) watch()
                return
            }
            if (interactive) installEnvironment() else ui = ui.copy(stage = ChatSetupStage.INTRO)
            return
        }
        if (!report.subscription) {
            chat.disconnect()
            ui = ui.copy(stage = ChatSetupStage.LOGIN, busy = false, loginUrl = report.loginUrl, message = "구독 계정 로그인·연결 승인이 필요해요.")
            if (report.loginUrl != null && ui.visible && foreground) openLogin(report.loginUrl)
            else if (interactive && task?.isActive != true) login()
            return
        }
        if (prefs.getString("verifiedId", "") == assets.id) ready()
        else if (interactive && task?.isActive != true) verify()
        else if (task?.isActive == true) return
        else ui = ui.copy(stage = ChatSetupStage.INTRO, busy = false, message = "설치·로그인을 찾았어요. 연결 확인을 눌러 완료해 주세요.")
    }
    private fun phase(value: String) = when (value) {
        "ubuntu" -> "Ubuntu 24.04를 설치·준비하고 있어요…"
        "claude" -> "Claude Code 2.1.292를 설치하고 있어요…"
        "bridge" -> "앱 연결 프로그램을 배치하고 있어요…"
        else -> "Termux → Ubuntu 24.04 → Claude Code 2.1.292 설치를 진행하고 있어요…"
    }
    private fun watch() {
        poll?.cancel()
        poll = scope.launch { while (isActive && prefs.getBoolean("pending", false)) { delay(3000); if (foreground) refresh(true) } }
    }
    private fun installEnvironment() {
        ui = ui.copy(stage = ChatSetupStage.INSTALLING, busy = true, message = phase(""))
        task = scope.launch {
            try {
                val report = ChatSetupReport.parse(command(assets.install(), 30 * 60_000))
                if (report.error.isNotBlank() || !report.installed(assets.id)) fail("설치가 완료되지 않았어요 (${report.error.ifBlank { "환경 확인" }}). Termux를 열어 진행 상태를 확인하거나 다시 시도해 주세요.")
                else { task = null; apply(report, true) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { fail("설치 응답을 받지 못했어요. Termux에서 설치가 계속 중일 수 있어요. 상태 확인 후 다시 시도해 주세요.") }
        }
        watch()
    }
    fun login() {
        if (task?.isActive == true) return
        ui = ui.copy(stage = ChatSetupStage.LOGIN, busy = false, message = "공식 구독 로그인 화면을 준비하고 있어요…")
        task = scope.launch {
            try {
                val report = ChatSetupReport.parse(command(assets.command("login"), 12 * 60_000))
                task = null
                if (report.subscription) apply(report, true) else fail("로그인이 완료되지 않았어요. 공식 화면에서 승인한 뒤 다시 확인해 주세요.")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { fail("로그인 응답을 받지 못했어요. 다시 로그인해 주세요.") }
        }
        watch()
    }
    fun openLogin(url: String? = ui.loginUrl) {
        val safe = url?.let(ChatSetupReport::officialLoginUrl) ?: return
        if (openedUrl == safe) return
        openedUrl = safe
        val intent = Intent(Intent.ACTION_VIEW, safe.toUri())
        try { launch(Intent(intent).setPackage("com.android.chrome")) }
        catch (_: Exception) { runCatching { launch(intent) }.onFailure { openedUrl = null; fail("공식 로그인 페이지를 열 브라우저가 없어요.") } }
    }
    fun reopenLogin() { openedUrl = null; openLogin() }
    fun submitCode(code: String) {
        val value = code.trim()
        if (value.length !in 1..4096 || value.any { it == '\n' || it == '\r' || it == '\u0000' }) { ui = ui.copy(message = "표시된 인증 코드 한 줄만 붙여넣어 주세요."); return }
        scope.launch {
            try { command(assets.command("code", value)); ui = ui.copy(message = "코드를 전달했어요. 로그인 완료를 확인하고 있어요…") }
            catch (_: Exception) { ui = ui.copy(message = "코드를 전달하지 못했어요. 로그인 화면을 다시 열어주세요.") }
        }
    }
    private fun verify() {
        ui = ui.copy(stage = ChatSetupStage.VERIFYING, busy = true, message = "실행·구독 연결을 확인하고 있어요…")
        task = scope.launch {
            try {
                val result = org.json.JSONObject(command(assets.command("verify"), 120_000))
                if (result.optString("error") == "USAGE_LIMIT") {
                    fail("구독 세션의 사용량 한도로 연결 확인을 마치지 못했어요. 채팅은 꺼져 있어요. 한도가 초기화된 뒤 다시 시도해 주세요.")
                    return@launch
                }
                check(result.optBoolean("verified"))
                prefs.edit { putString("verifiedId", assets.id) }
                ready()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { fail("구독 연결 확인에 실패했어요. 로그인·네트워크·사용량 한도를 확인하고 다시 시도해 주세요.") }
        }
    }
    private fun ready() {
        prefs.edit { putBoolean("introSeen", true); putBoolean("pending", false); putBoolean("enabled", true) }
        poll?.cancel()
        chat.connect()
        ui = ui.copy(stage = ChatSetupStage.READY, busy = false, loginUrl = null, message = "Ubuntu 24.04 · Claude Code 2.1.292 · 구독 연결 확인 완료")
    }
    companion object {
        fun termuxRelease(abis: List<String>): Pair<String, String>? = when (abis.firstOrNull { it in setOf("arm64-v8a", "x86_64") }) {
            "arm64-v8a" -> "arm64-v8a" to "72fdb596045116bf5ba1b5bdf5b26fddb9acc0bd074ad9f2da9eb0ae85e83a4e"
            "x86_64" -> "x86_64" to "3550e61f4d9eb49b712fd1bd9519dc37085a4d8eb597c57a340f0a64859b7144"
            else -> null
        }
    }
}
