package com.hormonelog.app.analysis

import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.doseSummary
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.feature.common.shortLabel
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.RowTarget
import com.hormonelog.app.state.plainNumber
import com.hormonelog.app.state.TimelinePeriod
import com.hormonelog.app.state.TimelineType
import com.hormonelog.app.state.TimelineUi
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.JournalEntry
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.RecordSource
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.VisitMemo
import com.hormonelog.core.domain.isAntiandrogen
import com.hormonelog.core.modelengine.CalibrationResult
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

/** What kind of record a timeline row is: it decides the icon and its tint. */
enum class RowKind { DOSE, GEL, MISS, LAB, CONDITION, BODY, EXTRA_LAB, MEMO }

data class TimelineRow(
    val target: RowTarget,
    val kind: RowKind,
    val antiandrogen: Boolean,
    val title: String,
    val sub: String,
    val source: String,
    val isExample: Boolean,
    val at: Instant?,
    val date: LocalDate?,
    val timeText: String,
    val drug: Drug?,
) {
    val key: String get() = "${target::class.simpleName}-${target.id}"
}

data class DayGroup(val key: String, val label: String, val rows: List<TimelineRow>)

enum class FoldState { TAKEN, MISSED, NONE }

data class FoldDay(val day: Int, val state: FoldState, val target: RowTarget?, val description: String)

/** A month of one medicine taken day after day, folded into one card with a calendar of dots. */
data class FoldGroup(val key: String, val title: String, val sub: String, val days: List<FoldDay>, val antiandrogen: Boolean)

data class MonthGroup(val key: String, val label: String, val folds: List<FoldGroup>, val days: List<DayGroup>)

data class AdherenceMonth(val label: String, val taken: Int, val total: Int, val percent: Int, val note: String)

data class TimelineModel(
    val adherence: List<AdherenceMonth>,
    val months: List<MonthGroup>,
    /** The drugs that appear in the doses, most used first, for the drug filter chips. */
    val drugs: List<Drug>,
    val rowCount: Int,
    val hasAnyRecord: Boolean,
)

/** Pure construction of the timeline from the records, the filters and the clock. */
object TimelineLogic {

    private const val FOLD_MIN_DAYS = 10
    private const val UNKNOWN_KEY = "unknown"

    fun build(s: AppState, fmt: Fmt, calibration: CalibrationResult): TimelineModel {
        val today = fmt.now.atZone(fmt.zone).toLocalDate()
        val ui = s.timeline
        val doseRows = doseRows(s.doses, fmt)
        val rows = doseRows + labRows(s.labs, s.doses, calibration, fmt) + journalRows(s.journal, fmt) + memoRows(s.memos, fmt)

        val drugs = s.doses.groupingBy { it.drug }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        val filtered = rows.filter { passes(it, ui, today) }

        // Daily medicines fold into one card per month, unless the filters ask for individual rows.
        val showFold = (ui.type == TimelineType.ALL || ui.type == TimelineType.DOSE) && ui.query.isEmpty()
        val foldCandidates = if (showFold) foldGroups(s.doses, fmt, today, ui) else emptyMap()
        val foldedIds = foldCandidates.values.flatMap { groups -> groups.flatMap { g -> g.days.mapNotNull { it.target?.id } } }.toSet()
        val visible = filtered.filterNot { it.target.id in foldedIds && it.kind != RowKind.GEL }

        val byMonth = visible.groupBy { it.date?.let { d -> YearMonth.from(d).toString() } ?: UNKNOWN_KEY }
        val monthKeys = (byMonth.keys + foldCandidates.keys).distinct().sortedWith(compareByDescending<String> { it != UNKNOWN_KEY }.thenByDescending { it })
        val months = monthKeys.map { key ->
            val monthRows = byMonth[key].orEmpty()
            val days = monthRows
                .groupBy { it.date }
                .entries.sortedWith(compareByDescending<Map.Entry<LocalDate?, List<TimelineRow>>> { it.key != null }.thenByDescending { it.key })
                .map { (date, list) ->
                    val label = when {
                        date == null -> "시간 미상"
                        date == today -> "오늘 · ${fmt.dateDay(dayStart(date, fmt))}"
                        else -> fmt.dateDay(dayStart(date, fmt))
                    }
                    DayGroup("$key-${date}", label, list.sortedByDescending { it.at ?: Instant.EPOCH })
                }
            val label = if (key == UNKNOWN_KEY) "시간 미상" else YearMonth.parse(key).let { ym -> if (ym.year == today.year) "${ym.monthValue}월" else "${ym.year}년 ${ym.monthValue}월" }
            MonthGroup(key, label, foldCandidates[key].orEmpty(), days)
        }.filter { it.days.isNotEmpty() || it.folds.isNotEmpty() }

        return TimelineModel(
            adherence = adherence(s.doses, fmt),
            months = months,
            drugs = drugs,
            rowCount = visible.size + foldCandidates.values.sumOf { it.size },
            hasAnyRecord = rows.isNotEmpty(),
        )
    }

