package com.hormonelog.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hormonelog.app.analysis.Analysis
import com.hormonelog.app.analysis.HomeLogic
import com.hormonelog.app.analysis.chartModelFor
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.reasonText
import com.hormonelog.app.feature.common.shortLabel
import com.hormonelog.app.feature.common.doseSummary
import com.hormonelog.app.feature.flow.EstimateChart
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.HormoneSeries
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlBanner
import com.hormonelog.app.ui.kit.HlButton
import com.hormonelog.app.ui.kit.HlButtonKind
import com.hormonelog.app.ui.kit.HlCard
import com.hormonelog.app.ui.kit.HlDisclaimer
import com.hormonelog.app.ui.kit.HlIconButton
import com.hormonelog.app.ui.kit.HlNotice
import com.hormonelog.app.ui.kit.HlPill
import com.hormonelog.app.ui.kit.HlStatTile
import com.hormonelog.app.ui.kit.HlTextAction
import com.hormonelog.app.ui.kit.MeasuredMark
import com.hormonelog.app.ui.kit.ScreenScroll
import com.hormonelog.app.ui.kit.ScreenTitle
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.app.ui.theme.HeroTracking
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.isAntiandrogen
import com.hormonelog.core.modelengine.EstimatePoint
import java.time.temporal.ChronoUnit

