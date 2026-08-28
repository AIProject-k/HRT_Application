package com.hormonelog.app.feature.dose

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hormonelog.app.feature.common.DOSE_SHEET_DRUGS
import com.hormonelog.app.feature.common.DOSE_SHEET_ROUTES
import com.hormonelog.app.feature.common.DOSE_SHEET_UNITS
import com.hormonelog.app.feature.common.isAntiandrogen
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.feature.dashboard.DoseDraft
import com.hormonelog.app.feature.dashboard.DoseTimeChoice
import com.hormonelog.app.feature.dashboard.fmtDate
import com.hormonelog.app.feature.dashboard.fmtTime
import com.hormonelog.app.feature.dashboard.resolveDoseTime
import com.hormonelog.app.ui.components.HlChip
import com.hormonelog.app.ui.theme.HlColor
import com.hormonelog.app.ui.theme.HlType
import com.hormonelog.core.domain.DoseUnit
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.Route
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

private data class Preset(val title: String, val sub: String, val drug: Drug, val route: Route, val amount: Double, val unit: DoseUnit)

private val TIME_CHOICES = listOf(
    DoseTimeChoice.NOW to "지금",
    DoseTimeChoice.MINUS_30M to "30분 전",
    DoseTimeChoice.MINUS_2H to "2시간 전",
    DoseTimeChoice.YESTERDAY to "어제",
)

private val INTERVAL_CHOICES = listOf(1 to "매일", 2 to "이틀마다", 7 to "매주", 14 to "2주마다")

private fun trimAmount(v: Double) = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()

private enum class PickerStage { NONE, DATE, TIME }
private enum class DateTarget { CUSTOM_TIME, REPEAT_START, REPEAT_END }

