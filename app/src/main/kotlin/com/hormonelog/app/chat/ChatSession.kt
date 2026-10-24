package com.hormonelog.app.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.JournalOps
import com.hormonelog.app.state.RecordsReducer
import com.hormonelog.app.state.UndoSnapshot
import com.hormonelog.core.data.RecordSnapshot
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class ChatLine(val user: Boolean, val text: String, val id: String = UUID.randomUUID().toString())
data class ChatUi(
    val connected: Boolean = false, val lines: List<ChatLine> = emptyList(),
    val busy: Boolean = false, val saving: Boolean = false, val saveFailed: Boolean = false,
    val canUndo: Boolean = false,
    val input: String = "", val conversationWarning: String? = null,
)

/** Durable conversation, with pending intent separate from completed actions and local query results. */
class ChatSession(
    private val state: () -> AppState,
    private val save: (AppState, (Boolean) -> Unit) -> Unit,
    private val request: (UUID, String, (Result<String>) -> Unit) -> Unit,
    private val cancelRequest: (UUID) -> Unit,
    private val clock: () -> Instant = Instant::now,
    private val conversationStore: ChatConversationStore? = null,
    private val allowOfflineInput: Boolean = false,
    private val zoneOf: () -> ZoneId = { ZoneId.systemDefault() },
) {
    var ui by mutableStateOf(ChatUi())
        private set
    private var turns = emptyList<Pair<Boolean, String>>()
    private var contextTurns = emptyList<Pair<Boolean, String>>()
    private var inFlight: UUID? = null
    private var anchor: Instant? = null
    private var inputTimes = emptyList<Instant>()
    private data class Receipt(val before: UndoSnapshot, val after: RecordSnapshot, val text: String, val undoing: Boolean = false, val includeContext: Boolean = true)
    private var receipt: Receipt? = null
    private var receiptAt: Instant? = null
    private var awaitingSave: Receipt? = null
    private var lastDoseId: String? = null
    private var lastDoseRevision = 0
    private var referenceActive = false
    private var pendingCommand: ChatCommand? = null

    init {
        conversationStore?.load()?.let { saved ->
            ui = ui.copy(lines = saved.lines, input = saved.input, conversationWarning = conversationStore.warning)
            turns = saved.turns; contextTurns = saved.context; inputTimes = saved.times; anchor = saved.anchor
            lastDoseId = saved.lastDoseId; lastDoseRevision = saved.lastDoseRevision
            referenceActive = saved.referenceActive; pendingCommand = saved.pending
            if (saved.operation == "saving") {
                turns = emptyList(); inputTimes = emptyList(); anchor = null; pendingCommand = null; referenceActive = false
                say("저장 중 앱이 종료됐어요. 중복 등록을 막기 위해 자동으로 다시 저장하지 않았어요. 최근 기록을 확인해 주세요.")
            } else if (saved.operation == "request") {
                say("응답을 기다리던 대화가 남아 있어요. 내용을 이어서 보내면 계속 확인할게요.")
            }
        }
    }

    private fun checkpoint(draftOnly: Boolean = false) {
        val store = conversationStore ?: return
        try {
            val snapshot = ChatConversation(ui.lines, ui.input, turns, contextTurns, inputTimes, anchor,
                lastDoseId, lastDoseRevision, referenceActive, pendingCommand,
                if (ui.saving || ui.saveFailed) "saving" else if (inFlight != null) "request" else "")
            if (draftOnly) store.saveDraft(snapshot) else store.save(snapshot)
            if (store.warning == null && ui.conversationWarning != null) ui = ui.copy(conversationWarning = null)
        } catch (_: Exception) {
            ui = ui.copy(conversationWarning = "대화를 기기에 저장하지 못했어요. 저장 공간을 확인해 주세요.")
        }
    }
    fun editInput(value: String) { ui = ui.copy(input = value.take(8000)); checkpoint(draftOnly = true) }
    fun submit() {
        val text = ui.input
        if (!canSend(text)) return
        ui = ui.copy(input = "")
        send(text)
    }
    fun flush() = checkpoint()
    fun suspendRequest() { inFlight?.let(cancelRequest); inFlight = null; ui = ui.copy(busy = ui.saving); checkpoint() }

    fun connect() { ui = ui.copy(connected = true) }
    fun disconnect() { suspendRequest(); ui = ui.copy(connected = false) }
    val memoryCount: Int get() = state().chatMemories.size
    private fun localShortcut(text: String): ChatShortcut? {
        val shortcut = ChatShortcut.find(text) ?: return null
        return shortcut.takeUnless { state().chatMemories.any { m -> m.name.filterNot { it.isWhitespace() } == text.filterNot { it.isWhitespace() } } }
    }
    fun canSend(text: String) = !ui.busy && !ui.saveFailed && text.isNotBlank() && (allowOfflineInput || ui.connected || localShortcut(text) != null)
    fun runShortcut(shortcut: ChatShortcut) = showLocal(shortcut.action)
    fun showStatus() = showLocal("status")
    fun showLabs() = showLocal("lab_results")
    private fun showLocal(action: String) {
        if (ui.busy || ui.saveFailed) return
        say(ChatCommands.apply(state(), ChatCommand(action), emptyList(), UUID.randomUUID(), clock(), zoneOf()).message)
    }
    fun listMemories() {
        if (ui.busy || ui.saveFailed) return
        say(ChatMemoryActions.list(state()))
    }
    private fun previousDoseAt(now: Instant): Instant? {
        val r = receipt ?: return null
        val saved = receiptAt ?: return null
        if (now.epochSecond - saved.epochSecond !in 0..600 || state().snapshot != r.after) return null
        val oldIds = r.before.doses.map { it.id }.toSet()
        return r.after.doses.filter { it.id !in oldIds }.map { it.occurredAt }.distinct().singleOrNull()
    }
    private fun say(text: String) { ui = ui.copy(lines = (ui.lines + ChatLine(false, text)).takeLast(200)); checkpoint() }
    private fun finishTurns(summary: String, includeTurns: Boolean = true) {
        contextTurns = (contextTurns + (if (includeTurns) turns else emptyList()) + (false to summary)).takeLast(20)
        turns = emptyList(); inputTimes = emptyList(); anchor = null
        pendingCommand = null; referenceActive = false
    }
    private fun dropOldestTurn() {
        if (turns.firstOrNull()?.first == true) inputTimes = inputTimes.drop(1)
        turns = turns.drop(1)
    }

    fun send(text: String) {
        if (!canSend(text)) return
        localShortcut(text)?.let {
            ui = ui.copy(lines = (ui.lines + ChatLine(true, text.trim())).takeLast(200))
            runShortcut(it)
            return
        }
        if (text.length > 8000) {
            say("한 번에 8,000자 이하로 입력해 주세요. 밀린 기록은 투약 50건까지 함께 보낼 수 있어요."); return
        }
        val now = clock()
        val zone = zoneOf()
        if (anchor != null && anchor!!.atZone(zone).toLocalDate() != now.atZone(zone).toLocalDate()) {
            finishTurns("날짜가 바뀌어 이전 미완료 요청은 종료했어요. 새 입력의 날짜·시각을 기준으로 확인해 주세요.")
        }
        pendingCommand?.takeIf { ChatCommands.switchesDrug(state(), it, text) }?.let {
            finishTurns("이전 미완료 요청은 종료했어요. 새 약 기록으로 확인해 주세요.", includeTurns = false)
        }
        if (anchor == null) { anchor = now; inputTimes = emptyList() }
        if (ChatDoseReference.requested(text)) referenceActive = true
        turns = turns + (true to text.trim())
        inputTimes = inputTimes + now
        while (turns.size > 20) dropOldestTurn()
        val id = UUID.randomUUID()
        val previous = previousDoseAt(now)
        inFlight = id
        ui = ui.copy(lines = (ui.lines + ChatLine(true, text.trim())).takeLast(200), busy = true)
        val referenceDose = if (referenceActive) ChatDoseReference.find(state(), lastDoseId, lastDoseRevision) else null
        fun payload() = ChatProtocol.request(turns, previousDoseTimeAvailable = previous != null, memories = state().chatMemories,
            context = contextTurns, pending = pendingCommand, referenceAvailable = referenceDose != null)
        var input = payload()
        while (input.toByteArray().size >= 120_000 && turns.size > 1) { dropOldestTurn(); input = payload() }
        checkpoint()
        try {
            request(id, input) callback@{ response ->
                if (inFlight != id) return@callback
                inFlight = null
                ui = ui.copy(busy = false)
                response.fold(onSuccess = { raw ->
                    val decoded = runCatching { ChatProtocol.decode(raw) }.getOrElse {
                        say("응답 형식을 확인하지 못했어요. 기록은 실행하지 않았어요. 다시 요청해 주세요."); return@callback
                    }
                    val before = state()
                    val reference = referenceDose?.takeIf { ChatDoseReference.find(before, lastDoseId, lastDoseRevision) == it }
                    val command = if (decoded.action == "dose" && reference != null) ChatDoseReference.apply(decoded, reference) else decoded
                    val inputs = KoreanTime.normalizeInputs(turns.filter { it.first }.map { it.second }) + if (decoded.action == "dose" && reference != null) listOf(ChatDoseReference.evidence(reference)) else emptyList()
                    val timeToken = command.time.ifBlank { command.doses.map { it.time }.filter { it.isNotBlank() }.distinct().singleOrNull().orEmpty() }.filterNot { it.isWhitespace() }
                    val timeIndex = inputs.indexOfLast { timeToken.isNotBlank() && it.filterNot { c -> c.isWhitespace() }.contains(timeToken) }
                    val referenceTime = inputTimes.getOrNull(timeIndex) ?: now
                    val decision = if (command.action in ChatMemoryActions.actions) ChatMemoryActions.apply(before, command, inputs, id, referenceTime, zone)
                        else ChatCommands.apply(before, command, inputs, id, referenceTime, zone,
                            previousDoseAt = previous.takeIf { receipt?.after == before.snapshot })
                    val next = decision.next
                    if (next == null) {
                        if (command.action in ChatCommands.readActions || command.action == "memory_list") {
                            // Remove only this query; keep an unfinished record and its timestamps.
                            turns = turns.dropLast(1); inputTimes = inputTimes.dropLast(1)
                            if (turns.isEmpty()) anchor = null
                        }
                        else if (decision.closes) finishTurns(decision.message, includeTurns = false)
                        else {
                            if (command.action != "ask") pendingCommand = decoded
                            turns = turns + (false to decision.message)
                            if (turns.size > 40) dropOldestTurn()
                        }
                        say(decision.message)
                    } else {
                        awaitingSave = Receipt(RecordsReducer.undoPoint(before), next.snapshot, decision.message, includeContext = command.action !in ChatMemoryActions.actions)
                        ui = ui.copy(busy = true, saving = true, canUndo = false)
                        checkpoint()
                        save(next, ::saved)
                    }
                }, onFailure = { say(it.message ?: "연결에 실패했어요. 기록은 실행하지 않았어요.") })
            }
        } catch (e: Exception) {
            inFlight = null
            ui = ui.copy(busy = false)
            say(e.message ?: "연결 설정을 확인해 주세요.")
        }
    }

    private fun saved(ok: Boolean) {
        val pending = awaitingSave ?: return
        ui = ui.copy(busy = false, saving = false, saveFailed = !ok)
        if (!ok) { say("파일 저장에 실패했어요. 앱을 닫기 전에 ‘저장 재시도’를 눌러주세요."); return }
        receipt = pending.takeUnless { it.undoing }
        receiptAt = clock().takeUnless { pending.undoing }
        awaitingSave = null
        val previousIds = pending.before.doses.map { it.id }.toSet()
        val added = pending.after.doses.filter { it.id !in previousIds }.singleOrNull()
        if (added != null) { lastDoseId = added.id.toString(); lastDoseRevision = added.revision }
        else if (pending.undoing || pending.after.doses.size != pending.before.doses.size) { lastDoseId = null; lastDoseRevision = 0 }
        finishTurns(if (pending.undoing) "직전 채팅 등록을 되돌렸어요." else "이 요청은 저장 완료됐어요. 같은 기록을 다시 실행하지 마세요.", includeTurns = pending.includeContext)
        ui = ui.copy(canUndo = !pending.undoing && state().snapshot == pending.after)
        say("저장 완료\n${pending.text}")
    }

    fun retrySave() {
        if (ui.busy || !ui.saveFailed || awaitingSave == null) return
        ui = ui.copy(busy = true, saving = true)
        save(state(), ::saved)
    }

    fun saveMemo() {
        val text = ui.input.trim()
        if (text.isEmpty() || ui.busy || ui.saveFailed) return
        ui = ui.copy(busy = true, saving = true)
        save(JournalOps.addNote(state(), text, clock(), zoneOf())) { ok ->
            ui = ui.copy(busy = false, saving = false)
            if (ok) { ui = ui.copy(input = ""); say("메모로 저장했어요. 타임라인에서 볼 수 있어요.") }
            else say("메모를 저장하지 못했어요. 입력한 내용은 그대로 남겨 두었어요.")
        }
    }

    fun undo() {
        val r = receipt ?: return
        if (ui.busy || ui.saveFailed) return
        if (state().snapshot != r.after) {
            ui = ui.copy(canUndo = false)
            say("이후 기록이 바뀌어 여기서 되돌릴 수 없어요. 타임라인에서 해당 기록을 확인해 주세요.")
            return
        }
        val next = RecordsReducer.undo(state().copy(undo = r.before)).copy(toast = null)
        awaitingSave = Receipt(RecordsReducer.undoPoint(state()), next.snapshot, "직전 채팅 등록을 되돌렸어요", undoing = true)
        receipt = null
        ui = ui.copy(busy = true, saving = true, canUndo = false)
        save(next, ::saved)
    }

    fun cancel() {
        if (ui.saving || ui.saveFailed) return
        inFlight?.let(cancelRequest)
        inFlight = null; turns = emptyList(); inputTimes = emptyList(); anchor = null
        pendingCommand = null; referenceActive = false
        ui = ui.copy(busy = false)
        checkpoint()
    }

    fun reset() {
        if (ui.saving || ui.saveFailed) return
        cancel(); receipt = null; contextTurns = emptyList(); lastDoseId = null; lastDoseRevision = 0
        ui = ChatUi(connected = ui.connected)
        checkpoint()
    }
    fun eraseHistory() {
        awaitingSave = null
        ui = ui.copy(saving = false, saveFailed = false)
        reset()
    }
}
