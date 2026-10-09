package com.hormonelog.app.feature.flow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hormonelog.app.analysis.Analysis
import com.hormonelog.app.analysis.SteadyState
import com.hormonelog.app.analysis.chartModelFor
import com.hormonelog.app.feature.common.DatePickDialog
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.reasonText
import com.hormonelog.app.feature.common.shortLabel
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.ChartRange
import com.hormonelog.app.state.HormoneSeries
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlBanner
import com.hormonelog.app.ui.kit.HlButton
import com.hormonelog.app.ui.kit.HlButtonKind
import com.hormonelog.app.ui.kit.HlChip
import com.hormonelog.app.ui.kit.HlDisclaimer
import com.hormonelog.app.ui.kit.HlNotice
import com.hormonelog.app.ui.kit.HlSegmented
import com.hormonelog.app.ui.kit.HlSwitch
import com.hormonelog.app.ui.kit.MeasuredMark
import com.hormonelog.app.ui.kit.ScreenScroll
import com.hormonelog.app.ui.kit.SegItem
import com.hormonelog.app.ui.kit.rememberAsync
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.isAntiandrogen
import com.hormonelog.core.modelengine.CalibrationResult
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

class FlowActions(
    val series: (HormoneSeries) -> Unit,
    val range: (ChartRange) -> Unit,
    val customFrom: (Long) -> Unit,
    val scrub: (Float?) -> Unit,
    val guide: (Boolean) -> Unit,
    val model: () -> Unit,
    val gonadal: () -> Unit,
    val record: () -> Unit,
)

private val RANGE_CHIPS = listOf(
    ChartRange.WEEK to "1주", ChartRange.MONTH to "1개월", ChartRange.QUARTER to "3개월", ChartRange.HALF to "6개월",
    ChartRange.YEAR to "1년", ChartRange.ALL to "전체", ChartRange.CUSTOM to "직접",
)

/** The time the chart shows: [spanDays] back from now, and a little way forward for what is planned. */
private class Window(val from: Instant, val to: Instant, val spanDays: Int)

