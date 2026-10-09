package com.hormonelog.app.feature.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hormonelog.app.analysis.Analysis
import com.hormonelog.app.feature.common.DateTimePickerDialog
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.DrawChoice
import com.hormonelog.app.state.LabDraft
import com.hormonelog.app.state.RecordsReducer
import com.hormonelog.app.state.resolveDrawTime
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlButton
import com.hormonelog.app.ui.kit.HlChip
import com.hormonelog.app.ui.kit.HlDivider
import com.hormonelog.app.ui.kit.HlFieldError
import com.hormonelog.app.ui.kit.HlIconButton
import com.hormonelog.app.ui.kit.HlSheet
import com.hormonelog.app.ui.kit.HlSwitchRow
import com.hormonelog.app.ui.kit.HlTextAction
import com.hormonelog.app.ui.kit.HlTextField
import com.hormonelog.app.ui.kit.MeasuredMark
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.core.domain.Assay
import com.hormonelog.core.domain.E2Unit
import com.hormonelog.core.domain.TUnit
import java.time.Instant

class LabSheetActions(
    val close: () -> Unit,
    val edit: ((LabDraft) -> LabDraft) -> Unit,
    val save: () -> Unit,
)

/** The unit mix-up warning: how far the typed value is from what the model expected, and the way out. */
private data class UnitWarning(val text: String, val swapLabel: String, val swapTo: E2Unit)

/**
 * A blood value far from what the model expected is most often a unit slip (pmol/L typed as
 * pg/mL or the other way round); say so before it is saved, and offer the one-tap fix.
 */
