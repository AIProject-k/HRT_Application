package com.hormonelog.app.feature.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.fix
import com.hormonelog.app.feature.common.text
import com.hormonelog.app.state.E2Outcome
import com.hormonelog.app.state.LabResultSummary
import com.hormonelog.app.state.TtOutcome
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlButton
import com.hormonelog.app.ui.kit.HlDisclaimer
import com.hormonelog.app.ui.kit.HlDivider
import com.hormonelog.app.ui.kit.HlIconButton
import com.hormonelog.app.ui.kit.MeasuredMark
import com.hormonelog.app.ui.kit.ScreenScroll
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.core.domain.E2Unit
import com.hormonelog.core.domain.TUnit
import com.hormonelog.core.modelengine.CalibrationGroup
import com.hormonelog.core.modelengine.CycleSpot

/**
 * What just happened to the lab that was saved: what the model expected at that moment next to
 * what the blood test said, how far apart they are, where in the dosing cycle it was drawn, and
 * whether it shaped the curve — and if not, why not.
 */
@Composable
fun LabResultScreen(summary: LabResultSummary, fmt: Fmt, onDone: () -> Unit, onEdit: (() -> Unit)? = null) {
    val c = Hl.colors
    ScreenScroll(gap = 14.dp, bottomInset = false) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            HlIconButton(HlIcon.Close, "닫기", onDone, modifier = Modifier.padding(start = 0.dp))
            HlText("검사값을 저장했어요", size = HlSize.t18, weight = FontWeight.Bold)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.hero)).background(c.card).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val at = summary.collectedAt
            HlText(
                when {
                    at == null -> "채혈 시각을 모르는 검사예요"
                    summary.hoursSinceDose != null -> "${fmt.dateTime(at)} 채혈 · 마지막 투약 후 ${fmt.hoursSince(summary.hoursSinceDose)}"
                    summary.beforeFirstDose -> "${fmt.dateTime(at)} 채혈 · 첫 투약 전이에요"
                    else -> "${fmt.dateTime(at)} 채혈"
                },
                size = HlSize.t13, color = c.muted,
            )
            summary.e2?.let { E2Block(it, summary, fmt) }
            summary.tt?.let { TtBlock(it) }
            if (summary.e2 == null && summary.tt == null) HlText("저장된 값이 없어요", size = HlSize.t14, color = c.muted)
        }
        HlButton("확인", onDone, Modifier.fillMaxWidth())
        if (onEdit != null) HlButton("고치기", onEdit, Modifier.fillMaxWidth(), kind = com.hormonelog.app.ui.kit.HlButtonKind.Text, minHeight = 48.dp, content = c.text)
        HlDisclaimer()
    }
}

@Composable
private fun E2Block(o: E2Outcome, summary: LabResultSummary, fmt: Fmt) {
    val c = Hl.colors
    val pmol = o.unit == E2Unit.PMOL_L
    fun shown(pg: Double) = (if (pmol) pg * 3.6713 else pg).toInt()
    Tiles(
        expectedLabel = "예상 · 그 시점",
        expected = o.expected?.let { shown(it.median).toString() } ?: "—",
        expectedUnit = o.unit.label,
        expectedSub = o.expected?.let { "범위 ${shown(it.lower)}–${shown(it.upper)}" } ?: "예상값을 낼 수 없어요",
        measured = com.hormonelog.app.state.plainNumber(o.reported),
        measuredUnit = o.unit.label,
        measuredSub = if (pmol) "= ${o.measuredPg.toInt()} pg/mL" else "검사지 값 그대로",
        measuredTitle = "실측",
    )
    Column {
        if (o.diffPercent != null) {
            Row1("차이", "${if (o.diffPercent >= 0) "+" else ""}${o.diffPercent}% · ${if (o.withinRange == true) "예상 범위 안" else "예상 범위 밖"}")
        }
        val spot = o.spot
        val hours = summary.hoursSinceDose
        if (spot != null && hours != null) {
            Row1(
                "주기 위치",
                "${when (spot) {
                    CycleSpot.NEAR_PEAK -> "피크 근처"
                    CycleSpot.NEAR_TROUGH -> "트로프 근처"
                    CycleSpot.MIDDLE -> "주기 중간"
                }} (투약 후 ${fmt.hoursSince(hours)})",
            )
        }
        val cal = o.calibration
        if (cal != null) {
            Column(Modifier.fillMaxWidth()) {
                HlDivider()
                Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        HlText("보정", size = HlSize.t14, color = c.muted)
                        HlText(if (cal.used) "보정에 썼어요" else "보정에 쓰지 않았어요", size = HlSize.t14, weight = FontWeight.Bold, color = if (cal.used) c.teal else c.orange)
                    }
                    HlText(
                        if (cal.used) {
                            "${groupName(cal.group)} 보정에 쓰인 ${summary.calibrationOrdinal ?: 1}번째 검사예요. 곡선이 다시 계산됐어요."
                        } else {
                            (cal.exclusion?.text ?: "") + (cal.exclusion?.fix?.let { " · $it" } ?: "")
                        },
                        size = HlSize.t13, color = c.muted, lineHeight = 1.5f,
                    )
                }
            }
        }
    }
}