    private fun dayStart(date: LocalDate, fmt: Fmt): Instant = date.atTime(12, 0).atZone(fmt.zone).toInstant()

    // ── rows ──────────────────────────────────────────────────
    private fun doseRows(doses: List<DoseEvent>, fmt: Fmt): List<TimelineRow> {
        // "용량 4 → 5 mg": a dose whose amount differs from the previous one of the same medicine.
        val changed = HashMap<java.util.UUID, String>()
        doses.filter { it.status.wasTaken }.groupBy { it.drug to it.route }.forEach { (_, list) ->
            list.sortedBy { it.occurredAt }.zipWithNext().forEach { (a, b) ->
                if (a.amountEntered != b.amountEntered) changed[b.id] = "용량 ${plainNumber(a.amountEntered)} → ${plainNumber(b.amountEntered)} ${b.enteredUnit.label}"
            }
        }
        return doses.map { d ->
            val missed = d.status == DoseStatus.SKIPPED
            val gel = d.route == Route.GEL
            val summary = doseSummary(d.drug, d.route, d.amountEntered, d.enteredUnit)
            val parts = buildList {
                if (gel) add("곡선 제외 · 모델 없음")
                changed[d.id]?.let(::add)
                d.site?.let { add(it.label) }
                d.note?.let(::add)
            }
            TimelineRow(
                target = RowTarget.Dose(d.id),
                kind = when { missed -> RowKind.MISS; gel -> RowKind.GEL; else -> RowKind.DOSE },
                antiandrogen = d.drug.isAntiandrogen,
                title = when {
                    missed -> "$summary · 놓침"
                    gel -> "${d.drug.label} ${plainNumber(d.amountEntered)} ${d.enteredUnit.label}"
                    else -> summary
                },
                sub = parts.joinToString(" · "),
                source = d.source.label,
                isExample = d.source == RecordSource.SAMPLE,
                at = d.occurredAt,
                date = d.occurredAt.atZone(fmt.zone).toLocalDate(),
                timeText = fmt.time(d.occurredAt),
                drug = d.drug,
            )
        }
    }

    private fun labRows(labs: List<LabResult>, doses: List<DoseEvent>, calibration: CalibrationResult, fmt: Fmt): List<TimelineRow> =
        labs.map { l ->
            val e2 = l.analytes.firstOrNull { it.analyte == Analyte.ESTRADIOL }
            val tt = l.analytes.firstOrNull { it.analyte == Analyte.TOTAL_TESTOSTERONE }
            val title = listOfNotNull(
                e2?.let { "E2 ${plainNumber(it.reportedValue)} ${it.reportedUnit}" },
                tt?.let { "TT ${plainNumber(it.reportedValue)} ${it.reportedUnit}" },
            ).joinToString(" · ")
            val at = l.collectedAt
            val since = at?.let { t -> Analysis.lastEstrogenBefore(doses, t)?.let { (t.toEpochMilli() - it.occurredAt.toEpochMilli()) / 86_400_000.0 } }
            val cal = calibration.labs[l.id]
            val parts = buildList {
                when {
                    l.isBaseline -> add("HRT 시작 전")
                    at == null -> add("채혈 시각 없음")
                    else -> {
                        if (since != null) add("투약 후 ${fmt.hoursSince(since * 24)}")
                        val used = (cal?.e2?.used == true) || (cal?.tt?.used == true)
                        add(if (used) "보정에 사용" else "보정 제외")
                    }
                }
                if (l.assay != Assay.UNKNOWN) add(l.assay.label)
                l.note?.let(::add)
            }
            TimelineRow(
                target = RowTarget.Lab(l.id), kind = RowKind.LAB, antiandrogen = false,
                title = title.ifEmpty { "검사 결과" }, sub = parts.joinToString(" · "), source = l.source.label, isExample = l.source == RecordSource.SAMPLE,
                at = at, date = at?.atZone(fmt.zone)?.toLocalDate(), timeText = at?.let(fmt::time).orEmpty(), drug = null,
            )
        }