private fun unitWarning(s: AppState, d: LabDraft, drawAt: Instant?, now: Instant, zone: java.time.ZoneId): UnitWarning? {
    val value = d.e2Value?.takeIf { it > 0 } ?: return null
    val at = drawAt ?: return null
    val others = s.labs.filter { it.id != d.editingId }
    val calibration = Analysis.calibrate(s.doses, others, s.settings.gonadalStatus)
    val expectedPg = Analysis.e2At(s.doses, s.regimens, calibration, at, now, zone)?.median?.takeIf { it >= 15.0 } ?: return null
    val expected = if (d.e2Unit == E2Unit.PMOL_L) expectedPg * 3.6713 else expectedPg
    val ratio = value / expected
    val unitLabel = d.e2Unit.label
    return when {
        ratio >= 2.8 && d.e2Unit == E2Unit.PG_ML -> UnitWarning(
            "그 시점 예상(${expected.toInt()} $unitLabel)보다 약 ${"%.1f".format(ratio)}배 높아요. 검사지 단위가 pmol/L인지 확인해 주세요.",
            "pmol/L로 바꾸기", E2Unit.PMOL_L,
        )
        ratio <= 0.36 && d.e2Unit == E2Unit.PMOL_L -> UnitWarning(
            "그 시점 예상(${expected.toInt()} $unitLabel)의 약 ${"%.2f".format(ratio)}배예요. 검사지 단위가 pg/mL인지 확인해 주세요.",
            "pg/mL로 바꾸기", E2Unit.PG_ML,
        )
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabSheet(s: AppState, fmt: Fmt, actions: LabSheetActions) {
    val c = Hl.colors
    val d = s.labDraft
    val now = fmt.now
    var picking by remember { mutableStateOf(false) }
    val editing = d.editingId != null
    val drawAt = resolveDrawTime(d, now, fmt.zone)
    val morning = remember(now.epochSecond / 60) { now.atZone(fmt.zone).toLocalDate().atTime(9, 0).atZone(fmt.zone).toInstant() }
    val warning = remember(d.e2, d.e2Unit, drawAt, s.doses, s.regimens, s.labs) { unitWarning(s, d, drawAt, now, fmt.zone) }
    val since = remember(drawAt, s.doses) { drawAt?.let { at -> Analysis.lastEstrogenBefore(s.doses, at)?.let { (at.toEpochMilli() - it.occurredAt.toEpochMilli()) / 3_600_000.0 } } }

    HlSheet(onDismiss = actions.close, fillHeight = true) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
        ) {
            HlText(if (editing) "검사 결과 수정" else "검사 결과", size = HlSize.t18, weight = FontWeight.Bold)
            HlIconButton(HlIcon.Close, "닫기", actions.close, size = 44.dp, iconSize = 20.dp)
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 6.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Field("채혈 시각") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HlChip("지금", d.draw == DrawChoice.NOW, { actions.edit { it.copy(draw = DrawChoice.NOW) } }, padding = chipPad)
                    if (!morning.isAfter(now)) {
                        HlChip("오늘 ${fmt.minutes(9 * 60)}", d.draw == DrawChoice.MORNING, { actions.edit { it.copy(draw = DrawChoice.MORNING) } }, padding = chipPad)
                    }
                    HlChip("어제 ${fmt.minutes(18 * 60)}", d.draw == DrawChoice.YESTERDAY_EVENING, { actions.edit { it.copy(draw = DrawChoice.YESTERDAY_EVENING) } }, padding = chipPad)
                    HlChip(
                        if (d.draw == DrawChoice.PICK && d.pickedMillis != null) fmt.dateTime(Instant.ofEpochMilli(d.pickedMillis)) else "직접 선택",
                        d.draw == DrawChoice.PICK, { picking = true }, padding = chipPad,
                    )
                    HlChip("시각 모름", d.draw == DrawChoice.UNKNOWN, { actions.edit { it.copy(draw = DrawChoice.UNKNOWN) } }, padding = chipPad)
                }
                HlText(
                    when {
                        d.draw == DrawChoice.UNKNOWN -> "값은 그대로 저장돼요. 다만 채혈 시각을 몰라 곡선 보정에는 쓰지 않아요."
                        d.draw == DrawChoice.PICK && d.pickedMillis == null -> "날짜·시각을 고르면 경과 시간을 보여드려요 · 미래 시각은 고를 수 없어요"
                        drawAt != null && since != null -> "마지막 투약 후 ${fmt.hoursSince(since)}"
                        drawAt != null -> "이 시각 전에 기록된 투약이 없어요"
                        else -> ""
                    },
                    size = HlSize.t13, color = c.muted,
                )
                HlText("결과를 받은 시간이 아니라, 피를 뽑은 시간이에요", size = HlSize.t12, color = c.dim)
            }

            Field(label = "E2 · 실측", diamond = true) {
                HlTextField(
                    value = d.e2, onValueChange = { t -> actions.edit { it.copy(e2 = RecordsReducer.cleanNumber(t)) } },
                    label = "E2 실측값", placeholder = "검사지 숫자", error = d.e2Error != null || warning != null,
                )
                if (d.e2Error != null) HlFieldError(d.e2Error!!)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    E2Unit.entries.forEach { u -> HlChip(u.label, d.e2Unit == u, { actions.edit { it.copy(e2Unit = u) } }, minHeight = 44.dp, size = HlSize.t13, padding = chipPad, shape = RoundedCornerShape(10.dp)) }
                }
                if (warning != null) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.chip)).background(c.orangeSoft).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HlText("단위를 확인해 주세요 (pg/mL ↔ pmol/L)", size = HlSize.t14, weight = FontWeight.Bold, color = c.orange)
                        HlText(warning.text, size = HlSize.t13, lineHeight = 1.5f)
                        HlTextAction(warning.swapLabel, { actions.edit { it.copy(e2Unit = warning.swapTo) } }, color = c.orange)
                    }
                }
            }

            Field(label = "Total T · 실측 · 선택", diamond = true) {
                HlTextField(
                    value = d.tt, onValueChange = { t -> actions.edit { it.copy(tt = RecordsReducer.cleanNumber(t)) } },
                    label = "Total T 실측값", placeholder = "검사지 숫자", error = d.ttError != null,
                )
                if (d.ttError != null) HlFieldError(d.ttError!!)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TUnit.entries.forEach { u -> HlChip(u.label, d.ttUnit == u, { actions.edit { it.copy(ttUnit = u) } }, minHeight = 44.dp, size = HlSize.t13, padding = chipPad, shape = RoundedCornerShape(10.dp)) }
                }
            }

            Field("검사 방식 · 선택") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Assay.LC_MS_MS, Assay.IMMUNOASSAY, Assay.UNKNOWN).forEach { a ->
                        HlChip(a.label, d.assay == a, { actions.edit { it.copy(assay = a) } }, padding = chipPad)
                    }
                }
                HlText("검사지에 없으면 '모름'으로 두어도 괜찮아요 · 면역측정은 ECLIA·CLIA 등이에요", size = HlSize.t12, color = c.muted)
            }

            Field("검사기관 · 메모 · 선택") {
                HlTextField(d.note, { t -> actions.edit { it.copy(note = t.take(120)) } }, label = "메모", placeholder = "예: OO의원 / 공복 채혈", keyboard = KeyboardType.Text, textSize = HlSize.t14)
            }

            Column(Modifier.clip(RoundedCornerShape(HlRadius.card)).background(c.input)) {
                HlSwitchRow(
                    title = "HRT 시작 전에 뽑은 검사예요", checked = d.isBaseline, onToggle = { v -> actions.edit { it.copy(isBaseline = v) } },
                    subtitle = "곡선 보정에는 쓰지 않고, Total T 예측의 출발점으로 써요",
                )
            }
        }

        Column(Modifier.fillMaxWidth()) {
            HlDivider()
            Row(Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 16.dp)) {
                HlButton("검사값 저장", actions.save, Modifier.fillMaxWidth(), enabled = d.canSave, container = if (d.canSave) c.yMark else null, content = if (d.canSave) Color(0xFF241D07) else null)
            }
        }
    }

    if (picking) {
        DateTimePickerDialog(
            seedMillis = d.pickedMillis ?: now.toEpochMilli(), zone = fmt.zone, is24Hour = fmt.clock24, now = now,
            onDismiss = { picking = false },
            onPicked = { millis -> actions.edit { it.copy(draw = DrawChoice.PICK, pickedMillis = millis) }; picking = false },
        )
    }
}

private val chipPad = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)

@Composable
private fun Field(label: String, diamond: Boolean = false, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (diamond) MeasuredMark(7.dp)
            HlText(label, size = HlSize.t13, weight = FontWeight.SemiBold, color = if (diamond) Hl.colors.yellow else Hl.colors.muted)
        }
        content()
    }
}