private fun window(s: AppState, fmt: Fmt): Window {
    val now = fmt.now
    val ui = s.flow
    val first = (s.doses.map { it.occurredAt } + s.labs.mapNotNull { it.collectedAt }).minOrNull()
    val spanDays = when (ui.range) {
        ChartRange.ALL -> first?.let { ((now.toEpochMilli() - it.toEpochMilli()) / 86_400_000L).toInt() + 2 }?.coerceAtLeast(14) ?: 30
        ChartRange.CUSTOM -> ui.customFromMillis?.let { ((now.toEpochMilli() - it) / 86_400_000L).toInt() }?.coerceAtLeast(7) ?: 30
        else -> ui.range.days ?: 30
    }
    val future = if (spanDays <= 30) 7 else (spanDays * 0.1).roundToInt().coerceAtMost(30)
    return Window(now.minusSeconds(spanDays * 86_400L), now.plusSeconds(future * 86_400L), spanDays)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowScreen(s: AppState, fmt: Fmt, actions: FlowActions) {
    val c = Hl.colors
    val ui = s.flow
    val now = fmt.now
    val status = s.settings.gonadalStatus
    val w = window(s, fmt)
    val bucket = now.epochSecond / 600
    var pickingStart by remember { mutableStateOf(false) }

    val calibration = remember(s.doses, s.labs, status) { Analysis.calibrate(s.doses, s.labs, status) }
    val curves = rememberAsync(s.doses, s.regimens, s.labs, status, ui.range, ui.customFromMillis, bucket) {
        Analysis.curves(s.doses, s.regimens, s.labs, status, now, w.from, w.to, fmt.zone, calibration, samples = (w.spanDays * 3).coerceIn(160, 700))
    }
    val ratios = rememberAsync(s.doses, s.regimens, s.labs, status) { ratioPoints(s, calibration, fmt) }
    val steady = remember(s.doses, bucket) { SteadyState.find(s.doses, now, fmt) }
    val isE2 = ui.series == HormoneSeries.E2

    ScreenScroll(gap = 12.dp) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            HlText("예상 흐름", size = HlSize.t22, weight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            val usedLabs = calibration.labs.values.count { it.e2?.used == true || it.tt?.used == true }
            HlText(
                if (usedLabs > 0) "모델 상태 · 보정 ${usedLabs}건" else "모델 상태",
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(c.orangeSoft)
                    .border(1.dp, c.orange, RoundedCornerShape(999.dp))
                    .clickable(role = Role.Button, onClick = actions.model)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                size = HlSize.t12, weight = FontWeight.Bold, color = c.orange,
            )
        }

        HlSegmented(
            items = listOf(SegItem(HormoneSeries.E2, "E2", content = c.teal), SegItem(HormoneSeries.TT, "Total T", content = c.blue)),
            selected = ui.series, onSelect = actions.series, minHeight = 42.dp, size = HlSize.t14,
        )

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RANGE_CHIPS.forEach { (r, label) ->
                HlChip(
                    label, ui.range == r,
                    { if (r == ChartRange.CUSTOM) { pickingStart = true } else actions.range(r) },
                    minHeight = 44.dp, size = HlSize.t13, shape = RoundedCornerShape(999.dp), padding = PaddingValues(horizontal = 14.dp),
                )
            }
        }
        if (ui.range == ChartRange.CUSTOM) {
            val from = ui.customFromMillis?.let(Instant::ofEpochMilli)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DateTile(from?.let { fmt.date(it) } ?: "시작일 고르기", Modifier.weight(1f)) { pickingStart = true }
                HlText("–", color = c.muted)
                DateTile("${fmt.date(now)} (오늘)", Modifier.weight(1f), onClick = null)
            }
        }

        when {
            isE2.not() && curves?.ttHidden == true -> TtHiddenCard(actions.gonadal)
            curves != null && curves.hasE2.not() && s.doses.none { it.status.wasTaken } -> EmptyChartCard(actions.record)
            else -> ChartCard(s, fmt, curves, w, actions)
        }

        if (curves != null) {
            val notices = buildList<@Composable () -> Unit> {
                if (curves.excluded.isNotEmpty()) {
                    val first = curves.excluded.first()
                    val names = curves.excluded.map { it.dose.drug }.distinct()
                    add {
                        HlBanner(HlNotice.Warn) {
                            HlText(
                                androidx.compose.ui.text.buildAnnotatedString {
                                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold, color = c.orange))
                                    append("${if (names.size == 1) names.first().shortLabel else "일부 투약"} ${curves.excluded.size}건은 곡선에서 제외")
                                    pop()
                                    append(" · ${first.reason.reasonText}")
                                },
                                size = HlSize.t13, lineHeight = 1.5f,
                            )
                        }
                    }
                }
                val recent = s.doses.filter { it.status.wasTaken && !it.drug.isAntiandrogen && it.occurredAt.isAfter(now.minusSeconds(60L * 86_400L)) }
                if (!isE2 && recent.isNotEmpty() && recent.all { it.route == Route.ORAL || it.route == Route.SUBLINGUAL } && !curves.ttHidden) {
                    add { HlBanner(HlNotice.Warn) { HlText("경구·설하만 쓰면 T 예측이 낮게 나올 수 있어요.", size = HlSize.t13, lineHeight = 1.5f) } }
                }
                steady?.let { n -> add { HlBanner(HlNotice.Info) { HlText(n.text, size = HlSize.t13, lineHeight = 1.5f) } } }
            }
            if (notices.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { notices.forEach { it() } }
        }

        if (isE2) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(HlRadius.button))
                    .border(1.dp, c.line, RoundedCornerShape(HlRadius.button))
                    .clickable(role = Role.Switch) { actions.guide(!ui.guide) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    HlText("가이드라인 참고 띠", size = HlSize.t14, weight = FontWeight.SemiBold)
                    HlText("진료 가이드라인에 실린 범위를 회색으로 표시해요. 목표가 아니에요.", size = HlSize.t12, color = c.muted)
                }
                HlSwitch(ui.guide)
            }
        }

        if (!ratios.isNullOrEmpty()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card).padding(start = 12.dp, top = 14.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    HlText("실측 ÷ 예상 추이", size = HlSize.t14, weight = FontWeight.Bold)
                    HlText("1.0 = 예상과 같음", size = HlSize.t12, color = c.muted)
                }
                RatioChart(ratios)
            }
        }

        HlDisclaimer(Modifier.padding(top = 4.dp))
    }

    if (pickingStart) {
        DatePickDialog(
            seed = ui.customFromMillis?.let { Instant.ofEpochMilli(it).atZone(fmt.zone).toLocalDate() } ?: LocalDate.now(fmt.zone).minusMonths(2),
            zone = fmt.zone, latest = LocalDate.now(fmt.zone).minusDays(1),
            onDismiss = { pickingStart = false },
            onPicked = { d -> actions.customFrom(d.atStartOfDay(fmt.zone).toInstant().toEpochMilli()); pickingStart = false },
        )
    }
}

