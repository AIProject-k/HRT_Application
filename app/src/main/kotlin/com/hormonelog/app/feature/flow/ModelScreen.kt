package com.hormonelog.app.feature.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hormonelog.app.analysis.Analysis
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.fix
import com.hormonelog.app.feature.common.text
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.plainNumber
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlDisclaimer
import com.hormonelog.app.ui.kit.HlDivider
import com.hormonelog.app.ui.kit.HlSectionLabel
import com.hormonelog.app.ui.kit.HlTextAction
import com.hormonelog.app.ui.kit.HlTopBar
import com.hormonelog.app.ui.kit.MeasuredMark
import com.hormonelog.app.ui.kit.ScreenScroll
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.isAntiandrogen
import com.hormonelog.core.modelengine.CalibrationEngine
import com.hormonelog.core.modelengine.CalibrationGroup
import com.hormonelog.core.modelengine.CalibrationResult
import com.hormonelog.core.modelengine.calibrationGroup
import java.util.UUID
import kotlin.math.roundToInt

class ModelActions(
    val back: () -> Unit,
    val evidence: () -> Unit,
    val editLab: (UUID) -> Unit,
    val gonadal: () -> Unit,
)

private fun groupName(g: CalibrationGroup) = when (g) {
    CalibrationGroup.INJECTION -> "주사"
    CalibrationGroup.ORAL -> "경구·설하"
    CalibrationGroup.PATCH -> "패치"
}

private class LabLine(val labId: UUID, val title: String, val sub: String, val ratio: String, val why: String?, val fix: String?)

@Composable
fun ModelScreen(s: AppState, fmt: Fmt, actions: ModelActions) {
    val c = Hl.colors
    val status = s.settings.gonadalStatus
    val calibration = remember(s.doses, s.labs, status) { Analysis.calibrate(s.doses, s.labs, status) }
    val groupsInUse = remember(s.doses) {
        CalibrationGroup.entries.filter { g -> s.doses.any { it.status.wasTaken && !it.drug.isAntiandrogen && it.route.calibrationGroup() == g } }
    }
    val lines = remember(s.labs, s.doses, s.regimens, calibration) { labLines(s, calibration, fmt) }

    Column(Modifier.fillMaxWidth()) {
        HlTopBar("모델 상태", actions.back)
        ScreenScroll(padding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 24.dp), gap = 14.dp, bottomInset = true) {
            HlSectionLabel("경로별 보정")
            if (groupsInUse.isEmpty()) {
                HlText("투약을 기록하면 경로마다 보정 상태가 여기에 보여요.", size = HlSize.t13, color = c.muted)
            }
            groupsInUse.forEach { g -> GroupCard(g, calibration) }

            TotalTCard(calibration, status, actions.gonadal)

            HlSectionLabel("보정에 쓴 검사")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card)) {
                val used = lines.first
                if (used.isEmpty()) {
                    HlText("아직 보정에 쓴 검사가 없어요", size = HlSize.t13, color = c.muted, modifier = Modifier.padding(16.dp))
                }
                used.forEachIndexed { i, l ->
                    if (i > 0) HlDivider()
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MeasuredMark(9.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            HlText(l.title, size = HlSize.t14, weight = FontWeight.SemiBold)
                            HlText(l.sub, size = HlSize.t12, color = c.muted)
                        }
                        HlText(l.ratio, size = HlSize.t13, weight = FontWeight.Bold, tabular = true)
                    }
                }
            }

            if (lines.second.isNotEmpty()) {
                HlSectionLabel("보정에 안 쓴 검사")
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card)) {
                    lines.second.forEachIndexed { i, l ->
                        if (i > 0) HlDivider()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.padding(top = 6.dp)) { MeasuredMark(9.dp, hollow = true) }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                HlText(l.title, size = HlSize.t14, weight = FontWeight.SemiBold)
                                if (l.why != null) HlText(l.why, size = HlSize.t13, weight = FontWeight.SemiBold, color = c.orange)
                                if (l.fix != null) HlTextAction(l.fix, { actions.editLab(l.labId) })
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(HlRadius.button)).background(c.card).border(1.dp, c.line, RoundedCornerShape(HlRadius.button))
                    .clickable(role = Role.Button, onClick = actions.evidence).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    HlText("근거 탐색기", size = HlSize.t14, weight = FontWeight.Bold)
                    HlText("곡선에 쓰인 파라미터와 출처", size = HlSize.t12, color = c.muted)
                }
                HlIcon(HlIcon.Chevron, size = 16.dp, tint = c.muted, strokeWidth = 2f)
            }
            HlDisclaimer()
        }
    }
}