/** Everything the home screen can ask the app to do. */
class HomeActions(
    val onStorage: () -> Unit,
    val onFirstDose: () -> Unit,
    val onNewPlan: () -> Unit,
    val onQuickLog: () -> Unit,
    val onOtherCombo: () -> Unit,
    val onLab: () -> Unit,
    val onBackup: () -> Unit,
    val onHideBanner: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(s: AppState, fmt: Fmt, actions: HomeActions, modifier: Modifier = Modifier) {
    val c = Hl.colors
    val now = fmt.now
    val zone = fmt.zone
    val status = s.settings.gonadalStatus
    val bucket = now.epochSecond / 600
    val calibration = remember(s.doses, s.labs, status) { Analysis.calibrate(s.doses, s.labs, status) }
    val from = now.minus(14, ChronoUnit.DAYS)
    val to = now.plus(7, ChronoUnit.DAYS)
    val curves = remember(s.doses, s.regimens, s.labs, status, calibration, bucket) {
        Analysis.curves(s.doses, s.regimens, s.labs, status, now, from, to, zone, calibration)
    }
    val e2 = remember(s.doses, s.regimens, calibration, bucket) { Analysis.e2At(s.doses, s.regimens, calibration, now, now, zone) }
    val tt = remember(s.doses, s.labs, status, calibration, bucket) { Analysis.ttAt(s.doses, s.labs, status, calibration, now) }
    val cycle = remember(s.doses, s.regimens, calibration, bucket) { Analysis.cycle(s.doses, s.regimens, calibration, now, zone) }
    val next = remember(s.regimens, s.doses, bucket) { HomeLogic.nextDose(s.regimens, s.doses, now, zone) }
    val lastLab = remember(s.labs) { HomeLogic.lastLab(s.labs) }
    val lastCombo = remember(s.doses) { s.doses.filter { it.status.wasTaken && !it.drug.isAntiandrogen }.maxByOrNull { it.occurredAt } }
    val nudge = HomeLogic.backupNudgeDays(s.settings, s.hasRecords, now)

    ScreenScroll(modifier) {
        ScreenTitle(
            title = "홈",
            subtitle = fmt.dateDay(now),
            trailing = {
                Row(
                    Modifier
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(999.dp))
                        .clickable(role = Role.Button, onClick = actions.onStorage)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    HlIcon(HlIcon.Phone, size = 14.dp, tint = c.muted, strokeWidth = 2f)
                    HlText("기기 안에만", size = HlSize.t12, weight = FontWeight.SemiBold, color = c.muted)
                }
            },
        )

        if (!s.hasRecords) {
            EmptyHome(actions)
        } else {
            if (nudge != null) BackupBanner(nudge, actions)

            // ── hero: the estimate right now ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(HlRadius.hero))
                    .background(c.card)
                    .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 14.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (e2 != null) {
                            "예상 E2 ${e2.median.toInt()} 피코그램 퍼 밀리리터, 범위 ${e2.lower.toInt()}에서 ${e2.upper.toInt()}, 실측 아님"
                        } else {
                            "예상 E2 없음"
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    HlText("지금 예상 E2", size = HlSize.t14, weight = FontWeight.SemiBold, color = c.muted, modifier = Modifier.weight(1f))
                    HlPill("예상 · 실측 아님", c.teal)
                }
                FlowRow(itemVerticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HlText(e2?.median?.toInt()?.toString() ?: "—", size = HlSize.t40, weight = FontWeight.Bold, color = c.teal, letterSpacing = HeroTracking, lineHeight = 1.1f, tabular = true)
                    HlText("pg/mL", size = HlSize.t16, weight = FontWeight.SemiBold, color = c.teal, modifier = Modifier.padding(bottom = 6.dp))
                    if (e2 != null) {
                        HlText("범위 ${e2.lower.toInt()}–${e2.upper.toInt()}", size = HlSize.t14, color = c.muted, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp), tabular = true)
                    }
                }
                EstimateChart(
                    model = chartModelFor(HormoneSeries.E2, curves, s.doses, s.labs, from, to, now, guide = false),
                    fmt = fmt, compact = true,
                    modifier = Modifier.padding(top = 4.dp),
                    summary = "최근 2주와 앞으로 1주의 예상 E2 곡선",
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LegendItem(c.muted, "예상 · 범위") { Box(Modifier.size(width = 14.dp, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(c.tealSoft).border(width = 2.dp, color = c.teal, shape = RoundedCornerShape(4.dp))) }
                    LegendItem(c.muted, "실측") { MeasuredMark(8.dp) }
                    LegendItem(c.muted, "투약") { Box(Modifier.size(width = 3.dp, height = 10.dp).background(c.blue)) }
                    LegendItem(c.muted, "예정") { Box(Modifier.size(width = 3.dp, height = 10.dp).border(1.dp, c.blue)) }
                }
            }

            if (curves.excluded.isNotEmpty()) {
                val kinds = curves.excluded.groupBy { it.dose.drug }
                HlBanner(HlNotice.Warn) {
                    val first = curves.excluded.first()
                    HlText(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.orange)) { append("${exclusionName(first.dose.drug, kinds.size)} ${curves.excluded.size}건은 곡선에서 제외") }
                            append(" · ${first.reason.reasonText}")
                        },
                        size = HlSize.t13, lineHeight = 1.5f,
                    )
                }
            }

            if (!curves.ttHidden && tt != null) TotalTCard(tt)

            // ── three tiles ──
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HlStatTile("다음 투약", Modifier.weight(1f).fillMaxHeight(), value = {
                    if (next == null) {
                        HlText("일정 없음", size = HlSize.t14, weight = FontWeight.Bold, lineHeight = 1.3f)
                    } else {
                        HlText(
                            "${fmt.dateDay(next.at)} ${fmt.time(next.at)}",
                            size = HlSize.t14, weight = FontWeight.Bold, lineHeight = 1.3f,
                            color = if (next.overdue) c.orange else c.text,
                        )
                    }
                }, detail = when {
                    next == null -> "반복 일정을 만들면 알려드려요"
                    next.overdue -> "예정 시각이 지났어요 · ${doseSummary(next.regimen.drug, next.regimen.route, next.regimen.amountEntered, next.regimen.enteredUnit)}"
                    else -> doseSummary(next.regimen.drug, next.regimen.route, next.regimen.amountEntered, next.regimen.enteredUnit)
                })
                HlStatTile("마지막 검사", Modifier.weight(1f).fillMaxHeight(), value = {
                    if (lastLab == null) {
                        HlText("기록 없음", size = HlSize.t14, weight = FontWeight.Bold)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            MeasuredMark(7.dp)
                            HlText("${com.hormonelog.app.state.plainNumber(lastLab.reported)} ${lastLab.unit}", size = HlSize.t14, weight = FontWeight.Bold, color = c.yellow, lineHeight = 1.3f)
                        }
                    }
                }, detail = lastLab?.lab?.collectedAt?.let { "실측 E2 · ${fmt.ago(it)}" } ?: "검사 결과를 입력해 보세요")
                HlStatTile("이번 주기 · 예상", Modifier.weight(1f).fillMaxHeight(), value = {
                    if (cycle == null) {
                        HlText("—", size = HlSize.t14, weight = FontWeight.Bold)
                    } else {
                        HlText("피크 ${cycle.peak.median.toInt()}\n트로프 ${cycle.trough.median.toInt()}", size = HlSize.t13, weight = FontWeight.Bold, lineHeight = 1.35f, tabular = true)
                    }
                }, detail = cycle?.let { "범위 ${it.peak.lower.toInt()}–${it.peak.upper.toInt()} / ${it.trough.lower.toInt()}–${it.trough.upper.toInt()}" } ?: "투약이 2번 이상이면 보여요")
            }

            // ── actions ──
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lastCombo != null) {
                    Column(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp)
                            .clip(RoundedCornerShape(HlRadius.button))
                            .background(c.teal)
                            .clickable(role = Role.Button, onClick = actions.onQuickLog)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        HlText("바로 기록 · 1탭", size = HlSize.t12, weight = FontWeight.SemiBold, color = c.onTeal.copy(alpha = 0.85f))
                        HlText("${doseSummary(lastCombo.drug, lastCombo.route, lastCombo.amountEntered, lastCombo.enteredUnit).replace(" · ", " ")} · 지금", size = HlSize.t14, weight = FontWeight.Bold, color = c.onTeal)
                    }
                } else {
                    HlButton("투약 기록하기", actions.onFirstDose, Modifier.weight(1f))
                }
                Column(
                    Modifier
                        .widthIn(min = 56.dp)
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(HlRadius.button))
                        .background(c.card)
                        .border(1.dp, c.line, RoundedCornerShape(HlRadius.button))
                        .clickable(role = Role.Button, onClick = actions.onOtherCombo)
                        .padding(horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    HlIcon(HlIcon.Plus, size = 18.dp, strokeWidth = 2f)
                    HlText("다른 조합", size = HlSize.t13, weight = FontWeight.SemiBold)
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .clip(RoundedCornerShape(HlRadius.button))
                    .border(1.dp, c.line, RoundedCornerShape(HlRadius.button))
                    .clickable(role = Role.Button, onClick = actions.onLab)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                MeasuredMark(8.dp)
                HlText("검사 결과 입력", size = HlSize.t14, weight = FontWeight.SemiBold)
            }
        }
        HlDisclaimer(Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun EmptyHome(actions: HomeActions) {
    val c = Hl.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.hero)).background(c.card).padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HlIcon(HlIcon.Flow, size = 40.dp, tint = c.teal, strokeWidth = 1.5f)
        HlText("아직 기록이 없어요", size = HlSize.t18, weight = FontWeight.Bold, lineHeight = 1.4f)
        HlText("투약을 1건 이상 기록하면 예상 E2 곡선이 그려져요. 지금은 보여드릴 예상값이 없어요.", size = HlSize.t14, color = c.muted, lineHeight = 1.6f)
        Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HlButton("첫 투약 기록하기", actions.onFirstDose, Modifier.fillMaxWidth(), minHeight = 52.dp, size = HlSize.t14)
            HlButton("반복 일정 만들기", actions.onNewPlan, Modifier.fillMaxWidth(), kind = HlButtonKind.Secondary, minHeight = 52.dp)
        }
    }
}

