package com.hormonelog.app.analysis

import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.cadenceLabel
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.feature.common.shortLabel
import com.hormonelog.app.feature.common.shortText
import com.hormonelog.app.feature.flow.ChartModel
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.HormoneSeries
import com.hormonelog.app.state.ReportPart
import com.hormonelog.app.state.ReportPeriod
import com.hormonelog.app.state.plainNumber
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.domain.Route
import com.hormonelog.core.evidence.EvidenceBundleV1
import com.hormonelog.core.modelengine.CalibrationGroup
import com.hormonelog.core.modelengine.CalibrationResult
import com.hormonelog.core.modelengine.calibrationGroup
import java.time.Instant
import kotlin.math.roundToInt

data class ReportScheduleRow(val drug: String, val routeDose: String, val cadence: String, val start: String)

data class ReportLabRow(val date: String, val since: String, val measured: String, val expected: String, val diff: String, val status: String)

data class ReportMemoRow(val date: String, val title: String, val body: String, val prescription: String?)

/** The newest real E2 beside what the model expected at the same moment, for the share image. */
data class ReportHighlight(val dateText: String, val measured: String, val unit: String, val expected: String?, val range: String?)

/**
 * Everything a report says, worked out once and in words, so the PDF and the share image only
 * have to draw it. A section the user left out is null, never an empty list.
 */
data class ReportData(
    val periodText: String,
    val createdText: String,
    /** "근거 세트 2026.09": which evidence the curves were drawn from. */
    val modelText: String?,
    val schedule: List<ReportScheduleRow>?,
    /** The E2 curve for the period; null when it was left out or there is nothing to draw. */
    val chart: ChartModel?,
    val chartWanted: Boolean,
    val labs: List<ReportLabRow>?,
    val labsNote: String?,
    val adherence: List<String>?,
    val model: List<String>?,
    val memos: List<ReportMemoRow>?,
    val highlight: ReportHighlight?,
    val from: Instant,
    val to: Instant,
)

object ReportLogic {

    fun build(s: AppState, fmt: Fmt): ReportData {
        val ui = s.report
        val now = fmt.now
        val zone = fmt.zone
        val status = s.settings.gonadalStatus
        val first = (s.doses.map { it.occurredAt } + s.labs.mapNotNull { it.collectedAt }).minOrNull()
        val from = ui.period.days?.let { now.minusSeconds(it * 86_400L) } ?: (first ?: now.minusSeconds(30 * 86_400L))
        val to = now
        val inPeriod = { at: Instant -> !at.isBefore(from) && !at.isAfter(to) }
        val doses = s.doses.filter { inPeriod(it.occurredAt) }
        val include = ui.include

        val calibration = Analysis.calibrate(s.doses, s.labs, status)

        val chart = if (ReportPart.CHART in include) {
            val curves = Analysis.curves(s.doses, s.regimens, s.labs, status, now, from, to, zone, calibration, samples = 320)
            if (curves.e2 != null) chartModelFor(HormoneSeries.E2, curves, s.doses, s.labs, from, to, now, guide = false) else null
        } else {
            null
        }

        val labRows = ReportPart.LABS in include
        val e2Labs = s.labs.filter { l -> l.analytes.any { it.analyte == Analyte.ESTRADIOL } && (l.collectedAt?.let(inPeriod) ?: true) }
            .sortedWith(compareBy({ it.collectedAt == null }, { it.collectedAt }))

        return ReportData(
            periodText = periodText(from, to, ui.period, fmt),
            createdText = "만든 날 ${year(now, fmt)}년 ${fmt.date(now)}",
            modelText = if (ReportPart.MODEL in include) "근거 세트 ${EvidenceBundleV1.bundle.version}" else null,
            schedule = if (ReportPart.SCHEDULE in include) schedule(s, fmt) else null,
            chart = chart,
            chartWanted = ReportPart.CHART in include,
            labs = if (labRows) e2Labs.mapNotNull { labRow(it, s, calibration, fmt) } else null,
            labsNote = if (labRows) ttNote(s, inPeriod, fmt) else null,
            adherence = if (ReportPart.ADHERENCE in include) adherence(doses, fmt) else null,
            model = if (ReportPart.MODEL in include) modelLines(s, doses, calibration) else null,
            memos = if (ReportPart.MEMOS in include) s.memos.filter { inPeriod(it.date.atTime(12, 0).atZone(zone).toInstant()) }.map {
                ReportMemoRow(fmt.date(it.date.atTime(12, 0).atZone(zone).toInstant()), it.title, it.body.trim().take(240), it.prescription)
            } else null,
            highlight = highlight(s, calibration, fmt),
            from = from, to = to,
        )
    }