@Composable
private fun DateTile(text: String, modifier: Modifier, onClick: (() -> Unit)?) {
    val c = Hl.colors
    Row(
        modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(HlRadius.chip)).background(c.input)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { HlText(text, size = HlSize.t13, weight = FontWeight.SemiBold) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChartCard(s: AppState, fmt: Fmt, curves: com.hormonelog.app.analysis.Curves?, w: Window, actions: FlowActions) {
    val c = Hl.colors
    val ui = s.flow
    val isE2 = ui.series == HormoneSeries.E2
    val model = if (curves != null) chartModelFor(ui.series, curves, s.doses, s.labs, w.from, w.to, fmt.now, guide = ui.guide) else null
    val unit = if (isE2) "pg/mL" else "ng/dL"
    val name = if (isE2) "E2" else "Total T"
    val accent = if (isE2) c.teal else c.blue

    // The line above the chart follows the finger; at rest it reads "now".
    val at = if (model != null && ui.scrub != null) scrubInstant(model, ui.scrub) else fmt.now
    val point = model?.curve?.pointAt(at)
    val near = model?.labs?.minByOrNull { kotlin.math.abs(it.at.toEpochMilli() - at.toEpochMilli()) }
        ?.takeIf { kotlin.math.abs(it.at.toEpochMilli() - at.toEpochMilli()) <= maxOf(0.6 * 86_400_000.0, w.spanDays * 86_400_000.0 / 120) }
    val summary = if (model?.curve != null) {
        val past = model.curve.points.filter { !it.at.isAfter(fmt.now) }
        val range = if (past.isEmpty()) "" else "${past.minOf { it.lower }.roundToInt()}–${past.maxOf { it.upper }.roundToInt()}"
        "${rangeText(ui.range)} 예상 $name $range $unit, 실측 ${model.labs.count { it.at in w.from..fmt.now }}건"
    } else {
        "예상 $name 곡선 없음"
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.hero)).background(c.card).padding(start = 12.dp, top = 14.dp, end = 12.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            HlText(
                if (ui.scrub == null) "지금 · ${fmt.dateDay(fmt.now)}" else "${fmt.dateDay(at)} ${fmt.time(at)}${if (at.isAfter(fmt.now)) " · 예정 투약 기준" else ""}",
                size = HlSize.t12, color = c.muted,
            )
            HlText(
                if (point != null) "예상 $name ${point.median.roundToInt()} $unit · 범위 ${point.lower.roundToInt()}–${point.upper.roundToInt()}" else "이 시점 예상값 없음",
                size = HlSize.t16, weight = FontWeight.Bold, color = accent, tabular = true,
            )
            if (near != null) {
                HlText(
                    "◆ 실측 ${near.value.roundToInt()} $unit · ${fmt.date(near.at)}${if (near.used) "" else " · 보정 제외"}",
                    size = HlSize.t13, weight = FontWeight.Bold, color = c.yellow,
                )
            }
        }
        if (model != null) {
            EstimateChart(model, fmt, scrub = ui.scrub, onScrub = actions.scrub, summary = summary)
        } else {
            Box(Modifier.fillMaxWidth().aspectRatio(320f / 210f))
        }
        FlowRow(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LegendRow("예상 · 범위") { Box(Modifier.size(width = 14.dp, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(if (isE2) c.tealSoft else c.blueSoft).border(2.dp, accent, RoundedCornerShape(4.dp))) }
            LegendRow("실측") { MeasuredMark(8.dp) }
            LegendRow("보정 제외") { MeasuredMark(8.dp, hollow = true) }
            LegendRow("에스트로겐") { Box(Modifier.size(width = 3.dp, height = 10.dp).background(c.blue)) }
            LegendRow("항안드로겐") { Box(Modifier.size(width = 3.dp, height = 10.dp).background(c.violet)) }
            LegendRow("예정") { Box(Modifier.size(width = 4.dp, height = 10.dp).border(1.2.dp, c.blue)) }
            LegendRow("놓침") { Box(Modifier.size(width = 4.dp, height = 10.dp).border(1.2.dp, c.danger)) }
        }
        HlText("가로로 끌면 날짜별 값을 볼 수 있어요", size = HlSize.t12, color = c.muted, modifier = Modifier.padding(horizontal = 4.dp))
    }
    HlText("요약 · $summary", size = HlSize.t12, color = c.muted, lineHeight = 1.6f, modifier = Modifier.padding(horizontal = 4.dp))
}

private fun rangeText(range: ChartRange): String = when (range) {
    ChartRange.ALL -> "기록 전체"
    ChartRange.CUSTOM -> "직접 고른 기간"
    ChartRange.WEEK -> "지난 1주"
    ChartRange.MONTH -> "지난 1개월"
    ChartRange.QUARTER -> "지난 3개월"
    ChartRange.HALF -> "지난 6개월"
    ChartRange.YEAR -> "지난 1년"
}

@Composable
private fun LegendRow(label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        swatch()
        HlText(label, size = HlSize.t12, color = Hl.colors.muted)
    }
}

