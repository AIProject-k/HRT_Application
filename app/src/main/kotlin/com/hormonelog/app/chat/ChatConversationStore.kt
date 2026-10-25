package com.hormonelog.app.chat

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Device-only conversation. No credentials, executable requests or medical-file writes. */
data class ChatConversation(
    val lines: List<ChatLine> = emptyList(),
    val input: String = "",
    val turns: List<Pair<Boolean, String>> = emptyList(),
    val context: List<Pair<Boolean, String>> = emptyList(),
    val times: List<Instant> = emptyList(),
    val anchor: Instant? = null,
    val lastDoseId: String? = null,
    val lastDoseRevision: Int = 0,
    val referenceActive: Boolean = false,
    val pending: ChatCommand? = null,
    val operation: String = "",
)

class ChatConversationStore(private val file: File) {
    private var writable = true
    private var draftTask: ScheduledFuture<*>? = null
    private var queuedDraft: ChatConversation? = null
    var warning: String? = null
        private set

    @Synchronized fun load(): ChatConversation {
        queuedDraft?.let { save(it) }
        if (!file.exists()) return ChatConversation()
        return try {
            require(file.length() <= 8_000_000)
            val o = JSONObject(file.readText())
            require(o.getInt("version") == 1)
            fun turns(key: String) = o.getJSONArray(key).let { a ->
                require(a.length() <= 40)
                (0 until a.length()).map { a.getJSONObject(it).let { v -> v.getBoolean("user") to v.getString("text").also { require(it.length <= 8000) } } }
            }
            val lines = o.getJSONArray("lines").let { a ->
                require(a.length() <= 200)
                (0 until a.length()).map { a.getJSONObject(it).let { v ->
                    ChatLine(v.getBoolean("user"), v.getString("text").also { require(it.length <= 60_000) }, v.getString("id"))
                } }
            }
            require(lines.map { it.id }.distinct().size == lines.size)
            val active = turns("turns")
            val times = o.getJSONArray("times").let { a -> (0 until a.length()).map { Instant.parse(a.getString(it)) } }
            require(times.size == active.count { it.first })
            ChatConversation(lines, o.getString("input"), active, turns("context"), times,
                o.optString("anchor").takeIf { it.isNotBlank() }?.let(Instant::parse),
                o.optString("lastDoseId").takeIf { it.isNotBlank() }, o.optInt("lastDoseRevision"),
                o.optBoolean("referenceActive"),
                o.optJSONObject("pending")?.let { ChatProtocol.decode(JSONObject().put("structured_output", it).toString()) },
                o.optString("operation"))
        } catch (_: Exception) {
            val kept = file.renameTo(File(file.parentFile, "chat-conversation.corrupt-${System.currentTimeMillis()}.json"))
            writable = kept
            warning = if (kept) "대화 파일을 읽지 못해 원본을 별도로 보관했어요. 투약·검사 기록은 그대로예요."
                else "대화 파일을 읽지 못했어요. 원본 보호를 위해 대화를 저장하지 못하고 있어요."
            ChatConversation()
        }
    }

    @Synchronized fun saveDraft(value: ChatConversation) {
        queuedDraft = value
        draftTask?.cancel(false)
        draftTask = drafts.schedule({
            synchronized(this) {
                queuedDraft?.let { draft ->
                    runCatching { save(draft) }.onFailure { warning = "작성 중인 대화를 저장하지 못했어요." }
                }
            }
        }, 250, TimeUnit.MILLISECONDS)
    }

    @Synchronized fun save(value: ChatConversation) {
        draftTask?.cancel(false); draftTask = null; queuedDraft = null
        check(writable) { "Unreadable conversation must not be overwritten" }
        fun turns(values: List<Pair<Boolean, String>>) = JSONArray().apply {
            values.forEach { (user, text) -> put(JSONObject().put("user", user).put("text", text)) }
        }
        val o = JSONObject().put("version", 1).put("input", value.input)
            .put("lines", JSONArray().apply { value.lines.forEach { put(JSONObject().put("user", it.user).put("text", it.text).put("id", it.id)) } })
            .put("turns", turns(value.turns)).put("context", turns(value.context))
            .put("times", JSONArray(value.times.map { it.toString() })).put("anchor", value.anchor?.toString().orEmpty())
            .put("lastDoseId", value.lastDoseId.orEmpty()).put("lastDoseRevision", value.lastDoseRevision)
            .put("referenceActive", value.referenceActive).put("operation", value.operation)
        value.pending?.let { o.put("pending", ChatProtocol.commandJson(it)) }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        try {
            FileOutputStream(tmp).use { out -> out.write(o.toString().toByteArray(Charsets.UTF_8)); out.fd.sync() }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { tmp.delete() }
    }
    companion object {
        private val drafts = Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "chat-draft-storage").apply { isDaemon = true }
        }
    }
}
