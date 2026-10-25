package com.hormonelog.app.eval

import com.hormonelog.app.ai.LlmResponseParser
import com.hormonelog.app.chat.ChatCommand
import com.hormonelog.app.chat.ChatCommands
import com.hormonelog.app.chat.KoreanTime
import com.hormonelog.app.state.AppState
import com.hormonelog.core.domain.Drug
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/** One scored case. `pass` means the saved records equal the fixed expectation, nothing more and nothing less. */
data class EvalOutcome(
    val id: String,
    val route: String,
    val pass: Boolean,
    val parseFail: Boolean,
    val itemsExpected: Int,
    val itemsMatched: Int,
    val wrongSaved: Int,
    val missedSaved: Int,
    val saved: List<String>,
    val message: String,
)

/** Scores a raw model reply (or a rule result) through the product validator, against the fixed expectation. */
object EvalScorer {
    private val minute = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /** Rule-first routing as the product does it: rule result when present, else the model reply. */
    fun routed(case: EvalCase, rule: ChatCommand?, raw: String?): EvalOutcome =
        if (rule != null) score(case, rule, "rule") else raw?.let { forced(case, it) } ?: parseFailed(case, "no-reply")

    /** Forces the model reply, ignoring the rules. Used to measure the model alone. */
    fun forced(case: EvalCase, raw: String): EvalOutcome {
        val command = try { LlmResponseParser.parse(raw) } catch (_: Exception) { return parseFailed(case, "model") }
        return score(case, command, "model")
    }

    fun score(case: EvalCase, command: ChatCommand, route: String): EvalOutcome {
        val rawDoses = when (command.action) {
            "batch" -> command.doses.map { it to command.date }
            "dose" -> listOf(command to command.date)
            else -> emptyList()
        }
        val consumed = mutableSetOf<Int>()
        var matched = 0
        for (want in case.doses) {
            val index = rawDoses.indices.firstOrNull { it !in consumed && matches(want, rawDoses[it].first, rawDoses[it].second) } ?: continue
            consumed += index
            matched++
        }
        val decision = ChatCommands.apply(AppState(), command, listOf(case.input), UUID.randomUUID(), EVAL_NOW, EVAL_ZONE)
        val next = decision.next
        val savedDoses = next?.doses.orEmpty().map { Dose(it.drug, it.amountEntered, it.occurredAt.atZone(EVAL_ZONE).format(minute)) }
        val savedLabs = next?.labs.orEmpty().flatMap { lab -> lab.analytes.map { LabRec(it.analyte, it.reportedValue, it.reportedUnit) } }
        val wantedDoses = if (case.save) case.doses else emptyList()
        val wantedLabs = if (case.save) case.labs else emptyList()
        val wrong = surplus(savedDoses, wantedDoses) + surplus(savedLabs, wantedLabs)
        val missed = surplus(wantedDoses, savedDoses) + surplus(wantedLabs, savedLabs)
        return EvalOutcome(
            id = case.id, route = route, pass = wrong == 0 && missed == 0, parseFail = false,
            itemsExpected = case.doses.size, itemsMatched = matched, wrongSaved = wrong, missedSaved = missed,
            saved = savedDoses.map { "${it.drug} ${it.mg}mg ${it.at}" } + savedLabs.map { "${it.analyte} ${it.value} ${it.unit}" },
            message = decision.message,
        )
    }

    /** How many items of `a` have no unmatched equal item left in `b` (multiset difference). */
    fun <T> surplus(a: List<T>, b: List<T>): Int {
        val left = b.toMutableList()
        return a.count { !left.remove(it) }
    }

    private fun parseFailed(case: EvalCase, route: String) = EvalOutcome(
        id = case.id, route = route, pass = false, parseFail = true, itemsExpected = case.doses.size, itemsMatched = 0,
        wrongSaved = 0, missedSaved = case.doses.size + case.labs.size, saved = emptyList(), message = "",
    )

    /** One raw item matches only when drug, amount, unit and the clock date and time all equal the expectation. */
    private fun matches(want: Dose, raw: ChatCommand, batchDate: String): Boolean {
        if (ChatCommands.knownDrug(AppState(), raw.drug)?.drug != want.drug) return false
        val amount = raw.amount.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return false
        if (amount != want.mg) return false
        val unit = raw.amountUnit.ifBlank { raw.amount }.lowercase()
        if (unit.any { it.isLetter() } && !unit.contains("mg")) return false
        val clock = KoreanTime.moment(raw.time, EVAL_NOW) as? KoreanTime.Moment.At ?: return false
        val day = KoreanTime.day(raw.date.ifBlank { batchDate.ifBlank { "오늘" } }, EVAL_NOW, EVAL_ZONE) ?: return false
        return LocalDateTime.of(day.date, clock.time).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) == want.at
    }
}