@Composable
private fun GroupCard(g: CalibrationGroup, calibration: CalibrationResult) {
    val c = Hl.colors
    val cal = calibration.groups[g]
    val atLimit = cal?.atLimit == true
    val pct = cal?.let { ((it.scale - 1.0) * 100).roundToInt() } ?: 0
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card)
            .then(if (atLimit) Modifier.border(1.dp, c.orange, RoundedCornerShape(HlRadius.card)) else Modifier).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            HlText("${groupName(g)} · E2", size = HlSize.t14, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            when {
                cal == null -> HlText("보정 안 됨", size = HlSize.t12, weight = FontWeight.Bold, color = c.muted)
                atLimit -> HlText("한계값에 걸림", size = HlSize.t12, weight = FontWeight.Bold, color = c.orange)
                else -> HlText("보정됨 · 검사 ${cal.labCount}건", size = HlSize.t12, weight = FontWeight.Bold, color = c.teal)
            }
        }
        if (cal == null) {
            HlText("검사값이 없어서 문헌 평균으로 그려요", size = HlSize.t16, weight = FontWeight.Bold)
            HlText("채혈 시각이 있는 E2 검사를 기록하면 이 경로의 곡선을 내 검사에 맞춰요.", size = HlSize.t13, color = c.muted, lineHeight = 1.5f)
        } else {
            HlText(
                when {
                    pct > 0 -> "예상보다 ${if (atLimit) "" else "약 "}${pct}% 높게 보정${if (atLimit) " (상한)" else ""}"
                    pct < 0 -> "예상보다 ${if (atLimit) "" else "약 "}${-pct}% 낮게 보정${if (atLimit) " (하한)" else ""}"
                    else -> "보정 없이 문헌 평균 그대로예요"
                },
                size = HlSize.t16, weight = FontWeight.Bold,
            )
            HlText(
                if (atLimit) "검사 ${cal.labCount}건이 예상과 크게 달라 보정 한계에서 멈췄어요. 실제 차이는 더 클 수 있어요. 단위나 채혈 시각을 다시 확인해 보세요."
                else "보정 폭은 −${((1 - CalibrationEngine.SCALE_MIN) * 100).roundToInt()}%~+${((CalibrationEngine.SCALE_MAX - 1) * 100).roundToInt()}%로 제한돼요. 검사가 더 쌓이면 범위가 좁아져요.",
                size = HlSize.t13, color = if (atLimit) c.text else c.muted, lineHeight = 1.5f,
            )
        }
    }
}

@Composable
private fun TotalTCard(calibration: CalibrationResult, status: GonadalStatus, onChange: () -> Unit) {
    val c = Hl.colors
    val tt = calibration.tt
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            HlText("Total T", size = HlSize.t14, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            HlText(
                when {
                    status == GonadalStatus.DECLINED -> "숨김"
                    tt != null -> "보정됨 · 검사 ${tt.labCount}건"
                    else -> "보정 안 됨"
                },
                size = HlSize.t12, weight = FontWeight.Bold, color = if (tt != null && status != GonadalStatus.DECLINED) c.teal else c.muted,
            )
        }
        when {
            status == GonadalStatus.DECLINED -> {
                HlText("고환 정보를 '말하고 싶지 않음'으로 두어서 Total T 곡선을 그리지 않아요.", size = HlSize.t13, color = c.muted, lineHeight = 1.5f)
                HlTextAction("고환 정보 바꾸기", onChange)
            }
            tt != null -> {
                val pct = ((tt.scale - 1.0) * 100).roundToInt()
                HlText(
                    when { pct > 0 -> "예상보다 약 ${pct}% 높게 보정"; pct < 0 -> "예상보다 약 ${-pct}% 낮게 보정"; else -> "보정 없이 문헌 평균 그대로예요" },
                    size = HlSize.t16, weight = FontWeight.Bold,
                )
            }
            else -> HlText("문헌 기본값으로 그려요. T 검사가 2건 이상 쌓이면 보정해요.", size = HlSize.t13, color = c.muted, lineHeight = 1.5f)
        }
    }
}

/** Used labs first, then the ones left out with the reason — one line per analyte per lab, newest first. */
private fun labLines(s: AppState, calibration: CalibrationResult, fmt: Fmt): Pair<List<LabLine>, List<LabLine>> {
    val used = ArrayList<LabLine>()
    val unused = ArrayList<LabLine>()
    for (lab in s.labs.sortedByDescending { it.collectedAt }) {
        val cal = calibration.labs[lab.id] ?: continue
        val at = lab.collectedAt
        val date = at?.let { fmt.date(it) } ?: "채혈 시각 없음"
        val since = at?.let { t -> Analysis.lastEstrogenBefore(s.doses, t)?.let { (t.toEpochMilli() - it.occurredAt.toEpochMilli()) / 3_600_000.0 } }
        for (analyte in listOf(Analyte.ESTRADIOL, Analyte.TOTAL_TESTOSTERONE)) {
            val value = lab.analytes.firstOrNull { it.analyte == analyte } ?: continue
            val a = if (analyte == Analyte.ESTRADIOL) cal.e2 else cal.tt
            if (a == null) continue
            val name = if (analyte == Analyte.ESTRADIOL) "E2" else "Total T"
            val title = "$name ${plainNumber(value.reportedValue)} ${value.reportedUnit} · $date"
            if (a.used) {
                val measured = Analysis.canonical(value)
                val ratio = if (analyte == Analyte.ESTRADIOL) {
                    at?.let { Analysis.e2At(s.doses, s.regimens, CalibrationResult.NONE, it, fmt.now, fmt.zone)?.median }?.let { e -> measured?.div(e) }
                } else {
                    a.expected?.median?.let { e -> measured?.div(e) }
                }
                used += LabLine(
                    lab.id, title,
                    (a.group?.let { groupName(it) } ?: "Total T") + (since?.let { " · 투약 후 ${fmt.hoursSince(it)}" } ?: ""),
                    ratio?.let { "×${"%.2f".format(it)}" } ?: "",
                    null, null,
                )
            } else {
                unused += LabLine(lab.id, title, "", "", a.exclusion?.text, a.exclusion?.fix)
            }
        }
    }
    return used to unused
}

