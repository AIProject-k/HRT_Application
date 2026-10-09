package com.hormonelog.app.feature.common

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dates and times as the design writes them: "10월 4일 (일)", "오후 2:10" — or "14:10" when
 * the user prefers a 24-hour clock. A year appears only for a date outside the current year.
 */
class Fmt(val zone: ZoneId, val clock24: Boolean, val now: Instant) {

    private fun z(i: Instant): ZonedDateTime = i.atZone(zone)

    fun weekday(i: Instant): String = z(i).dayOfWeek.shortLabel

    /** "10월 4일", "2025년 12월 3일" */
    fun date(i: Instant): String {
        val d = z(i)
        val year = if (d.year != z(now).year) "${d.year}년 " else ""
        return "$year${d.monthValue}월 ${d.dayOfMonth}일"
    }

    /** "10월 4일 (일)" */
    fun dateDay(i: Instant): String = "${date(i)} (${weekday(i)})"

    /** "오후 2:10" or "14:10" */
    fun time(i: Instant): String = minutes(z(i).hour * 60 + z(i).minute)

    fun minutes(minutesOfDay: Int): String {
        val h = (minutesOfDay / 60) % 24
        val m = minutesOfDay % 60
        if (clock24) return "%02d:%02d".format(h, m)
        val ap = if (h < 12) "오전" else "오후"
        val hh = if (h % 12 == 0) 12 else h % 12
        return "$ap $hh:%02d".format(m)
    }

    /** "10월 4일 (일) 오후 2:10" */
    fun dateTime(i: Instant): String = "${dateDay(i)} ${time(i)}"

    /** "10/4" for chart axes */
    fun short(i: Instant): String = z(i).let { "${it.monthValue}/${it.dayOfMonth}" }

    /** "방금", "3시간 전", "12일 전", "3일 뒤" */
    fun ago(i: Instant): String {
        val minutes = (now.toEpochMilli() - i.toEpochMilli()) / 60_000.0
        return when {
            minutes < -60 * 24 -> "${kotlin.math.ceil(-minutes / (60 * 24)).toInt()}일 뒤"
            minutes < -1 -> "${kotlin.math.ceil(-minutes / 60).toInt().coerceAtLeast(1)}시간 뒤"
            minutes < 1 -> "방금"
            minutes < 60 -> "${minutes.roundToInt()}분 전"
            minutes < 60 * 24 -> "${(minutes / 60).roundToInt()}시간 전"
            else -> "${(minutes / (60 * 24)).roundToInt()}일 전"
        }
    }

    /** Whole local calendar days from [a] to [b] (negative when b is earlier). */
    fun daysBetween(a: Instant, b: Instant): Long = java.time.temporal.ChronoUnit.DAYS.between(z(a).toLocalDate(), z(b).toLocalDate())

    /** "29시간", "1.2일": how long after a dose, in the unit that reads naturally. */
    fun hoursSince(hours: Double): String = when {
        hours < 48 -> "${hours.roundToInt()}시간"
        else -> "${"%.1f".format(hours / 24).trimEnd('0').trimEnd('.')}일"
    }

    /** "16시간 40분 전" for the past-time warning. */
    fun elapsed(from: Instant): String {
        val total = abs((now.toEpochMilli() - from.toEpochMilli()) / 60_000)
        val d = total / (60 * 24)
        val h = (total / 60) % 24
        val m = total % 60
        return buildString {
            if (d > 0) append("${d}일 ")
            if (h > 0) append("${h}시간 ")
            if (m > 0 || isEmpty()) append("${m}분")
        }.trim()
    }

    companion object {
        val Week: List<DayOfWeek> = DayOfWeek.entries
    }
}