    private fun journalRows(journal: List<JournalEntry>, fmt: Fmt): List<TimelineRow> = journal.map { j ->
        val (kind, title, sub) = when (j.kind) {
            JournalEntry.Kind.CONDITION -> Triple(
                RowKind.CONDITION, "컨디션 ${j.condition ?: "-"}/5",
                (j.symptoms.sortedBy { it.ordinal }.map { it.label } + listOfNotNull(j.note)).joinToString(" · "),
            )
            JournalEntry.Kind.BODY -> Triple(
                RowKind.BODY,
                listOfNotNull(
                    j.weightKg?.let { "체중 ${plainNumber(it)} kg" },
                    if (j.systolic != null && j.diastolic != null) "혈압 ${j.systolic}/${j.diastolic}" else null,
                ).joinToString(" · "),
                "",
            )
            JournalEntry.Kind.EXTRA_LABS -> Triple(
                RowKind.EXTRA_LAB,
                j.extraLabs.entries.sortedBy { it.key.ordinal }.joinToString(" · ") { (k, v) -> "${k.label} ${plainNumber(v)}" },
                "추가 검사",
            )
        }
        TimelineRow(
            target = RowTarget.Journal(j.id), kind = kind, antiandrogen = false, title = title, sub = sub, source = "직접", isExample = false,
            at = j.at, date = j.at.atZone(fmt.zone).toLocalDate(), timeText = fmt.time(j.at), drug = null,
        )
    }

    private fun memoRows(memos: List<VisitMemo>, fmt: Fmt): List<TimelineRow> = memos.map { m ->
        val at = m.date.atTime(12, 0).atZone(fmt.zone).toInstant()
        TimelineRow(
            target = RowTarget.Memo(m.id), kind = RowKind.MEMO, antiandrogen = false, title = m.title,
            sub = listOfNotNull("진료 메모", m.prescription).joinToString(" · "), source = "직접", isExample = false,
            at = at, date = m.date, timeText = "", drug = null,
        )
    }

    // ── filtering ─────────────────────────────────────────────
    private fun passes(r: TimelineRow, ui: TimelineUi, today: LocalDate): Boolean {
        val typeOk = when (ui.type) {
            TimelineType.ALL -> true
            TimelineType.DOSE -> r.kind == RowKind.DOSE || r.kind == RowKind.GEL
            TimelineType.LAB -> r.kind == RowKind.LAB || r.kind == RowKind.EXTRA_LAB
            TimelineType.MISSED -> r.kind == RowKind.MISS
            TimelineType.CONDITION -> r.kind == RowKind.CONDITION || r.kind == RowKind.BODY
            TimelineType.MEMO -> r.kind == RowKind.MEMO
        }
        if (!typeOk) return false
        if (ui.drug != null && r.drug != ui.drug) return false
        val days = ui.period.days
        if (days != null) {
            val d = r.date ?: return false
            if (d.isBefore(today.minusDays(days.toLong()))) return false
        }
        if (ui.query.isNotEmpty() && !(r.title + " " + r.sub).contains(ui.query, ignoreCase = true)) return false
        return true
    }