@Composable
private fun BackupBanner(days: Int, actions: HomeActions) {
    val c = Hl.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HlRadius.button))
            .background(c.orangeSoft)
            .padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
            .semantics { contentDescription = "백업 알림" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HlIcon(HlIcon.Clock, size = 18.dp, tint = c.orange, strokeWidth = 2f)
        HlText(if (days < 0) "아직 백업한 적이 없어요" else "마지막 백업 ${days}일 전", modifier = Modifier.weight(1f), size = HlSize.t13, weight = FontWeight.SemiBold, color = c.orange, lineHeight = 1.4f)
        HlTextAction("백업하기", actions.onBackup, color = c.orange)
        HlIconButton(HlIcon.Close, "배너 닫기", actions.onHideBanner, tint = c.orange, size = 40.dp, iconSize = 16.dp)
    }
}

@Composable
private fun TotalTCard(tt: EstimatePoint) {
    val c = Hl.colors
    HlCard(radius = HlRadius.card, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {
                contentDescription = "예상 Total T ${tt.median.toInt()} 나노그램 퍼 데시리터, 범위 ${tt.lower.toInt()}에서 ${tt.upper.toInt()}"
            }) {
                HlText("지금 예상 Total T", size = HlSize.t13, weight = FontWeight.SemiBold, color = c.muted)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    HlText(tt.median.toInt().toString(), size = HlSize.t22, weight = FontWeight.Bold, color = c.blue, tabular = true)
                    HlText("ng/dL", size = HlSize.t13, weight = FontWeight.SemiBold, color = c.blue, modifier = Modifier.padding(bottom = 2.dp))
                    HlText("범위 ${tt.lower.toInt()}–${tt.upper.toInt()}", size = HlSize.t13, color = c.muted, modifier = Modifier.padding(bottom = 2.dp), tabular = true)
                }
            }
            HlPill("예상", c.blue)
        }
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String, swatch: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        swatch()
        HlText(label, size = HlSize.t12, color = color)
    }
}

private fun exclusionName(drug: Drug, kinds: Int): String = if (kinds == 1) drug.shortLabel else "일부 투약"
