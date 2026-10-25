package com.hormonelog.app.chat

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** One reader for Korean dates and times. The rule parser and the validator both use it, so they agree. */
internal object KoreanTime {
    data class Day(val date: LocalDate, val yearless: Boolean)

    sealed interface Moment {
        data class At(val time: LocalTime) : Moment
        data class Elapsed(val instant: Instant) : Moment
        data object Ambiguous : Moment
    }

    private val affirmations = setOf("네", "예", "응", "맞아", "맞아요", "맞습니다", "그래", "그래요", "좋아", "좋아요")
    private val relativeDays = mapOf("오늘" to 0L, "어제" to -1L, "그제" to -2L, "그저께" to -2L, "엊그제" to -2L, "내일" to 1L, "모레" to 2L)

    fun isAffirmation(line: String) = compact(line) in affirmations

    fun day(token: String, now: Instant, zone: ZoneId): Day? {
        val t = compact(token)
        val today = now.atZone(zone).toLocalDate()
        relativeDays[t]?.let { return Day(today.plusDays(it), yearless = false) }
        Regex("(\\d{1,3})일전").matchEntire(t)?.let { return Day(today.minusDays(it.groupValues[1].toLong()), yearless = false) }
        Regex("(\\d{4})(?:년|-|\\.|/)(\\d{1,2})(?:월|-|\\.|/)(\\d{1,2})일?").matchEntire(t)?.let {
            return date(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt(), yearless = false)
        }
        Regex("(\\d{1,2})(?:월|/|\\.)(\\d{1,2})일?").matchEntire(t)?.let {
            return date(today.year, it.groupValues[1].toInt(), it.groupValues[2].toInt(), yearless = true)
        }
        return null
    }

    fun moment(token: String, now: Instant): Moment? {
        val t = compact(token)
        if (t == "방금" || t == "지금") return Moment.Elapsed(now)
        if (t == "정오") return Moment.At(LocalTime.NOON)
        Regex("(\\d{1,3})분전").matchEntire(t)?.let { return Moment.Elapsed(now.minusSeconds(it.groupValues[1].toLong() * 60)) }
        Regex("(\\d{1,2})시간전").matchEntire(t)?.let { return Moment.Elapsed(now.minusSeconds(it.groupValues[1].toLong() * 3600)) }
        Regex("(\\d{1,2}):(\\d{2})").matchEntire(t)?.let { return clock(it.groupValues[1].toInt(), it.groupValues[2].toInt()) }
        val m = Regex("(오전|오후|아침|저녁|밤|새벽|낮)?(\\d{1,2})시(?:(\\d{1,2})분|(반))?").matchEntire(t) ?: return null
        val minute = when {
            m.groupValues[4].isNotEmpty() -> 30
            m.groupValues[3].isNotEmpty() -> m.groupValues[3].toInt()
            else -> 0
        }
        val hour = m.groupValues[2].toInt()
        return when (m.groupValues[1]) {
            "" -> if (hour == 0 || hour >= 13) clock(hour, minute) else Moment.Ambiguous
            "오전", "아침", "새벽" -> if (hour in 1..12) clock(hour % 12, minute) else null
            "오후", "저녁", "낮" -> if (hour in 1..12) clock(hour % 12 + 12, minute) else null
            "밤" -> if (hour in 6..11) clock(hour + 12, minute) else Moment.Ambiguous
            else -> null
        }
    }

    /** Normalizes unit words and joins short answers into the earlier line whose field they complete. */
    fun normalizeInputs(inputs: List<String>): List<String> = normalizeWithOrigins(inputs).map { it.first }

    /** Same as [normalizeInputs], and each line carries the index of the input it came from. */
    fun normalizeWithOrigins(inputs: List<String>): List<Pair<String, Int>> {
        val lines = mutableListOf<Pair<String, Int>>()
        inputs.forEachIndexed { index, raw ->
            val line = raw.trim().replace(Regex("밀리그[램람]"), "mg")
            val period = Regex("^(오전|오후)$").matchEntire(line)?.groupValues?.get(1)
            val periodHour = Regex("^(오전|오후)\\s*(\\d{1,2})시(?:\\s*\\d{1,2}분|\\s*반)?$").matchEntire(line)
            val target = when {
                period != null -> lastBareHour(lines, null)
                periodHour != null -> lastBareHour(lines, periodHour.groupValues[2])
                line.lowercase() == "mg" -> lines.indices.reversed().firstOrNull { i -> !isAffirmation(lines[i].first) && amountCandidate.containsMatchIn(lines[i].first) }
                else -> null
            }
            if (target == null) {
                lines += line to index
                return@forEachIndexed
            }
            val (text, _) = lines[target]
            val merged = when {
                period != null -> replaceBareHour(text, "$period ${bareHours(text).first().value}")
                periodHour != null -> replaceBareHour(text, line, periodHour.groupValues[2])
                else -> amountCandidate.replaceFirst(text, "$1mg")
            }
            lines[target] = merged to lines[target].second
        }
        return lines
    }

    private val periodWords = listOf("오전", "오후", "아침", "저녁", "밤", "새벽", "낮")
    private val amountCandidate = Regex("(?<![0-9.:/\\-])(\\d+(?:\\.\\d+)?)(?![0-9.:/\\-]|\\s*(?:mg|밀리그램|밀리그람|mL|정|알|시|분|반|일|월|년|%))")

    /** Hour tokens written without an 오전/오후/아침/저녁/밤 word in front of them. */
    private fun bareHours(line: String): List<MatchResult> =
        Regex("(\\d{1,2})시(?:\\s*\\d{1,2}분|\\s*반)?").findAll(line).filter { m ->
            periodWords.none { line.substring(0, m.range.first).trimEnd().endsWith(it) }
        }.toList()

    private fun lastBareHour(lines: List<Pair<String, Int>>, hour: String?): Int? =
        lines.indices.reversed().firstOrNull { i ->
            !isAffirmation(lines[i].first) && bareHours(lines[i].first).any { hour == null || it.groupValues[1] == hour }
        }

    private fun replaceBareHour(text: String, replacement: String, hour: String? = null): String {
        val match = bareHours(text).first { hour == null || it.groupValues[1] == hour }
        return text.replaceRange(match.range, replacement)
    }

    private fun date(year: Int, month: Int, day: Int, yearless: Boolean): Day? =
        runCatching { Day(LocalDate.of(year, month, day), yearless) }.getOrNull()

    private fun clock(hour: Int, minute: Int): Moment? = runCatching { Moment.At(LocalTime.of(hour, minute)) }.getOrNull()

    private fun compact(s: String) = s.filterNot { it.isWhitespace() }.lowercase()
}