@Composable
private fun TtBlock(o: TtOutcome) {
    val c = Hl.colors
    val toUnit: (Double) -> Double = { ng ->
        when (o.unit) {
            TUnit.NG_DL -> ng
            TUnit.NG_ML -> ng / 100.0
            TUnit.NMOL_L -> ng / 28.842
        }
    }
    fun fmtT(v: Double) = if (o.unit == TUnit.NG_DL) v.toInt().toString() else "%.1f".format(v).trimEnd('0').trimEnd('.')
    HlDivider()
    HlText("Total T", size = HlSize.t14, weight = FontWeight.Bold)
    Tiles(
        expectedLabel = "예상 · 그 시점",
        expected = o.expected?.let { fmtT(toUnit(it.median)) } ?: "—",
        expectedUnit = o.unit.label,
        expectedSub = o.expected?.let { "범위 ${fmtT(toUnit(it.lower))}–${fmtT(toUnit(it.upper))}" } ?: "예상값을 낼 수 없어요",
        measured = com.hormonelog.app.state.plainNumber(o.reported),
        measuredUnit = o.unit.label,
        measuredSub = if (o.unit != TUnit.NG_DL) "= ${o.measuredNgDl.toInt()} ng/dL" else "검사지 값 그대로",
        measuredTitle = "실측",
    )
    Column {
        if (o.diffPercent != null) Row1("차이", "${if (o.diffPercent >= 0) "+" else ""}${o.diffPercent}% · ${if (o.withinRange == true) "예상 범위 안" else "예상 범위 밖"}")
        o.calibration?.let { cal ->
            Row1("보정", if (cal.used) "보정에 썼어요" else cal.exclusion?.text ?: "보정에 쓰지 않았어요", if (cal.used) c.teal else c.orange)
        }
    }
}

private fun groupName(g: CalibrationGroup?): String = when (g) {
    CalibrationGroup.INJECTION -> "주사"
    CalibrationGroup.ORAL -> "경구·설하"
    CalibrationGroup.PATCH -> "패치"
    null -> ""
}

@Composable
private fun Tiles(
    expectedLabel: String, expected: String, expectedUnit: String, expectedSub: String,
    measuredTitle: String, measured: String, measuredUnit: String, measuredSub: String,
) {
    val c = Hl.colors
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(HlRadius.button)).background(c.tealSoft).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HlText(expectedLabel, size = HlSize.t12, weight = FontWeight.Bold, color = c.teal)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HlText(expected, size = HlSize.t22, weight = FontWeight.Bold, color = c.teal, tabular = true)
                HlText(expectedUnit, size = HlSize.t13, color = c.teal, modifier = Modifier.padding(bottom = 3.dp))
            }
            HlText(expectedSub, size = HlSize.t12, color = c.muted)
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(HlRadius.button)).background(c.yellowSoft).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                MeasuredMark(7.dp)
                HlText(measuredTitle, size = HlSize.t12, weight = FontWeight.Bold, color = c.yellow)
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                HlText(measured, size = HlSize.t22, weight = FontWeight.Bold, color = c.yellow, tabular = true)
                HlText(measuredUnit, size = HlSize.t13, color = c.yellow, modifier = Modifier.padding(bottom = 3.dp))
            }
            HlText(measuredSub, size = HlSize.t12, color = c.muted)
        }
    }
}

@Composable
private fun Row1(label: String, value: String, valueColor: Color = Hl.colors.text) {
    Column(Modifier.fillMaxWidth()) {
        HlDivider()
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HlText(label, size = HlSize.t14, color = Hl.colors.muted)
            HlText(value, modifier = Modifier.weight(1f), size = HlSize.t14, weight = FontWeight.Bold, color = valueColor, align = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

