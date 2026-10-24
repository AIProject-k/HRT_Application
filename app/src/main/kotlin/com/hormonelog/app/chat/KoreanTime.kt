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
    private val units = setOf("mg", "밀리그램", "밀리그람")

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

    /** Normalizes unit words, then joins short answers: a bare “오전”/“오후” line completes the hour above it, a bare unit line completes the number above it. */
    fun normalizeInputs(inputs: List<String>): List<String> {
        val lines = mutableListOf<String>()
        for (raw in inputs) {
            val line = raw.trim().replace(Regex("밀리그[램람]"), "mg")
            val last = lines.lastOrNull()
            val hourEnd = Regex("(\\d{1,2}시(?:\\s*\\d{1,2}분|\\s*반)?)\\s*$")
            when {
                last != null && (line == "오전" || line == "오후") && hourEnd.containsMatchIn(last) ->
                    lines[lines.lastIndex] = hourEnd.replace(last, "$line $1")
                last != null && line.lowercase() in units && Regex("\\d").containsMatchIn(last) ->
                    lines[lines.lastIndex] = attachUnit(last)
                else -> lines += line
            }
        }
        return lines
    }

    private fun attachUnit(line: String) = line.replaceFirst(
        Regex("(?<![0-9.])(\\d+(?:\\.\\d+)?)(?![0-9.]|\\s*(?:mg|밀리그램|밀리그람|mL|정|알|시|분|반|일|월|년|%))"), "$1mg",
    )

    private fun date(year: Int, month: Int, day: Int, yearless: Boolean): Day? =
        runCatching { Day(LocalDate.of(year, month, day), yearless) }.getOrNull()

    private fun clock(hour: Int, minute: Int): Moment? = runCatching { Moment.At(LocalTime.of(hour, minute)) }.getOrNull()

    private fun compact(s: String) = s.filterNot { it.isWhitespace() }.lowercase()
}
