package com.hormonelog.app.chat

import com.hormonelog.app.state.AppState
import com.hormonelog.core.domain.Drug

/**
 * Ties each record's amount and time to its own drug mention by position.
 * A segment ends at a separator (comma, semicolon, newline, sentence end) or at a completion/route word, so one record's values stay in its segment.
 * Inside a segment a value binds to the nearest drug on its side; drugs on both sides make it ambiguous.
 * A record is bound when its amount binds to its drug and its time binds to the same drug in the same segment.
 * A time that sits only in segments without any drug is a common value for the list. Single-drug text needs no binding.
 */
internal object ChatBinding {
    private val separator = Regex("[\\n,;]|(?<=[가-힣0-9a-zA-Z])[.!](?=\\s|$)")
    private val closer = Regex("맞고|맞았|맞음|맞은|먹었|먹엇|먹음|먹고|복용했|복용함|복용|근육주사|피하주사|경구|설하")

    /**
     * True when a value of this record is not bound to the record's drug inside its own segment.
     * `checkTime` = false for a time the record inherited from the batch: that time is common to the list.
     */
    fun violates(s: AppState, c: ChatCommand, inputs: List<String>, checkTime: Boolean = true): Boolean {
        val text = inputs.joinToString("\n").lowercase()
        val mentions = drugMentions(s, text)
        if (mentions.size < 2) return false
        val drug = ChatCommands.knownDrug(s, c.drug)?.drug ?: return true
        val bounds = boundaries(text)
        val amount = c.amount.filter { it.isDigit() || it == '.' }
        val amountSegments = occurrences(text, amount)
            .filter { bindTo(text, it, mentions, bounds) == drug }
            .map { segmentStart(it, bounds) }
            .toSet()
        if (amountSegments.isEmpty()) return true
        val time = c.time.lowercase()
        if (!checkTime || time.isBlank()) return false
        val inDrugSegments = occurrences(text, time).filter { hasMention(text, it, mentions, bounds) }
        if (inDrugSegments.isEmpty()) return false
        return inDrugSegments.none { segmentStart(it, bounds) in amountSegments && bindTo(text, it, mentions, bounds) == drug }
    }

    private fun drugMentions(s: AppState, text: String): List<Mention> {
        val found = ChatCommands.drugNames(s).flatMap { name ->
            val drug = ChatCommands.knownDrug(s, name)?.drug ?: return@flatMap emptyList()
            Regex(Regex.escape(name.lowercase())).findAll(text).map { Mention(it.range.first, it.range.last + 1, drug) }.toList()
        }.sortedByDescending { it.end - it.start }
        val kept = mutableListOf<Mention>()
        for (m in found) if (kept.none { it.start < m.end && m.start < it.end }) kept += m
        return kept.sortedBy { it.start }
    }

    private fun occurrences(text: String, value: String): List<Int> {
        if (value.isBlank()) return emptyList()
        val pattern = value.trim().split(Regex("\\s+")).joinToString("\\s*") { Regex.escape(it) }
        return Regex("(?<![0-9.:/\\-])$pattern(?![0-9.:/\\-])").findAll(text).map { it.range.first }.toList()
    }

    private fun boundaries(text: String): List<Int> =
        (separator.findAll(text).map { it.range.last + 1 } + closer.findAll(text).map { it.range.last + 1 }).sorted().toList()

    private fun segmentStart(pos: Int, bounds: List<Int>): Int = bounds.lastOrNull { it <= pos } ?: 0

    private fun hasMention(text: String, pos: Int, mentions: List<Mention>, bounds: List<Int>): Boolean {
        val start = segmentStart(pos, bounds)
        val end = bounds.firstOrNull { it > pos } ?: text.length
        return mentions.any { it.start >= start && it.end <= end }
    }

    private fun bindTo(text: String, pos: Int, mentions: List<Mention>, bounds: List<Int>): Drug? {
        val start = segmentStart(pos, bounds)
        val end = bounds.firstOrNull { it > pos } ?: text.length
        val inSegment = mentions.filter { it.start >= start && it.end <= end }
        val left = inSegment.filter { it.end <= pos }.maxByOrNull { it.end }
        val right = inSegment.filter { it.start >= pos }.minByOrNull { it.start }
        return when {
            left != null && right != null -> if (left.drug == right.drug) left.drug else null
            left != null -> left.drug
            right != null -> right.drug
            else -> null
        }
    }

    private class Mention(val start: Int, val end: Int, val drug: Drug)
}
