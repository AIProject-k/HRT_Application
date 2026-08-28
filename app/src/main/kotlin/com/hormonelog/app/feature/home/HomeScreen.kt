package com.hormonelog.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hormonelog.app.feature.dashboard.CalibrationStatus
import com.hormonelog.app.feature.dashboard.DashboardState
import com.hormonelog.app.feature.dashboard.homeSummary
import com.hormonelog.app.feature.dashboard.timelineEntries
import com.hormonelog.app.feature.dashboard.todayLabel
import com.hormonelog.app.feature.flow.EstimateChart
import com.hormonelog.app.feature.flow.chartWindow
import com.hormonelog.app.feature.flow.computeCurves
import com.hormonelog.app.feature.flow.e2MedianAt
import com.hormonelog.app.feature.common.RecentRow
import com.hormonelog.app.ui.components.HlCard
import com.hormonelog.app.ui.components.SectionHeader
import com.hormonelog.app.ui.components.StatTile
import com.hormonelog.app.ui.theme.HlColor
import com.hormonelog.app.ui.theme.HlType
import java.time.Instant
import java.time.ZoneId

@Composable
fun HomeScreen(
    state: DashboardState,
    now: Instant,
    zone: ZoneId,
    onOpenDose: () -> Unit,
    onOpenLab: () -> Unit,
    onGoFlow: () -> Unit,
    onGoTimeline: () -> Unit,
    onGoModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Home mini-chart is always a 30-day window.
    val window = remember(now) { chartWindow(30, now) }
    val curves = remember(state.doses, state.regimens, state.labs, now) {
        computeCurves(state.doses, state.regimens, state.labs, now, window.first, window.second)
    }
    val e2Now = remember(state.doses, state.labs, now) { e2MedianAt(state.doses, state.labs, now) }
    val summary = homeSummary(state, now).copy(
        e2Now = e2Now?.let { it.toInt().toString() } ?: "—",
    )
    val model = CalibrationStatus.of(curves.cal.includedLabIds.size, curves.cal.exposureScale, curves.canEstimate)
    val recent = timelineEntries(state, now, zone, withMethod = false).take(3)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // header
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("호르몬로그", style = HlType.ScreenTitle, color = HlColor.TextPrimary)
                Text(todayLabel(now, zone), style = HlType.Caption, color = HlColor.TextDim)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(HlColor.ChipIdle)
                    .border(1.dp, HlColor.Border08, RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text("🔒", fontSize = 11.sp)
                Text("이 기기에만 저장", style = HlType.Caption, color = HlColor.TextMuted)
            }
        }

        HeroCard(e2Now = summary.e2Now, nextDose = summary.nextDose, lastLab = summary.lastLab)

        // action buttons
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionButton(
                modifier = Modifier.weight(1f),
                background = HlColor.Teal,
                contentColor = HlColor.OnTeal,
                border = false,
                icon = "💉",
                title = "투약 기록",
                subtitle = "최근 조합 그대로 1탭",
                subtitleColor = HlColor.OnTeal.copy(alpha = 0.68f),
                onClick = onOpenDose,
            )
            ActionButton(
                modifier = Modifier.weight(1f),
                background = HlColor.ButtonIdle,
                contentColor = HlColor.TextPrimary,
                border = true,
                icon = "🧪",
                title = "검사 결과",
                subtitle = "E2 · Total T 입력",
                subtitleColor = HlColor.TextMuted,
                onClick = onOpenLab,
            )
        }

        // model banner
        HlCard(
            modifier = Modifier.clickable(onClick = onGoModel),
            background = HlColor.RowIdle,
            borderColor = HlColor.Border06,
            corner = 16,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(HlColor.OrangeTintSoft),
                    contentAlignment = Alignment.Center,
                ) { Text("◐", fontSize = 15.sp, color = HlColor.Orange) }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(model.title, style = HlType.CardTitle, color = HlColor.Orange)
                    Text(model.subtitle, style = HlType.BodySm, color = HlColor.TextMuted)
                }
                Text("›", fontSize = 16.sp, color = HlColor.TextFaint)
            }
        }

        // flow preview
        HlCard(
            modifier = Modifier.clickable(onClick = onGoFlow),
            borderColor = HlColor.Border06,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("예상 흐름", style = HlType.SectionHeader, color = HlColor.TextPrimary)
                Text("자세히 ›", style = HlType.Label, color = HlColor.Teal)
            }
            EstimateChart(
                doses = state.doses,
                labs = state.labs,
                series = com.hormonelog.app.feature.dashboard.HormoneSeries.E2,
                rangeDays = 30,
                now = now,
                zone = zone,
                curve = curves.e2,
                scrubFraction = null,
                onScrub = {},
                compact = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendItem(dashed = true, label = "예상 곡선")
                LegendItem(dashed = false, label = "실제 검사값")
            }
        }

        SectionHeader("최근 기록", trailing = "전체 보기 ›", onTrailingClick = onGoTimeline)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (recent.isEmpty()) {
                Text(
                    "홈에서 투약이나 검사 결과를 추가해 보세요.",
                    style = HlType.BodySm,
                    color = HlColor.TextDim,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            } else {
                recent.forEach { RecentRow(it) }
            }
        }
    }
}

@Composable
private fun HeroCard(e2Now: String, nextDose: String, lastLab: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(HlColor.HeroGradientTop, HlColor.HeroGradientBottom)))
            .border(1.dp, HlColor.TealTint, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier
                    .size(8.dp)
                    .border(1.5.dp, HlColor.Teal, RoundedCornerShape(2.dp)),
            )
            Text("예상 수치 · 실제 검사값 아님", style = HlType.LabelStrong, color = HlColor.Teal)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(e2Now, style = HlType.HeroNumber, color = HlColor.TextPrimary)
            Text(
                "pg/mL · 예상 E2",
                style = HlType.HeroUnit,
                color = HlColor.TextMuted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("다음 투약", nextDose, modifier = Modifier.weight(1f))
            StatTile("마지막 검사", lastLab, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier,
    background: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    border: Boolean,
    icon: String,
    title: String,
    subtitle: String,
    subtitleColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .heightIn(min = 88.dp)
            .clip(shape)
            .background(background)
            .then(if (border) Modifier.border(1.dp, HlColor.Border10, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Text(icon, fontSize = 19.sp)
        Text(title, style = HlType.ButtonLabel.copy(fontSize = 16.sp), color = contentColor)
        Text(subtitle, style = HlType.Caption, color = subtitleColor)
    }
}

@Composable
private fun LegendItem(dashed: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (dashed) {
            Box(
                Modifier
                    .size(width = 14.dp, height = 2.dp)
                    .background(HlColor.Teal),
            )
        } else {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(50))
                    .background(HlColor.Yellow),
            )
        }
        Text(label, style = HlType.Caption, color = HlColor.TextMuted)
        Spacer(Modifier.height(0.dp))
    }
}