@Composable
private fun EmptyChartCard(onRecord: () -> Unit) {
    val c = Hl.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.hero)).background(c.card).padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HlIcon(HlIcon.Flow, size = 36.dp, tint = c.teal, strokeWidth = 1.5f)
        HlText("아직 그릴 곡선이 없어요", size = HlSize.t16, weight = FontWeight.Bold)
        HlText("투약을 1건 이상 기록하면 예상 곡선이 그려져요.", size = HlSize.t14, color = c.muted, lineHeight = 1.6f)
        HlButton("투약 기록하기", onRecord, Modifier.fillMaxWidth(), minHeight = 52.dp, size = HlSize.t14)
    }
}

@Composable
private fun TtHiddenCard(onChange: () -> Unit) {
    val c = Hl.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.hero)).background(c.card).padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HlText("Total T 곡선을 그리지 않아요", size = HlSize.t16, weight = FontWeight.Bold)
        HlText("고환 정보를 '말하고 싶지 않음'으로 두어서, Total T는 예측하지 않아요. 바꾸면 바로 그려져요.", size = HlSize.t14, color = c.muted, lineHeight = 1.6f)
        HlButton("고환 정보 바꾸기", onChange, kind = HlButtonKind.Secondary, minHeight = 48.dp, size = HlSize.t14)
    }
}

// ── 실측 ÷ 예상 ───────────────────────────────────────────────