private fun atNineLocal(utcDateMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(utcDateMillis).atZone(ZoneOffset.UTC).toLocalDate()
        .atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DoseSheet(
    draft: DoseDraft,
    now: Instant,
    zone: ZoneId,
    lastDose: com.hormonelog.core.domain.DoseEvent?,
    onEdit: ((DoseDraft) -> DoseDraft) -> Unit,
    onStep: (Boolean) -> Unit,
    onSetAmount: (String) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onSaveRegimen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = buildList {
        if (lastDose != null && !lastDose.drug.isAntiandrogen) {
            add(
                Preset(
                    lastDose.drug.label,
                    "${lastDose.route.label} · ${trimAmount(lastDose.amountEntered)}${lastDose.enteredUnit.label} · 지금",
                    lastDose.drug, lastDose.route, lastDose.amountEntered, lastDose.enteredUnit,
                ),
            )
        }
        add(Preset("사이프로테론", "경구 · 25mg · 지금", Drug.CYPROTERONE, Route.ORAL, 25.0, DoseUnit.MG))
    }

    val resolved = resolveDoseTime(draft.time, now, draft.customEpochMillis)

    var stage by remember { mutableStateOf(PickerStage.NONE) }
    var dateTarget by remember { mutableStateOf(DateTarget.CUSTOM_TIME) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    Column(modifier = modifier.fillMaxSize().background(HlColor.Background)) {
        SheetHeader("투약 기록", onClose)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // presets
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("최근에 쓴 조합 · 누르면 바로 채워져요", style = HlType.LabelStrong, color = HlColor.Teal)
                presets.forEach { p ->
                    val active = draft.drug == p.drug && draft.route == p.route && draft.amount == p.amount
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) HlColor.TealTintSoft else HlColor.Card)
                            .border(1.dp, if (active) HlColor.Teal else HlColor.Border06, RoundedCornerShape(14.dp))
                            .clickable {
                                onEdit { it.copy(drug = p.drug, route = p.route, amount = p.amount, unit = p.unit, time = DoseTimeChoice.NOW) }
                            }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(HlColor.TealTintSoft),
                            contentAlignment = Alignment.Center,
                        ) { Text("💉", fontSize = 13.sp) }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(p.title, style = HlType.CardTitle.copy(fontSize = 13.5.sp), color = HlColor.TextPrimary)
                            Text(p.sub, style = HlType.Caption, color = HlColor.TextDim)
                        }
                        Text(
                            if (active) "선택됨" else "1탭",
                            style = HlType.Badge,
                            color = if (active) HlColor.Teal else HlColor.TextDim,
                        )
                    }
                }
            }

            FieldBlock("약물") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    DOSE_SHEET_DRUGS.forEach { d ->
                        HlChip(d.label, draft.drug == d, { onEdit { it.copy(drug = d) } })
                    }
                }
            }

            FieldBlock("투여 방법") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    DOSE_SHEET_ROUTES.forEach { r ->
                        HlChip(r.label, draft.route == r, { onEdit { it.copy(route = r) } })
                    }
                }
            }

            FieldBlock("용량") {
                // Re-seeds whenever draft.amount changes elsewhere (stepper, preset, chip).
                var amountText by remember(draft.amount) { mutableStateOf(trimAmount(draft.amount)) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StepperButton("−") { onStep(false) }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(HlColor.InputSurface)
                            .border(1.dp, HlColor.Border08, RoundedCornerShape(14.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    ) {
                        BasicTextField(
                            value = amountText,
                            onValueChange = { raw ->
                                val filtered = raw.filter { it.isDigit() || it == '.' }.take(6)
                                amountText = filtered
                                onSetAmount(filtered)
                            },
                            textStyle = HlType.Stepper.copy(color = HlColor.TextPrimary, textAlign = TextAlign.Center),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            cursorBrush = SolidColor(HlColor.Teal),
                            modifier = Modifier.widthIn(min = 44.dp, max = 140.dp),
                        )
                        Text(draft.unit.label, style = HlType.LabelStrong, color = HlColor.TextMuted)
                    }
                    StepperButton("+") { onStep(true) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    DOSE_SHEET_UNITS.forEach { u ->
                        HlChip(
                            u.label, draft.unit == u, { onEdit { it.copy(unit = u) } },
                            shape = RoundedCornerShape(9.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            FieldBlock("반복") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    HlChip("1회만", !draft.repeat, { onEdit { it.copy(repeat = false) } })
                    HlChip("반복 일정", draft.repeat, { onEdit { it.copy(repeat = true) } })
                }
            }

            if (!draft.repeat) {
                FieldBlock("시간") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        TIME_CHOICES.forEach { (choice, lbl) ->
                            HlChip(lbl, draft.time == choice, { onEdit { it.copy(time = choice) } })
                        }
                        HlChip(
                            label = if (draft.time == DoseTimeChoice.CUSTOM && draft.customEpochMillis != null) {
                                "${fmtDate(resolved, zone)} ${fmtTime(resolved, zone)}"
                            } else {
                                "날짜 선택"
                            },
                            selected = draft.time == DoseTimeChoice.CUSTOM,
                            onClick = { dateTarget = DateTarget.CUSTOM_TIME; stage = PickerStage.DATE },
                        )
                    }
                    Text("${fmtDate(resolved, zone)} ${fmtTime(resolved, zone)} 로 저장돼요", style = HlType.Caption, color = HlColor.TextDim)
                }
            } else {
                val start = draft.repeatStartMillis?.let(Instant::ofEpochMilli) ?: now.minus(30, java.time.temporal.ChronoUnit.DAYS)
                FieldBlock("간격") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        INTERVAL_CHOICES.forEach { (days, lbl) ->
                            HlChip(lbl, draft.repeatEveryDays == days, { onEdit { it.copy(repeatEveryDays = days) } })
                        }
                    }
                }
                FieldBlock("시작일") {
                    HlChip(
                        label = fmtDate(start, zone),
                        selected = true,
                        onClick = { dateTarget = DateTarget.REPEAT_START; stage = PickerStage.DATE },
                    )
                }
                FieldBlock("종료") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        HlChip("계속", draft.repeatOngoing, { onEdit { it.copy(repeatOngoing = true, repeatEndMillis = null) } })
                        HlChip(
                            label = if (!draft.repeatOngoing && draft.repeatEndMillis != null) {
                                fmtDate(Instant.ofEpochMilli(draft.repeatEndMillis!!), zone)
                            } else {
                                "종료일 지정"
                            },
                            selected = !draft.repeatOngoing,
                            onClick = { dateTarget = DateTarget.REPEAT_END; stage = PickerStage.DATE },
                        )
                    }
                    Text("시작일부터 지금까지는 기록으로, 이후는 예상 흐름 예측으로 반영돼요.", style = HlType.Caption, color = HlColor.TextDim)
                }
            }

            FieldBlock("메모 (선택)") {
                NoteInput(draft.note, "예: 왼쪽 허벅지, 통증 조금") { onEdit { d -> d.copy(note = it) } }
            }
        }

        SheetFooter(
            label = if (draft.repeat) {
                "${INTERVAL_CHOICES.firstOrNull { it.first == draft.repeatEveryDays }?.second ?: "${draft.repeatEveryDays}일마다"} ${draft.drug.label} ${trimAmount(draft.amount)}${draft.unit.label} 반복 기록"
            } else {
                "${draft.drug.label} ${trimAmount(draft.amount)}${draft.unit.label} 기록하기"
            },
            background = HlColor.Teal,
            foreground = HlColor.OnTeal,
            onClick = if (draft.repeat) onSaveRegimen else onSave,
        )
    }

    if (stage == PickerStage.DATE) {
        val seedMillis = when (dateTarget) {
            DateTarget.CUSTOM_TIME -> draft.customEpochMillis ?: now.toEpochMilli()
            DateTarget.REPEAT_START -> draft.repeatStartMillis ?: now.minus(30, java.time.temporal.ChronoUnit.DAYS).toEpochMilli()
            DateTarget.REPEAT_END -> draft.repeatEndMillis ?: now.toEpochMilli()
        }
        val dateState = rememberDatePickerState(initialSelectedDateMillis = seedMillis)
        DatePickerDialog(
            onDismissRequest = { stage = PickerStage.NONE },
            confirmButton = {
                TextButton(onClick = {
                    val sel = dateState.selectedDateMillis ?: now.toEpochMilli()
                    when (dateTarget) {
                        DateTarget.CUSTOM_TIME -> {
                            pendingDateMillis = sel
                            stage = PickerStage.TIME
                        }
                        DateTarget.REPEAT_START -> {
                            onEdit { it.copy(repeatStartMillis = atNineLocal(sel, zone)) }
                            stage = PickerStage.NONE
                        }
                        DateTarget.REPEAT_END -> {
                            onEdit { it.copy(repeatEndMillis = atNineLocal(sel, zone), repeatOngoing = false) }
                            stage = PickerStage.NONE
                        }
                    }
                }) { Text(if (dateTarget == DateTarget.CUSTOM_TIME) "다음" else "확인") }
            },
            dismissButton = {
                TextButton(onClick = { stage = PickerStage.NONE }) { Text("취소") }
            },
        ) { DatePicker(state = dateState) }
    }

    if (stage == PickerStage.TIME) {
        val seed = (draft.customEpochMillis?.let(Instant::ofEpochMilli) ?: now).atZone(zone)
        val timeState = rememberTimePickerState(
            initialHour = seed.hour,
            initialMinute = seed.minute,
            is24Hour = false,
        )
        AlertDialog(
            onDismissRequest = { stage = PickerStage.NONE },
            confirmButton = {
                TextButton(onClick = {
                    val date = Instant.ofEpochMilli(pendingDateMillis ?: now.toEpochMilli())
                        .atZone(ZoneOffset.UTC).toLocalDate()
                    val millis = LocalDateTime.of(date, LocalTime.of(timeState.hour, timeState.minute))
                        .atZone(zone).toInstant().toEpochMilli()
                    onEdit { it.copy(time = DoseTimeChoice.CUSTOM, customEpochMillis = millis) }
                    stage = PickerStage.NONE
                }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { stage = PickerStage.NONE }) { Text("취소") }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
internal fun SheetHeader(title: String, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(HlColor.ButtonIdle)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) { Text("✕", fontSize = 16.sp, color = HlColor.TextSecondary) }
        Text(title, style = HlType.CardTitleLg.copy(fontSize = 17.sp), color = HlColor.TextPrimary)
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(HlColor.Border06))
}

@Composable
internal fun SheetFooter(
    label: String,
    background: androidx.compose.ui.graphics.Color,
    foreground: androidx.compose.ui.graphics.Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(HlColor.Border06))
    Box(modifier = Modifier.fillMaxWidth().background(HlColor.NavBackground).padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = HlType.ButtonLabel, color = foreground, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun FieldBlock(label: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(label, style = HlType.LabelStrong, color = HlColor.TextMuted)
        content()
    }
}

@Composable
private fun StepperButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HlColor.ButtonIdle)
            .border(1.dp, HlColor.Border08, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(symbol, fontSize = 22.sp, color = HlColor.TextBright) }
}

@Composable
internal fun NoteInput(value: String, placeholder: String, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HlColor.InputSurface)
            .border(1.dp, HlColor.Border08, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = HlType.Body.copy(color = HlColor.TextPrimary),
            cursorBrush = SolidColor(HlColor.Teal),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, style = HlType.Body, color = HlColor.TextFaint)
                inner()
            },
        )
    }
}
