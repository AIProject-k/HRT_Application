package com.hormonelog.app.chat

import com.hormonelog.app.state.AppState
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Drives the chat the way the phone does: rules first, the model only when the rules return nothing.
 * Records go through the real ChatSession, validator and reducers. The model is a stub that counts its calls.
 */
internal class ChatHarness(
    start: String = "2026-10-24T14:00:00Z",
    private val model: (String) -> Result<String> = { Result.failure(IllegalStateException("MODEL_NOT_RUN")) },
) {
    var state = AppState()
    var now: Instant = Instant.parse(start)
    var modelCalls = 0
        private set
    /** Each save applies the next state first, as the app does, then reports failure for the next N saves. */
    var failNextSaves = 0
    /** What is on disk: only a save that reported success changes it. The in-memory [state] moves on before that. */
    var disk = AppState()
    /** When set, save callbacks are held in [deferred] until the test runs them, so late callbacks can be interleaved. */
    var deferSaves = false
    val deferred = mutableListOf<() -> Unit>()
    val chat = ChatSession(
        state = { state },
        save = { next, done ->
            state = next
            val ok = if (failNextSaves > 0) { failNextSaves--; false } else true
            if (ok) disk = next
            if (deferSaves) deferred += { done(ok) } else done(ok)
        },
        request = { _, payload, done -> route(payload, done) },
        cancelRequest = {},
        clock = { now },
        allowOfflineInput = true,
        zoneOf = { SEOUL },
    )

    private fun route(payload: String, done: (Result<String>) -> Unit) {
        RuleBasedParser.parse(payload, state)?.let { rule ->
            done(Result.success(JSONObject().put("structured_output", ChatProtocol.commandJson(rule)).toString()))
            return
        }
        modelCalls++
        done(model(payload))
    }

    fun send(text: String, afterSeconds: Long = 0): ChatHarness {
        now = now.plusSeconds(afterSeconds)
        chat.send(text)
        return this
    }

    val doses get() = state.doses
    val lastReply: String get() = chat.ui.lines.last { !it.user }.text
    fun doseTimes(): List<String> = doses.map { kst(it.occurredAt) }.sorted()

    companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        private val FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun kst(at: Instant): String = FORMAT.format(at.atZone(SEOUL))

        fun structured(command: ChatCommand): Result<String> =
            Result.success(JSONObject().put("structured_output", ChatProtocol.commandJson(command)).toString())

        fun dose(drug: String, amount: String, time: String, route: String = "") =
            ChatCommand("dose", drug = drug, route = route, amount = amount, amountUnit = "mg", time = time)

        fun batch(date: String, vararg doses: ChatCommand) = ChatCommand("batch", date = date, doses = doses.toList())
    }
}