    // ── daily medicines ───────────────────────────────────────
    /** Month key → the folded cards of that month. Only medicines taken on many days of the month are folded. */
    private fun foldGroups(doses: List<DoseEvent>, fmt: Fmt, today: LocalDate, ui: TimelineUi): Map<String, List<FoldGroup>> {
        val result = LinkedHashMap<String, MutableList<FoldGroup>>()
        // A month folded into one card stays while any day of it is inside the chosen period.
        val cutoff = ui.period.days?.let { today.minusDays(it.toLong()) }
        val byMonth = doses.filter { it.route != Route.GEL }.groupBy { YearMonth.from(it.occurredAt.atZone(fmt.zone)) }
        for ((month, monthDoses) in byMonth) {
            if (cutoff != null && month.atEndOfMonth().isBefore(cutoff)) continue
            val lastDay = if (month == YearMonth.from(today)) today.dayOfMonth else month.lengthOfMonth()
            monthDoses.groupBy { it.drug to it.route }.forEach { (key, list) ->
                val (drug, _) = key
                if (ui.drug != null && ui.drug != drug) return@forEach
                val daysWithRecord = list.map { it.occurredAt.atZone(fmt.zone).dayOfMonth }.toSet()
                if (daysWithRecord.size < FOLD_MIN_DAYS) return@forEach
                val newest = list.maxBy { it.occurredAt }
                val taken = list.filter { it.status.wasTaken }.map { it.occurredAt.atZone(fmt.zone).dayOfMonth }.toSet()
                val missedDays = list.filter { it.status == DoseStatus.SKIPPED }.map { it.occurredAt.atZone(fmt.zone).dayOfMonth }.toSet() - taken
                val dots = (1..lastDay).map { day ->
                    val target = list.filter { it.occurredAt.atZone(fmt.zone).dayOfMonth == day }.let { l -> l.firstOrNull { it.status.wasTaken } ?: l.firstOrNull() }
                    val state = when { day in taken -> FoldState.TAKEN; day in missedDays -> FoldState.MISSED; else -> FoldState.NONE }
                    FoldDay(day, state, target?.let { RowTarget.Dose(it.id) }, "${month.monthValue}월 ${day}일 ${when (state) { FoldState.TAKEN -> "복용"; FoldState.MISSED -> "놓침"; FoldState.NONE -> "기록 없음" }}")
                }
                val daily = daysWithRecord.size >= (lastDay * 0.8)
                val name = doseSummary(newest.drug, newest.route, newest.amountEntered, newest.enteredUnit)
                val sub = buildString {
                    append("${month.monthValue}월 · ${taken.size}회 기록")
                    if (missedDays.isNotEmpty()) append(" · 놓침 ${missedDays.size}")
                }
                result.getOrPut(month.toString()) { ArrayList() } += FoldGroup(
                    key = "${month}|${drug}|${key.second}",
                    title = if (daily) "$name · 매일" else name,
                    sub = sub, days = dots, antiandrogen = drug.isAntiandrogen,
                )
            }
        }
        return result
    }

    // ── adherence ─────────────────────────────────────────────
    /** Per month, of the doses that were recorded, how many were really taken. The two newest months. */
    private fun adherence(doses: List<DoseEvent>, fmt: Fmt): List<AdherenceMonth> =
        doses.groupBy { YearMonth.from(it.occurredAt.atZone(fmt.zone)) }
            .entries.sortedByDescending { it.key }.take(2)
            .map { (month, list) ->
                val missed = list.filter { it.status == DoseStatus.SKIPPED }
                val taken = list.size - missed.size
                val pct = if (list.isEmpty()) 0 else Math.round(taken * 100f / list.size)
                val last = missed.maxByOrNull { it.occurredAt }
                AdherenceMonth(
                    label = "${month.monthValue}월 투약", taken = taken, total = list.size, percent = pct,
                    note = if (missed.isEmpty()) "놓친 기록 없음" else "놓침 ${missed.size}회" + (last?.let { " · ${fmt.date(it.occurredAt)}" } ?: ""),
                )
            }
}
