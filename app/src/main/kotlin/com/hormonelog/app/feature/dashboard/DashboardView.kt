package com.hormonelog.app.feature.dashboard

import com.hormonelog.app.feature.common.isAntiandrogen
import com.hormonelog.app.feature.common.label
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.LabResult
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** Pure view-model helpers: turn [DashboardState] + a clock into display-ready models. */

private val KOREAN_WEEKDAYS = listOf("월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일")

fun fmtDate(instant: Instant, zone: ZoneId): String {
    val d = instant.atZone(zone)
    return "${d.monthValue}월 ${d.dayOfMonth}일"
}

fun fmtShort(instant: Instant, zone: ZoneId): String {
    val d = instant.atZone(zone)
    return "${d.monthValue}/${d.dayOfMonth}"
}

fun fmtTime(instant: Instant, zone: ZoneId): String {
    val d = instant.atZone(zone)
    val h = d.hour
    val ap = if (h < 12) "오전" else "오후"
    val hh = if (h % 12 == 0) 12 else h % 12
    return "$ap $hh:${d.minute.toString().padStart(2, '0')}"
}

fun todayLabel(now: Instant, zone: ZoneId): String {
    val d = now.atZone(zone)
    return "${fmtDate(now, zone)} ${KOREAN_WEEKDAYS[d.dayOfWeek.value - 1]}"
}

fun ago(instant: Instant, now: Instant): String {
    val days = (now.toEpochMilli() - instant.toEpochMilli()) / 86_400_000.0
    return when {
        days < 0 -> "${kotlin.math.ceil(-days).toInt()}일 뒤"
        days < 0.04 -> "방금"
        days < 1 -> "${(days * 24).roundToInt()}시간 전"
        else -> "${days.roundToInt()}일 전"
    }
}

// ── lab analyte access ────────────────────────────────────────
fun LabResult.reported(analyte: Analyte): Double? =
    analytes.firstOrNull { it.analyte == analyte }?.reportedValue

fun LabResult.canonical(analyte: Analyte): Double? =
    analytes.firstOrNull { it.analyte == analyte }?.let { it.canonicalValue ?: it.reportedValue }

private fun DoseEvent.amountLabel(): String = buildString {
    append(if (amountEntered % 1.0 == 0.0) amountEntered.toInt().toString() else amountEntered.toString())
    append(enteredUnit.label)
}

// ── timeline / recent ────────────────────────────────────────
enum class TimelineKind { DOSE, MISSED, LAB }

data class TimelineEntry(
    val id: String,
    val kind: TimelineKind,
    val title: String,
    val subtitle: String,
    val timeText: String,
    val dateKey: String,
    val agoText: String,
    val isNew: Boolean,
)

data class TimelineGroup(val dateLabel: String, val items: List<TimelineEntry>)

private fun labSubtitle(lab: LabResult, withMethod: Boolean): String {
    val e2 = lab.reported(Analyte.ESTRADIOL)
    val tt = lab.reported(Analyte.TOTAL_TESTOSTERONE)
    val parts = buildList {
        if (e2 != null) add("E2 ${trimNum(e2)} pg/mL")
        if (tt != null) add("Total T ${trimNum(tt)} ng/dL")
    }
    var s = parts.joinToString(" · ")
    if (withMethod) {
        s += " · ${lab.assay.label}"
        lab.note?.let { s += " · $it" }
    }
    return s
}