class RatioPoint(val at: Instant, val ratio: Double, val used: Boolean)

/** For every E2 lab with a draw time: what was measured divided by what the model said without any calibration. */
private fun ratioPoints(s: AppState, calibration: CalibrationResult, fmt: Fmt): List<RatioPoint> =
    s.labs.filter { !it.isBaseline && it.collectedAt != null }.mapNotNull { lab ->
        val at = lab.collectedAt!!
        val measured = lab.analytes.firstOrNull { it.analyte == com.hormonelog.core.domain.Analyte.ESTRADIOL }?.let(Analysis::canonical) ?: return@mapNotNull null
        val expected = Analysis.e2At(s.doses, s.regimens, CalibrationResult.NONE, at, fmt.now, fmt.zone)?.median?.takeIf { it >= 15.0 } ?: return@mapNotNull null
        RatioPoint(at, measured / expected, calibration.labs[lab.id]?.e2?.used == true)
    }.sortedBy { it.at }

@Composable
private fun RatioChart(points: List<RatioPoint>) {
    val c = Hl.colors
    val measurer = rememberTextMeasurer()
    val zone = java.time.ZoneId.systemDefault()
    val label = TextStyle(fontSize = 9.sp, color = c.muted)
    Canvas(Modifier.fillMaxWidth().aspectRatio(320f / 90f).semantics { contentDescription = "검사마다 실측을 예상으로 나눈 값의 추이" }) {
        val sx = size.width / 320f
        val sy = size.height / 90f
        val pl = 30f; val pr = 10f; val pt = 8f; val pb = 18f
        val iw = 320f - pl - pr; val ih = 90f - pt - pb
        fun y(r: Double) = (pt + ih - ((r - 0.4) / (2.4 - 0.4)).toFloat() * ih)
        val t0 = points.first().at.toEpochMilli()
        val t1 = maxOf(points.last().at.toEpochMilli(), t0 + 86_400_000L)
        fun x(t: Instant) = pl + ((t.toEpochMilli() - t0).toFloat() / (t1 - t0)) * iw
        drawLine(c.muted, Offset(pl * sx, y(1.0) * sy), Offset((320f - pr) * sx, y(1.0) * sy), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * sx, 3f * sx)))
        for ((v, text) in listOf(1.0 to "1.0", 2.0 to "2.0")) {
            val m = measurer.measure(text, label)
            drawText(m, topLeft = Offset((pl - 5f) * sx - m.size.width, y(v) * sy - m.size.height / 2f))
        }
        val used = points.filter { it.used }
        if (used.size >= 2) {
            val path = androidx.compose.ui.graphics.Path()
            used.forEachIndexed { i, p -> val o = Offset(x(p.at) * sx, y(p.ratio.coerceIn(0.4, 2.35)) * sy); if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y) }
            drawPath(path, c.yMark, style = Stroke(width = 1.5f * sx))
        }
        var lastLabelX = -100f
        points.forEach { p ->
            val o = Offset(x(p.at) * sx, y(p.ratio.coerceIn(0.4, 2.35)) * sy)
            val r = 4f * sx
            rotate(45f, o) {
                drawRect(if (p.used) c.yMark else c.card, topLeft = Offset(o.x - r, o.y - r), size = Size(r * 2, r * 2))
                drawRect(c.yMark, topLeft = Offset(o.x - r, o.y - r), size = Size(r * 2, r * 2), style = Stroke(width = 1.4f * sx))
            }
            if (o.x - lastLabelX > 34f * sx) {
                val d = p.at.atZone(zone)
                val m = measurer.measure("${d.monthValue}/${d.dayOfMonth}", TextStyle(fontSize = 8.5.sp, color = c.muted))
                drawText(m, topLeft = Offset(o.x - m.size.width / 2f, size.height - m.size.height - 1f))
                lastLabelX = o.x
            }
        }
    }
}

