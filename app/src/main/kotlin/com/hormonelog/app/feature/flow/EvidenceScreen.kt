package com.hormonelog.app.feature.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hormonelog.app.analysis.Analysis
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.state.AppState
import com.hormonelog.app.ui.kit.HlChip
import com.hormonelog.app.ui.kit.HlDisclaimer
import com.hormonelog.app.ui.kit.HlPill
import com.hormonelog.app.ui.kit.HlSectionLabel
import com.hormonelog.app.ui.kit.HlTopBar
import com.hormonelog.app.ui.kit.ScreenScroll
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlColors
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.core.domain.Route
import com.hormonelog.core.evidence.EvidenceBundleV1
import com.hormonelog.core.evidence.EvidenceSource
import com.hormonelog.core.evidence.ModelParameter
import com.hormonelog.core.modelengine.CalibrationEngine
import com.hormonelog.core.modelengine.CalibrationGroup
import kotlin.math.roundToInt

private enum class Part(val label: String) {
    INJECTION("주사 · EV"), ORAL("경구"), SUBLINGUAL("설하"), PATCH("패치"), ANTIANDROGEN("항안드로겐")
}

private val PARAMETER_NAMES = mapOf(
    "d" to "용량당 도달 수치 (d)",
    "k1" to "흡수 속도 1 (k1)",
    "k2" to "흡수 속도 2 (k2)",
    "k3" to "제거 속도 (k3)",
    "oralScale" to "경구 노출 배율",
    "oralKa" to "경구 흡수 속도 (ka)",
    "oralKe" to "경구 제거 속도 (ke)",
    "slScale" to "설하 노출 배율",
    "slKa" to "설하 흡수 속도 (ka)",
    "slKe" to "설하 제거 속도 (ke)",
    "slSwallowed" to "삼켜서 흡수되는 비율",
    "ttBaselineIntact" to "Total T 출발점 (고환 있음)",
    "ttBaselinePostOrchi" to "Total T 출발점 (수술 후)",
    "ttFloor" to "Total T 하한",
    "ttIntervalFraction" to "Total T 범위 폭",
    "e2EffectTauDays" to "E2가 T에 반영되는 지연",
    "e2SupprEmax" to "E2의 최대 억제",
    "e2SupprEC50" to "E2 억제가 절반이 되는 수치",
    "e2SupprHill" to "E2 억제 곡선의 가팔라짐",
    "cpaF" to "사이프로테론 흡수율",
    "cpaKaPerDay" to "사이프로테론 흡수 속도",
    "cpaKePerDay" to "사이프로테론 제거 속도",
    "cpaSupprEmax" to "사이프로테론의 최대 억제",
    "cpaSupprEC50" to "사이프로테론 억제가 절반이 되는 수준",
)

private val LIMITS = mapOf(
    Part.INJECTION to "주사 부위·용매·체중에 따른 개인차는 반영하지 않아요. 피하주사(SC)는 근육주사 값을 쓰되 범위를 넓게 잡아요.",
    Part.ORAL to "음식·복용 시각의 영향을 반영하지 않아요. 0–8시간 자료에서 그 뒤 구간을 이어 그렸어요.",
    Part.SUBLINGUAL to "삼키는 비율(약 40%)은 평균값이에요. 개인차가 커서 범위를 넓게 잡아요.",
    Part.PATCH to "피부 상태·부착 부위 차이와, 붙이고 떼는 시점은 반영하지 않아요. 규격 표기값(µg/일)을 그대로 써요.",
    Part.ANTIANDROGEN to "Total T 범위가 넓게 나와요. 경구·설하만 쓸 때는 낮게 나오는 경향이 있어요. 검사로 확인이 필요해요.",
)

/** How strong the evidence behind a parameter reads, from the kind of study that produced it. */
private fun levelOf(sources: List<EvidenceSource>): String {
    fun rank(s: EvidenceSource): Int {
        val l = s.evidenceLevel.lowercase()
        return when {
            "primary" in l -> 3
            "systematic" in l || "meta" in l || "cohort" in l -> 2
            else -> 1
        }
    }
    return when (sources.maxOfOrNull(::rank) ?: 0) { 3 -> "높음"; 2 -> "중간"; else -> "낮음" }
}

private fun levelColor(c: HlColors, level: String): Color = when (level) {
    "높음" -> c.teal
    "중간" -> c.blue
    "낮음" -> c.orange
    else -> c.yellow
}