private fun trimNum(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

fun timelineEntries(state: DashboardState, now: Instant, zone: ZoneId, withMethod: Boolean): List<TimelineEntry> {
    val doseEntries = state.doses.map { d ->
        val missed = d.status == DoseStatus.SKIPPED
        TimelineEntry(
            id = d.id.toString(),
            kind = if (missed) TimelineKind.MISSED else TimelineKind.DOSE,
            title = if (missed) "${d.drug.label} 누락" else d.drug.label,
            subtitle = buildString {
                append("${d.route.label} · ${d.amountLabel()}")
                d.note?.let { append(" · $it") }
            },
            timeText = fmtTime(d.occurredAt, zone),
            dateKey = fmtDate(d.occurredAt, zone),
            agoText = ago(d.occurredAt, now),
            isNew = d.id.toString() in state.newIds,
        ).let { it to d.occurredAt }
    }
    val labEntries = state.labs.map { l ->
        val t = l.collectedAt ?: Instant.EPOCH
        TimelineEntry(
            id = l.id.toString(),
            kind = TimelineKind.LAB,
            title = "혈액검사 결과",
            subtitle = labSubtitle(l, withMethod),
            timeText = l.collectedAt?.let { fmtTime(it, zone) } ?: "",
            dateKey = l.collectedAt?.let { fmtDate(it, zone) } ?: "시간 미상",
            agoText = l.collectedAt?.let { ago(it, now) } ?: "",
            isNew = l.id.toString() in state.newIds,
        ).let { it to t }
    }
    return (doseEntries + labEntries).sortedByDescending { it.second }.map { it.first }
}

fun timelineGroups(entries: List<TimelineEntry>, filter: TimelineFilter): List<TimelineGroup> {
    val shown = entries.filter {
        when (filter) {
            TimelineFilter.ALL -> true
            TimelineFilter.DOSE -> it.kind != TimelineKind.LAB
            TimelineFilter.LAB -> it.kind == TimelineKind.LAB
        }
    }
    if (shown.isEmpty()) return emptyList()
    val groups = LinkedHashMap<String, MutableList<TimelineEntry>>()
    for (e in shown) groups.getOrPut(e.dateKey) { mutableListOf() }.add(e)
    return groups.map { (date, items) ->
        val label = if (items.first().agoText.isNotEmpty()) "$date · ${items.first().agoText}" else date
        TimelineGroup(label, items)
    }
}

// ── home ─────────────────────────────────────────────────────
data class HomeSummary(val e2Now: String, val nextDose: String, val lastLab: String)

fun homeSummary(state: DashboardState, now: Instant): HomeSummary {
    val estrogenDoses = state.doses.filter {
        !it.drug.isAntiandrogen && it.status != DoseStatus.SKIPPED
    }
    val lastDose = estrogenDoses.maxByOrNull { it.occurredAt }
    // Prefer an active estrogen regimen's interval; otherwise assume weekly.
    val intervalDays = state.regimens
        .firstOrNull { it.active && !it.drug.isAntiandrogen }
        ?.everyDays ?: 7
    val nextDose = if (lastDose == null) {
        "기록 없음"
    } else {
        val nextDays = ((lastDose.occurredAt.plus(intervalDays.toLong(), ChronoUnit.DAYS).toEpochMilli() - now.toEpochMilli()) / 86_400_000.0).roundToInt()
        if (nextDays <= 0) "오늘" else "${nextDays}일 뒤"
    }
    val lastLab = state.labs.mapNotNull { it.collectedAt }.maxOrNull()
    return HomeSummary(
        e2Now = "—", // overridden by the caller with the engine median when available
        nextDose = nextDose,
        lastLab = lastLab?.let { ago(it, now) } ?: "기록 없음",
    )
}

// ── chart scrub text near a lab ──────────────────────────────
fun nearestLabWithin(state: DashboardState, t: Instant, series: HormoneSeries, hours: Long): Pair<Double, String>? {
    val analyte = if (series == HormoneSeries.E2) Analyte.ESTRADIOL else Analyte.TOTAL_TESTOSTERONE
    val unit = if (series == HormoneSeries.E2) "pg/mL" else "ng/dL"
    val match = state.labs
        .filter { it.collectedAt != null && it.reported(analyte) != null }
        .minByOrNull { abs((it.collectedAt!!.toEpochMilli() - t.toEpochMilli())) }
        ?: return null
    val within = abs(match.collectedAt!!.toEpochMilli() - t.toEpochMilli()) <= hours * 3_600_000
    if (!within) return null
    val v = match.reported(analyte)!!
    return v to "같은 날 실측 ${trimNum(v)} $unit"
}
