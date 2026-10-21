package com.hormonelog.app.platform

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import com.hormonelog.core.domain.ChatModel
import java.util.UUID

/** Explicit, one-shot IPC; neither a server port nor a general shell interface is exposed. */
object TermuxChat {
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    private data class Pending(val intent: PendingIntent, val done: (Result<String>) -> Unit)
    private val pending = mutableMapOf<String, Pending>()
    private val handler = Handler(Looper.getMainLooper())

    fun installed(context: Context) = runCatching { context.packageManager.getPackageInfo("com.termux", 0) }.isSuccess
    fun permitted(context: Context) = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun request(context: Context, id: UUID, input: String, model: ChatModel, done: (Result<String>) -> Unit) {
        require(input.toByteArray().size < 128_000) { "요청이 너무 길어요. 입력을 나누어 보내주세요." }
        execute(context, id, "\$PREFIX/bin/hrt-chat", arrayOf(model.modelId), input, 90_000, done)
    }

    /** Only bundled installer scripts use this path; conversation text never reaches a shell. */
    internal fun setup(context: Context, id: UUID, script: String, timeout: Long, done: (Result<String>) -> Unit) {
        require(script.toByteArray().size < 200_000)
        execute(context, id, "\$PREFIX/bin/bash", arrayOf("-s"), script, timeout, done)
    }

    private fun execute(context: Context, id: UUID, path: String, args: Array<String>, input: String, timeout: Long, done: (Result<String>) -> Unit) {
        check(installed(context)) { "Termux를 먼저 설치해 주세요." }
        check(permitted(context)) { "Termux 명령 실행 권한이 필요해요." }
        val key = id.toString()
        val callback = Intent(context, ChatResultReceiver::class.java).setData("hormonelog-chat://result/$key".toUri())
        val pi = PendingIntent.getBroadcast(context, 0, callback,
            PendingIntent.FLAG_ONE_SHOT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        pending[key] = Pending(pi, done)
        val request = Intent("com.termux.RUN_COMMAND").setClassName("com.termux", "com.termux.app.RunCommandService")
            .putExtra("com.termux.RUN_COMMAND_PATH", path)
            .putExtra("com.termux.RUN_COMMAND_ARGUMENTS", args)
            .putExtra("com.termux.RUN_COMMAND_WORKDIR", "~/")
            .putExtra("com.termux.RUN_COMMAND_STDIN", input)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            .putExtra("com.termux.RUN_COMMAND_BACKGROUND_CUSTOM_LOG_LEVEL", "off")
            .putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi)
        try {
            check(context.startService(request) != null) { "Termux를 시작하지 못했어요" }
        } catch (e: Exception) {
            finish(key, Result.failure(IllegalStateException("Termux에 연결하지 못했어요. 외부 앱 실행 설정과 권한을 확인해 주세요.", e)))
            return
        }
        handler.postDelayed({ finish(key, Result.failure(IllegalStateException("응답 대기 시간이 지났어요. Termux·로그인·통신을 확인해 주세요."))) }, timeout)
    }

    fun cancel(id: UUID) { pending.remove(id.toString())?.intent?.cancel() }
    private fun finish(key: String, result: Result<String>) {
        val call = pending.remove(key) ?: return
        call.intent.cancel()
        call.done(result)
    }
    internal fun receive(key: String?, result: Bundle?) {
        if (key == null || key !in pending) return
        val stdout = result?.getString("stdout").orEmpty()
        val ok = result != null && result.getInt("exitCode", -1) == 0 && result.getInt("err", -1) == -1 &&
            result.getInt("stdout_original_length", stdout.length) <= stdout.length && stdout.length <= 60_000
        finish(key, if (ok) Result.success(stdout) else Result.failure(IllegalStateException("Claude 응답을 받지 못했어요. Termux 설정·로그인·사용량 한도를 확인해 주세요.")))
    }
}

class ChatResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TermuxChat.receive(intent.data?.lastPathSegment, intent.getBundleExtra("result"))
    }
}