    private fun year(i: Instant, fmt: Fmt): Int = i.atZone(fmt.zone).year

    private fun periodText(from: Instant, to: Instant, period: ReportPeriod, fmt: Fmt): String {
        val a = from.atZone(fmt.zone)
        val b = to.atZone(fmt.zone)
        val end = if (a.year == b.year) "${b.monthValue}월 ${b.dayOfMonth}일" else "${b.year}년 ${b.monthValue}월 ${b.dayOfMonth}일"
        val length = when (period) {
            ReportPeriod.MONTH -> " (1개월)"
            ReportPeriod.QUARTER -> " (3개월)"
            ReportPeriod.HALF -> " (6개월)"
            ReportPeriod.ALL -> " (전체)"
        }
        return "${a.year}년 ${a.monthValue}월 ${a.dayOfMonth}일 – $end$length"
    }

    private fun schedule(s: AppState, fmt: Fmt): List<ReportScheduleRow> =
        s.regimens.filter { it.isRunningAt(fmt.now) }.sortedBy { it.drug.ordinal }.map { r ->
            val unit = if (r.route == Route.PATCH) "µg/일" else "mg"
            ReportScheduleRow(
                drug = r.drug.label,
                routeDose = "${r.route.shortLabel} ${plainNumber(r.amountEntered)} $unit",
                cadence = r.cadenceLabel(),
                start = "${year(r.startAt, fmt)}년 ${fmt.date(r.startAt)}",
            )
        }

    private fun labRow(lab: LabResult, s: AppState, calibration: CalibrationResult, fmt: Fmt): ReportLabRow? {
        val value = lab.analytes.firstOrNull { it.analyte == Analyte.ESTRADIOL } ?: return null
        val measured = Analysis.canonical(value)
        val at = lab.collectedAt
        val cal = calibration.labs[lab.id]?.e2
        val expected = cal?.expected ?: at?.let { Analysis.e2At(s.doses, s.regimens, CalibrationResult.NONE, it, fmt.now, fmt.zone) }
        val since = at?.let { t -> Analysis.lastEstrogenBefore(s.doses, t)?.let { (t.toEpochMilli() - it.occurredAt.toEpochMilli()) / 3_600_000.0 } }
        val diff = if (measured != null && expected != null && expected.median > 0) ((measured - expected.median) / expected.median * 100).roundToInt() else null
        return ReportLabRow(
            date = at?.let { fmt.date(it) } ?: "시각 모름",
            since = if (lab.isBaseline) "시작 전" else since?.let { fmt.hoursSince(it) } ?: "—",
            measured = measured?.let { plainNumber(Math.round(it * 10) / 10.0) } ?: "—",
            expected = expected?.let { "${it.median.roundToInt()} (${it.lower.roundToInt()}–${it.upper.roundToInt()})" } ?: "—",
            diff = diff?.let { "${if (it >= 0) "+" else ""}$it%" } ?: "—",
            status = if (cal?.used == true) "사용" else cal?.exclusion?.shortText ?: "—",
        )
    }

    /** Total T is not drawn in the table, so its measured values are listed under it. */
    private fun ttNote(s: AppState, inPeriod: (Instant) -> Boolean, fmt: Fmt): String? {
        val tt = s.labs.filter { l -> !l.isBaseline && l.collectedAt?.let(inPeriod) == true }.sortedByDescending { it.collectedAt }.mapNotNull { l ->
            l.analytes.firstOrNull { it.analyte == Analyte.TOTAL_TESTOSTERONE }?.let { "${fmt.date(l.collectedAt!!)} ${plainNumber(it.reportedValue)} ${it.reportedUnit}" }
        }.take(6)
        return buildString {
            append("E2 단위 pg/mL")
            if (tt.isNotEmpty()) append(" · Total T 실측: ${tt.joinToString(", ")}")
        }
    }

