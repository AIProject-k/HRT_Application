package com.hormonelog.app.feature.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hormonelog.app.feature.dashboard.ChartRange
import com.hormonelog.app.feature.dashboard.DashboardState
import com.hormonelog.app.feature.dashboard.HormoneSeries
import com.hormonelog.app.feature.dashboard.CalibrationStatus
import com.hormonelog.app.feature.dashboard.fmtShort
import com.hormonelog.app.feature.dashboard.fmtTime
import com.hormonelog.app.feature.dashboard.nearestLabWithin
import com.hormonelog.app.ui.components.Disclaimer
import com.hormonelog.app.ui.components.HlCard
import com.hormonelog.app.ui.theme.HlColor
import com.hormonelog.app.ui.theme.HlType

@Composable
fun FlowScreen(
    state: DashboardState,
    now: java.time.Instant,
    zone: java.time.ZoneId,
    onSeries: (HormoneSeries) -> Unit,
    onRange: (ChartRange) -> Unit,
    onScrub: (Float?) -> Unit,
    onGoModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isE2 = state.series == HormoneSeries.E2
    val window = remember(state.range, now) { chartWindow(state.range.days, now) }
    val curves = remember(state.doses, state.regimens, state.labs, state.range, now) {
        computeCurves(state.doses, state.regimens, state.labs, now, window.first, window.second)
    }
    val model = CalibrationStatus.of(curves.cal.includedLabIds.size, curves.cal.exposureScale, curves.canEstimate)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text("예상 흐름", style = HlType.ScreenTitle, color = HlColor.TextPrimary)
            Text(
                model.chip,
                style = HlType.Badge,
                color = HlColor.Orange,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(HlColor.OrangeTintSoft)
                    .clickable(onClick = onGoModel)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }

        // series tabs
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SeriesTab("에스트라디올 (E2)", isE2, Modifier.weight(1f)) { onSeries(HormoneSeries.E2) }
            SeriesTab("테스토스테론", !isE2, Modifier.weight(1f)) { onSeries(HormoneSeries.TT) }
        }

        // chart card
        HlCard(
            borderColor = HlColor.Border06,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (isE2) "예상 E2 (pg/mL)" else "예상 Total T (ng/dL)",
                    style = HlType.LabelStrong,
                    color = HlColor.TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    RangeChip("1주", state.range == ChartRange.WEEK) { onRange(ChartRange.WEEK) }
                    RangeChip("1개월", state.range == ChartRange.MONTH) { onRange(ChartRange.MONTH) }
                    RangeChip("3개월", state.range == ChartRange.QUARTER) { onRange(ChartRange.QUARTER) }
                }
            }

            EstimateChart(
                doses = state.doses,
                labs = state.labs,
                series = state.series,
                rangeDays = state.range.days,
                now = now,
                zone = zone,
                curve = curves.forSeries(state.series),
                scrubFraction = state.scrubFraction,
                onScrub = onScrub,
            )

            Box(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 34.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                Text(scrubText(state, curves, now, zone), style = HlType.BodySm, color = HlColor.TextMuted)
            }
        }

        LegendCard()

        Disclaimer("예상 곡선은 기록만으로 계산한 참고용 추정입니다. 용량 판단이나 변경은 의료진과 상의하세요.")
    }
}

private fun scrubText(state: DashboardState, curves: Curves, now: java.time.Instant, zone: java.time.ZoneId): String {
    val f = state.scrubFraction ?: return "그래프를 좌우로 문지르면 그 시점의 예상값을 볼 수 있어요"
    val t = scrubInstant(state.range.days, now, f)
    val unit = if (state.series == HormoneSeries.E2) "pg/mL" else "ng/dL"
    val nearest = curves.forSeries(state.series)?.points
        ?.minByOrNull { kotlin.math.abs(it.at.toEpochMilli() - t.toEpochMilli()) }
    var text = if (nearest != null) {
        "${fmtShort(t, zone)} ${fmtTime(t, zone)} · 예상 ${nearest.median.toInt()} $unit " +
            "(범위 ${nearest.lower.toInt()}–${nearest.upper.toInt()})"
    } else {
        "${fmtShort(t, zone)} ${fmtTime(t, zone)} · 이 시점 예상값 없음 · 근거 자료 필요"
    }
    nearestLabWithin(state, t, state.series, hours = 24)?.let { (_, label) -> text += " · $label" }
    return text
}

@Composable
private fun SeriesTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text = label,
        style = HlType.CardTitle.copy(fontSize = 13.sp),
        color = if (selected) HlColor.Teal else HlColor.TextMuted,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) HlColor.TealTintSoft else HlColor.Card)
            .border(1.dp, if (selected) HlColor.Teal else HlColor.Border06, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
    )
}

@Composable
private fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = HlType.Chip.copy(fontSize = 11.5.sp),
        color = if (selected) HlColor.TextPrimary else HlColor.TextDim,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) HlColor.KeyAlt else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun LegendCard() {
    HlCard(background = HlColor.CardAlt, borderColor = HlColor.Border06, corner = 16) {
        Text("그래프 읽는 법", style = HlType.LabelStrong, color = HlColor.TextSecondary)
        LegendRow({ SolidLine() }, "실선", "기록된 투약으로 계산한 과거 예상값")
        LegendRow({ DashedLine() }, "점선", "앞으로의 예측값")
        LegendRow({ BandSwatch() }, "옅은 띠", "불확실성 범위. 넓을수록 덜 확실해요")
        LegendRow({ DiamondSwatch() }, "노란 마름모", "실제 혈액검사 값 (예상값 아님)")
        LegendRow({ TickSwatch() }, "아래 눈금", "투약 시점 (💉 기록, ✕ 누락)")
    }
}

@Composable
private fun LegendRow(swatch: @Composable () -> Unit, lead: String, rest: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Box(modifier = Modifier.size(width = 20.dp, height = 12.dp), contentAlignment = Alignment.Center) { swatch() }
        Text(
            buildLegendText(lead, rest),
            style = HlType.BodySm,
            color = HlColor.TextSecondary,
        )
    }
}

private fun buildLegendText(lead: String, rest: String) = "$lead  $rest"

@Composable private fun SolidLine() = Box(Modifier.size(width = 20.dp, height = 2.dp).background(HlColor.Teal))

@Composable private fun DashedLine() = Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    repeat(4) { Box(Modifier.size(width = 3.dp, height = 2.dp).background(HlColor.Teal)) }
}

@Composable private fun BandSwatch() = Box(
    Modifier.size(width = 20.dp, height = 11.dp).clip(RoundedCornerShape(3.dp)).background(HlColor.TealTint),
)

@Composable private fun DiamondSwatch() = Box(
    Modifier.size(10.dp).rotate(45f).background(HlColor.Yellow),
)

@Composable private fun TickSwatch() = Box(Modifier.size(width = 2.dp, height = 12.dp).background(HlColor.Blue))
