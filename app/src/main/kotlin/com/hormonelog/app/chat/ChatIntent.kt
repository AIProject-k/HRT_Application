package com.hormonelog.app.chat

import com.hormonelog.app.state.AppState
import com.hormonelog.core.domain.Drug

/** What one clause says about a dose. Only COMPLETED clauses may become a record. */
internal enum class ChatIntent { COMPLETED, NEGATED, QUESTION, PLANNED, HYPOTHETICAL, QUOTED, CORRECTION, UNKNOWN }

/** Clause-level intent checks shared by the rule path and the model path. */
internal object ChatIntents {
    private val blockingIntents = setOf(ChatIntent.NEGATED, ChatIntent.QUESTION, ChatIntent.PLANNED, ChatIntent.HYPOTHETICAL, ChatIntent.QUOTED)
    private val negated = Regex("못\\s*(했|먹|맞|복용|함)|안\\s*(먹|맞|했|복용|함)|(먹|맞|복용|투약)\\s*지\\s*(않|안)|않았|않음|않아|하지\\s*(마|말)|취소|복용\\s*안")
    private val planned = Regex("(할|먹을|맞을|복용할|투약할|드실|먹어야|맞아야)\\s*(거|것|게|예정|계획)|예정|하려고|계획|먹기\\s*전|맞기\\s*전|복용\\s*전|투약\\s*전")
    private val hypothetical = Regex("만약|라면|다면|예시|예를 들|가정|먹어도|복용해도|맞아도|어떻게|예\\s*:")
    private val question = Regex("\\?|(었|았|였|했)(나요|나|니|을까|는지)[.!?]*\\s*$")
    private val completed = Regex("먹었|먹엇|복용했|복용함|복용|먹음|맞았|맞음|맞고|먹고|먹은|맞은|했어|했음|했습니다|했어요|먹었다")
    private val quoted = Regex("[\"“‘'].+[\"”’']")
    private val doseVerb = Regex("[먹맞]|복용|투약")
    private val symptom = Regex("두통|머리가|머리 아|어지러|어지럼|피곤|메스꺼|구역|구토|두근|설사|가슴이|붓|우울|불면|통증|아파|아팠|열이|현기증")

    fun of(clause: String): ChatIntent {
        val t = clause.trim()
        return when {
            t.isEmpty() -> ChatIntent.UNKNOWN
            quoted.containsMatchIn(t) -> ChatIntent.QUOTED
            t.startsWith("아니") && completed.containsMatchIn(t) && !negated.containsMatchIn(t) -> ChatIntent.CORRECTION
            negated.containsMatchIn(t) -> ChatIntent.NEGATED
            question.containsMatchIn(t) -> ChatIntent.QUESTION
            hypothetical.containsMatchIn(t) -> ChatIntent.HYPOTHETICAL
            planned.containsMatchIn(t) -> ChatIntent.PLANNED
            completed.containsMatchIn(t) -> ChatIntent.COMPLETED
            else -> ChatIntent.UNKNOWN
        }
    }

    /**
     * A dose is blocked when a clause says it did not happen or is not a fact.
     * With one drug named, any such clause blocks it. With several drugs, only a clause naming the same drug, or naming none, blocks it.
     */
    fun blocks(s: AppState, drug: Drug?, inputs: List<String>): Boolean {
        val named = drugsNamed(s, inputs)
        return clauses(inputs).any { clause ->
            of(clause) in blockingIntents && (named.size <= 1 || clauseDrugs(s, clause).let { it.isEmpty() || drug in it })
        }
    }

    /** A clause that says a dose did not happen, and names a drug or a dose verb, refuses the whole message before any parse. */
    fun blockedDose(s: AppState, inputs: List<String>): Boolean =
        clauses(inputs).any { clause -> of(clause) in blockingIntents && (clauseDrugs(s, clause).isNotEmpty() || doseVerb.containsMatchIn(clause)) }

    fun hasSymptom(inputs: List<String>): Boolean = inputs.any { symptom.containsMatchIn(it) }

    fun repeated(inputs: List<String>): Boolean =
        inputs.flatMap { it.lines() }.map { it.trim() }.filter { it.any(Char::isDigit) }.groupingBy { it }.eachCount().any { it.value > 1 }

    private fun clauses(inputs: List<String>): List<String> =
        inputs.flatMap { it.split(Regex("[\\n;,]|(?<=[가-힣0-9a-zA-Z])[.!](?=\\s|$)")) }.map { it.trim() }.filter { it.isNotEmpty() }

    private fun drugsNamed(s: AppState, inputs: List<String>): Set<Drug> = clauseDrugs(s, inputs.joinToString("\n"))

    private fun clauseDrugs(s: AppState, text: String): Set<Drug> {
        val compacted = compact(text)
        return ChatCommands.drugNames(s).filter { compacted.contains(compact(it)) }.mapNotNull { ChatCommands.knownDrug(s, it)?.drug }.toSet()
    }

    private fun compact(s: String) = s.filterNot { it.isWhitespace() }.lowercase()
}
