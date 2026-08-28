package com.hormonelog.app.feature.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hormonelog.app.feature.common.LAB_METHODS
import com.hormonelog.app.feature.common.hint
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.feature.dashboard.LabDraft
import com.hormonelog.app.feature.dashboard.LabField
import com.hormonelog.app.feature.dashboard.LabTimeChoice
import com.hormonelog.app.feature.dashboard.fmtDate
import com.hormonelog.app.feature.dashboard.fmtShort
import com.hormonelog.app.feature.dashboard.fmtTime
import com.hormonelog.app.feature.dashboard.resolveLabTime
import com.hormonelog.app.feature.dose.FieldBlock
import com.hormonelog.app.feature.dose.NoteInput
import com.hormonelog.app.feature.dose.SheetFooter
import com.hormonelog.app.feature.dose.SheetHeader
import com.hormonelog.app.ui.components.HlChip
import com.hormonelog.app.ui.theme.HlColor
import com.hormonelog.app.ui.theme.HlType
import com.hormonelog.core.domain.DoseEvent
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

private val LAB_TIME_CHOICES = listOf(
    LabTimeChoice.NOW to "방금 받음",
    LabTimeChoice.THIS_MORNING to "오늘 오전",
    LabTimeChoice.YESTERDAY to "어제",
)

private val PAD_KEYS = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ".", "0", "⌫")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabSheet(
    draft: LabDraft,
    now: Instant,
    zone: ZoneId,
    lastDose: DoseEvent?,
    onEdit: ((LabDraft) -> LabDraft) -> Unit,
    onFocus: (LabField) -> Unit,
    onKey: (String) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolved = resolveLabTime(draft.time, now, zone)
    val canSave = draft.canSave

    Column(modifier = modifier.fillMaxSize().background(HlColor.Background)) {
        SheetHeader("검사 결과 기록", onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(17.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(HlColor.YellowTintSoft)
                    .border(1.dp, HlColor.YellowTint, RoundedCornerShape(13.dp))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("🧪", fontSize = 12.sp)
                Text(
                    "여기 입력한 값은 실제 검사값으로 저장되고, 그래프에 예상 곡선과 구분해서 표시돼요.",
                    style = HlType.BodySm,
                    color = HlColor.Orange,
                )
            }

            AnalyteField(
                title = "에스트라디올 (E2)",
                caption = "혈중 에스트로겐 수치",
                value = draft.e2,
                unit = draft.e2Unit,
                focused = draft.focus == LabField.E2,
                onClick = { onFocus(LabField.E2) },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("pg/mL", "pmol/L").forEach { u ->
                    HlChip(
                        u, draft.e2Unit == u, { onEdit { it.copy(e2Unit = u) } },
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            AnalyteField(
                title = "총 테스토스테론",
                caption = "Total T",
                value = draft.tt,
                unit = "ng/dL",
                focused = draft.focus == LabField.TT,
                onClick = { onFocus(LabField.TT) },
            )

            FieldBlock("검사 시간") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    LAB_TIME_CHOICES.forEach { (choice, lbl) ->
                        HlChip(lbl, draft.time == choice, { onEdit { it.copy(time = choice) } })
                    }
                }
                Text("${fmtDate(resolved, zone)} ${fmtTime(resolved, zone)} 로 저장돼요", style = HlType.Caption, color = HlColor.TextDim)
            }

            FieldBlock("검사 방법") {
                Text("모르면 '모름'으로 두어도 괜찮아요", style = HlType.Caption, color = HlColor.TextDim)
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    LAB_METHODS.forEach { m ->
                        val active = draft.method == m
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) HlColor.TealTintSoft else HlColor.Card)
                                .border(1.dp, if (active) HlColor.Teal else HlColor.Border06, RoundedCornerShape(12.dp))
                                .clickable { onEdit { it.copy(method = m) } }
                                .padding(horizontal = 13.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(if (active) HlColor.Teal else androidx.compose.ui.graphics.Color.Transparent)
                                    .border(2.dp, if (active) HlColor.Teal else HlColor.TextFaint, RoundedCornerShape(50)),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(m.label, style = HlType.CardTitle.copy(fontSize = 13.sp), color = if (active) HlColor.Teal else HlColor.TextPrimary)
                                Text(m.hint, style = HlType.Caption, color = HlColor.TextDim)
                            }
                        }
                    }
                }
            }

            FieldBlock("검사기관 · 메모 (선택)") {
                NoteInput(draft.note, "예: OO의원 / 공복 채혈") { onEdit { d -> d.copy(note = it) } }
            }

            if (lastDose != null) {
                val days = ((resolved.toEpochMilli() - lastDose.occurredAt.toEpochMilli()) / 3_600_000.0 / 24.0).roundToInt()
                Text(
                    "마지막 투약(${fmtShort(lastDose.occurredAt, zone)})으로부터 약 ${days}일 뒤 채혈로 기록돼요.",
                    style = HlType.Caption,
                    color = HlColor.TextDim,
                )
            }
        }

        if (draft.focus != LabField.NONE) {
            Numpad(onKey)
        }

        SheetFooter(
            label = if (canSave) "검사 결과 저장" else "E2 또는 Total T를 입력해 주세요",
            background = if (canSave) HlColor.Yellow else HlColor.KeyAlt,
            foreground = if (canSave) HlColor.OnYellow else HlColor.TextDim,
            enabled = canSave,
            onClick = onSave,
        )
    }
}

@Composable
private fun AnalyteField(
    title: String,
    caption: String,
    value: String,
    unit: String,
    focused: Boolean,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, style = HlType.CardTitle.copy(fontSize = 13.sp), color = HlColor.TextPrimary)
            Text(caption, style = HlType.Caption, color = HlColor.TextDim)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(HlColor.InputSurface)
                .border(1.5.dp, if (focused) HlColor.Yellow else HlColor.Border08, RoundedCornerShape(14.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                value.ifEmpty { "입력" },
                style = HlType.Stepper,
                color = if (value.isEmpty()) HlColor.TextPlaceholder else HlColor.Yellow,
            )
            Text(unit, style = HlType.LabelStrong, color = HlColor.TextMuted)
        }
    }
}

@Composable
private fun Numpad(onKey: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HlColor.ChipIdle)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PAD_KEYS.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Text(
                        key,
                        style = HlType.CardTitleLg.copy(fontSize = 19.sp),
                        color = HlColor.TextPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (key == "⌫") HlColor.KeyAlt else HlColor.KeyIdle)
                            .clickable { onKey(key) }
                            .padding(top = 12.dp),
                    )
                }
            }
        }
    }
}