    private fun groupName(g: CalibrationGroup) = when (g) {
        CalibrationGroup.INJECTION -> "주사"
        CalibrationGroup.ORAL -> "경구·설하"
        CalibrationGroup.PATCH -> "패치"
    }

    private fun kindOf(d: DoseEvent): String = when (d.route) {
        Route.IM_INJECTION, Route.SC_INJECTION -> "주사"
        Route.ORAL, Route.SUBLINGUAL -> "경구"
        Route.PATCH -> "패치"
        Route.GEL -> "젤"
    }

    /** Of the doses recorded in the period, how many were really taken — per kind, with the missed days named when few. */
    private fun adherence(doses: List<DoseEvent>, fmt: Fmt): List<String> {
        if (doses.isEmpty()) return listOf("기록된 투약이 없어요")
        return doses.groupBy(::kindOf).map { (kind, list) ->
            val missed = list.filter { it.status == DoseStatus.SKIPPED }
            val taken = list.size - missed.size
            val pct = Math.round(taken * 100f / list.size)
            val miss = when {
                missed.isEmpty() -> ""
                missed.size == 1 -> " · 놓침 ${fmt.date(missed.first().occurredAt)}"
                else -> " · 놓침 ${missed.size}회"
            }
            "$kind $taken / ${list.size}회 ($pct%)$miss"
        }
    }

    private fun modelLines(s: AppState, doses: List<DoseEvent>, calibration: CalibrationResult): List<String> {
        val lines = ArrayList<String>()
        calibration.groups.entries.sortedBy { it.key.ordinal }.forEach { (g, cal) ->
            val pct = ((cal.scale - 1.0) * 100).roundToInt()
            lines += "${groupName(g)} E2: 검사 ${cal.labCount}건으로 " + when {
                pct > 0 -> "+$pct% 보정"
                pct < 0 -> "$pct% 보정"
                else -> "보정 없음"
            } + if (cal.atLimit) " (한계값)" else ""
        }
        calibration.tt?.let { lines += "Total T: 검사 ${it.labCount}건으로 ${((it.scale - 1.0) * 100).roundToInt().let { p -> if (p >= 0) "+$p" else "$p" }}% 보정" }
        if (lines.isEmpty()) lines += "보정 없이 문헌 평균으로 그렸어요"
        val gel = doses.count { it.route == Route.GEL }
        if (gel > 0) lines += "젤 기록 ${gel}건은 곡선 계산에서 제외"
        if (s.settings.gonadalStatus == GonadalStatus.DECLINED) lines += "Total T 곡선은 그리지 않았어요"
        return lines
    }

    /** The newest real E2 with a draw time, against what the model expected at that moment. */
    private fun highlight(s: AppState, calibration: CalibrationResult, fmt: Fmt): ReportHighlight? {
        val last = HomeLogic.lastLab(s.labs) ?: return null
        val at = last.lab.collectedAt ?: return null
        val cal = calibration.labs[last.lab.id]?.e2
        val expected = Analysis.e2At(s.doses, s.regimens, calibration, at, fmt.now, fmt.zone) ?: cal?.expected
        val measured = last.lab.analytes.firstOrNull { it.analyte == Analyte.ESTRADIOL }?.let(Analysis::canonical)
        return ReportHighlight(
            dateText = fmt.date(at),
            measured = measured?.let { plainNumber(Math.round(it).toDouble()) } ?: plainNumber(last.reported),
            unit = if (measured != null) "pg/mL" else last.unit,
            expected = expected?.median?.roundToInt()?.toString(),
            range = expected?.let { "${it.lower.roundToInt()}–${it.upper.roundToInt()}" },
        )
    }
}