private fun valueText(p: ModelParameter): String {
    val v = if (p.value % 1.0 == 0.0) p.value.toLong().toString() else "%.4g".format(p.value)
    return if (p.unit.isEmpty() || p.unit == "fraction") v else "$v ${p.unit.replace("1/day", "/일").replace("day", "일")}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EvidenceScreen(s: AppState, fmt: Fmt, onBack: () -> Unit) {
    val c = Hl.colors
    val bundle = EvidenceBundleV1.bundle
    var part by remember { mutableStateOf(Part.INJECTION) }
    val calibration = remember(s.doses, s.labs, s.settings.gonadalStatus) { Analysis.calibrate(s.doses, s.labs, s.settings.gonadalStatus) }

    val params: List<ModelParameter> = when (part) {
        Part.INJECTION -> bundle.parameters.filter { it.route == Route.IM_INJECTION && it.ester == "EV" }
        Part.ORAL -> bundle.parameters.filter { it.route == Route.ORAL }
        Part.SUBLINGUAL -> bundle.parameters.filter { it.route == Route.SUBLINGUAL && it.name.startsWith("sl") }
        Part.PATCH -> bundle.parameters.filter { it.route == Route.PATCH }
        Part.ANTIANDROGEN -> bundle.parameters.filter { it.route == null }
    }
    val group = when (part) { Part.INJECTION -> CalibrationGroup.INJECTION; Part.ORAL, Part.SUBLINGUAL -> CalibrationGroup.ORAL; Part.PATCH -> CalibrationGroup.PATCH; Part.ANTIANDROGEN -> null }
    val personal = group?.let { calibration.groups[it] }

    Column(Modifier.fillMaxWidth()) {
        HlTopBar("근거 탐색기", onBack)
        ScreenScroll(padding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 24.dp), gap = 12.dp, bottomInset = true) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HlText("지금 곡선", size = HlSize.t12, color = c.muted)
                HlText("근거 묶음 ${bundle.version} · 출처 ${bundle.sources.size}개", size = HlSize.t14, weight = FontWeight.Bold)
                HlText("주사·패치는 3구획 모델, 경구·설하는 1구획 모델이에요. 검사로 경로마다 배율을 보정해요.", size = HlSize.t12, color = c.muted, lineHeight = 1.5f)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Part.entries.forEach { p -> HlChip(p.label, part == p, { part = p }, minHeight = 44.dp, size = HlSize.t13, shape = RoundedCornerShape(999.dp), padding = PaddingValues(horizontal = 14.dp)) }
            }

            if (personal != null) {
                ParamCard(
                    name = "개인 보정 배율", value = "×${"%.2f".format(personal.scale)} (검사 ${personal.labCount}건)", level = "내 검사",
                    source = "내 검사값으로 계산했어요", limit = "보정 폭은 −${((1 - CalibrationEngine.SCALE_MIN) * 100).roundToInt()}%~+${((CalibrationEngine.SCALE_MAX - 1) * 100).roundToInt()}%로 제한돼요.",
                )
            }
            params.forEach { p ->
                val srcs = p.evidenceIds.mapNotNull { bundle.sources[it] }
                ParamCard(
                    name = (if (p.ester == "tw") "주 2회 · " else if (p.ester == "ow") "주 1회 · " else "") + (PARAMETER_NAMES[p.name] ?: p.name),
                    value = valueText(p), level = levelOf(srcs),
                    source = srcs.joinToString(", ") { "${it.title.substringBefore(" — ").take(48)} (${it.year})" },
                    limit = LIMITS.getValue(part),
                )
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.orangeSoft).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HlText("곡선을 그리지 않는 경우", size = HlSize.t14, weight = FontWeight.Bold, color = c.orange)
                HlText("· 젤: 흡수 편차를 다룬 근거가 부족해요\n· 고환 정보를 '말하고 싶지 않음'으로 둔 경우: Total T 곡선을 숨겨요\n· 기록 시작 후 2주 미만: 검사값을 보정에 쓰지 않아요", size = HlSize.t13, lineHeight = 1.6f)
            }

            HlSectionLabel("출처 목록")
            bundle.sources.values.forEach { src ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card).padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    HlText(src.title, size = HlSize.t13, weight = FontWeight.SemiBold, lineHeight = 1.4f)
                    HlText("${src.authors} · ${src.year} · ${src.reference}", size = HlSize.t12, color = c.muted, lineHeight = 1.5f)
                    HlText("종류 · ${src.evidenceLevel}", size = HlSize.t12, color = c.muted)
                }
            }
            HlText("여기 값은 문헌에서 유도한 인구집단 근사치이며 임상적으로 검증되지 않았어요. 용량을 정하는 데 쓰지 마세요.", size = HlSize.t12, color = c.muted, lineHeight = 1.5f)
            HlDisclaimer()
        }
    }
}

@Composable
private fun ParamCard(name: String, value: String, level: String, source: String, limit: String) {
    val c = Hl.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            HlText(name, size = HlSize.t14, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            HlPill("근거 $level", levelColor(c, level))
        }
        HlText(value, size = HlSize.t16, weight = FontWeight.Bold, tabular = true)
        HlText("출처 · $source", size = HlSize.t12, color = c.muted, lineHeight = 1.5f)
        HlText("한계 · $limit", size = HlSize.t13, lineHeight = 1.5f)
    }
}
