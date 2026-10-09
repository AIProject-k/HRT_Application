package com.hormonelog.app.feature.dose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hormonelog.app.feature.common.DOSE_STATUS_CHOICES
import com.hormonelog.app.feature.common.DatePickDialog
import com.hormonelog.app.feature.common.DateTimePickerDialog
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.TimePickDialog
import com.hormonelog.app.feature.common.hint
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.state.Combo
import com.hormonelog.app.state.DoseDraft
import com.hormonelog.app.state.DoseTime
import com.hormonelog.app.state.IntervalChoice
import com.hormonelog.app.state.PATCH_STRENGTHS
import com.hormonelog.app.state.drugsFor
import com.hormonelog.app.state.frequentCombos
import com.hormonelog.app.state.planStartDate
import com.hormonelog.app.state.plainNumber
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlBanner
import com.hormonelog.app.ui.kit.HlButton
import com.hormonelog.app.ui.kit.HlChip
import com.hormonelog.app.ui.kit.HlFieldError
import com.hormonelog.app.ui.kit.HlIconButton
import com.hormonelog.app.ui.kit.HlNotice
import com.hormonelog.app.ui.kit.HlSheet
import com.hormonelog.app.ui.kit.HlTextField
import com.hormonelog.app.ui.kit.HlSegmented
import com.hormonelog.app.ui.kit.SegItem
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.InjectionSite
import com.hormonelog.core.domain.PatchCycle
import com.hormonelog.core.domain.Route
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What the dose sheet can ask the app to do. */
class DoseSheetActions(
    val close: () -> Unit,
    val edit: ((DoseDraft) -> DoseDraft) -> Unit,
    val route: (Route) -> Unit,
    val drug: (com.hormonelog.core.domain.Drug) -> Unit,
    val amount: (String) -> Unit,
    val combo: (Combo) -> Unit,
    val save: () -> Unit,
    val savePlan: () -> Unit,
)

private enum class Picker { NONE, DATETIME, START_DATE, PLAN_TIME }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoseSheet(doses: List<DoseEvent>, draft: DoseDraft, fmt: Fmt, actions: DoseSheetActions) {
    val c = Hl.colors
    val now = fmt.now
    var picker by remember { mutableStateOf(Picker.NONE) }
    val editing = draft.editingId != null
    val combos = remember(doses, now.epochSecond / 60) { frequentCombos(doses, now) }

    HlSheet(onDismiss = actions.close, fillHeight = true) {
        // ── header: title, close, and the 1회만 / 반복 일정 switch ──
        Column(
            Modifier
                .fillMaxWidth()
                .background(if (draft.repeat) c.blueSoft else androidx.compose.ui.graphics.Color.Transparent)
                .padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                HlText(
                    when {
                        editing -> "투약 기록 수정"
                        draft.repeat -> "반복 일정 만들기"
                        else -> "투약 기록"
                    },
                    size = HlSize.t18, weight = FontWeight.Bold, color = if (draft.repeat) c.blue else c.text,
                )
                HlIconButton(HlIcon.Close, "닫기", actions.close, tint = if (draft.repeat) c.blue else c.text, size = 44.dp, iconSize = 20.dp)
            }
            if (!editing) {
                HlSegmented(
                    items = listOf(SegItem(false, "1회만"), SegItem(true, "반복 일정", container = c.blue)),
                    selected = draft.repeat,
                    onSelect = { v -> actions.edit { it.copy(repeat = v) } },
                    solid = true, minHeight = 42.dp, size = HlSize.t14,
                )
            }
        }

        // ── body ──
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 14.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!draft.repeat && !editing && combos.isNotEmpty()) {
                Field("자주 쓴 조합") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        combos.forEach { combo ->
                            val selected = draft.drug == combo.drug && draft.route == combo.route &&
                                (if (combo.route == Route.PATCH) draft.patchStrength == combo.amount else draft.amount == combo.amount)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(HlRadius.chip))
                                    .background(if (selected) c.tealSoft else androidx.compose.ui.graphics.Color.Transparent)
                                    .border(1.dp, if (selected) c.teal else c.line, RoundedCornerShape(HlRadius.chip))
                                    .clickable(role = Role.RadioButton) { actions.combo(combo) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                HlText(
                                    com.hormonelog.app.feature.common.doseSummary(combo.drug, combo.route, combo.amount, combo.unit),
                                    modifier = Modifier.weight(1f), size = HlSize.t14, weight = FontWeight.SemiBold,
                                )
                                HlText("최근 30일 ${combo.uses}회", size = HlSize.t12, color = c.muted)
                            }
                        }
                    }
                }
            }

            Field("경로") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        Triple("주사", Route.IM_INJECTION) { r: Route -> r == Route.IM_INJECTION || r == Route.SC_INJECTION },
                        Triple("경구", Route.ORAL) { r: Route -> r == Route.ORAL },
                        Triple("설하", Route.SUBLINGUAL) { r: Route -> r == Route.SUBLINGUAL },
                        Triple("패치", Route.PATCH) { r: Route -> r == Route.PATCH },
                        Triple("젤", Route.GEL) { r: Route -> r == Route.GEL },
                    ).forEach { (label, route, matches) ->
                        HlChip(label, matches(draft.route), { if (!matches(draft.route)) actions.route(route) }, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp))
                    }
                }
            }
            if (draft.isInjection) {
                Field("주사 방식") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(Route.IM_INJECTION to "근육(IM)", Route.SC_INJECTION to "피하(SC)").forEach { (r, l) ->
                            HlChip(l, draft.route == r, { actions.route(r) })
                        }
                    }
                }
            }
            Field("약물") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    drugsFor(draft.route).forEach { d ->
                        HlChip(d.label, draft.drug == d, { actions.drug(d) }, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp))
                    }
                }
            }

            if (draft.isGel) {
                HlBanner(HlNotice.Warn) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        HlText("곡선 없음 · 젤 모델 미지원", size = HlSize.t13, weight = FontWeight.Bold, color = c.orange)
                        HlText("젤은 흡수 편차를 다룬 근거가 부족해 예상 곡선을 그리지 않아요. 기록은 저장되고 타임라인에 보여요.", size = HlSize.t13, lineHeight = 1.5f)
                    }
                }
            }

            if (draft.isPatch) {
                Field("패치 규격 · µg/일") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PATCH_STRENGTHS.forEach { v ->
                            HlChip(plainNumber(v), draft.patchStrength == v, { actions.edit { it.copy(patchStrength = v) } }, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                }
                Field("교체 주기") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PatchCycle.entries.forEach { p -> HlChip(p.label, draft.patchCycle == p, { actions.edit { it.copy(patchCycle = p) } }) }
                    }
                }
            } else {
                Field("용량") {
                    HlTextField(
                        value = draft.amountText,
                        onValueChange = actions.amount,
                        label = "용량",
                        placeholder = "예: 5",
                        unit = "mg",
                        keyboard = KeyboardType.Decimal,
                        error = draft.amountError != null && draft.amountText.isNotEmpty(),
                        filled = draft.amountFilled,
                    )
                    val error = draft.amountError
                    if (error != null && draft.amountText.isNotEmpty()) HlFieldError(error)
                    if (draft.amountFilled && error == null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            HlIcon(HlIcon.Check, size = 14.dp, tint = c.teal, strokeWidth = 2.4f)
                            HlText("${draft.drug.label}로 지난번 쓴 용량(${draft.amountText} mg)을 채웠어요", size = HlSize.t13, weight = FontWeight.SemiBold, color = c.teal)
                        }
                    }
                }
            }

            if (!draft.repeat) {
                Field("투약 시각") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        HlChip("지금 · ${fmt.time(now)}", draft.time == DoseTime.NOW, { actions.edit { it.copy(time = DoseTime.NOW) } })
                        HlChip(
                            if (draft.time == DoseTime.PICK && draft.pickedMillis != null) fmt.dateTime(java.time.Instant.ofEpochMilli(draft.pickedMillis)) else "직접 선택",
                            draft.time == DoseTime.PICK, { picker = Picker.DATETIME },
                        )
                    }
                    val picked = draft.pickedMillis?.let(java.time.Instant::ofEpochMilli)
                    if (draft.time == DoseTime.PICK && picked != null && picked.isBefore(now.minusSeconds(300))) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(HlRadius.chip))
                                .background(c.orangeSoft)
                                .border(1.dp, c.orange, RoundedCornerShape(HlRadius.chip))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            HlText("과거 시각 · ${fmt.dateDay(picked)} ${fmt.time(picked)}", size = HlSize.t14, weight = FontWeight.Bold, color = c.orange)
                            HlText("${fmt.elapsed(picked)} 전으로 기록돼요. 미래 시각은 고를 수 없어요.", size = HlSize.t12)
                        }
                    }
                }
            } else {
                if (!draft.isPatch) {
                    Field("간격") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                IntervalChoice.DAILY to "매일", IntervalChoice.TWICE_WEEKLY to "주 2회", IntervalChoice.WEEKLY to "매주",
                                IntervalChoice.BIWEEKLY to "2주", IntervalChoice.CUSTOM to "직접 입력",
                            ).forEach { (v, l) -> HlChip(l, draft.interval == v, { actions.edit { it.copy(interval = v) } }, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)) }
                        }
                        if (draft.interval == IntervalChoice.CUSTOM) {
                            HlTextField(
                                value = draft.customDays, onValueChange = { v -> actions.edit { it.copy(customDays = v.filter(Char::isDigit).take(2)) } },
                                label = "간격 일수", unit = "일마다", keyboard = KeyboardType.Number, error = draft.customDaysError != null && draft.customDays.isNotEmpty(),
                            )
                            val e = draft.customDaysError
                            if (e != null && draft.customDays.isNotEmpty()) HlFieldError(e)
                        }
                    }
                }
                val start = planStartDate(draft, now, fmt.zone)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlanTile("시작일", if (start == now.atZone(fmt.zone).toLocalDate()) "오늘 · ${start.monthValue}월 ${start.dayOfMonth}일" else "${start.year}년 ${start.monthValue}월 ${start.dayOfMonth}일", Modifier.weight(1f)) { picker = Picker.START_DATE }
                    PlanTile("투약 시각", fmt.minutes(draft.timeMinutes), Modifier.weight(1f)) { picker = Picker.PLAN_TIME }
                }
            }

            if (draft.isInjection && !draft.repeat) {
                Field("주사 부위 · 선택") {
                    val recency = remember(doses) { siteRecency(doses, now) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InjectionSite.entries.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { site ->
                                    HlChip(
                                        site.label, draft.site == site, { actions.edit { it.copy(site = if (it.site == site) null else site) } },
                                        modifier = Modifier.weight(1f), minHeight = 52.dp, sub = recency[site],
                                        padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (!draft.repeat) {
                Field("상태") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        DOSE_STATUS_CHOICES.forEach { st -> HlChip(st.label, draft.status == st, { actions.edit { it.copy(status = st) } }) }
                    }
                    HlText(draft.status.hint, size = HlSize.t12, color = c.muted)
                }
                Field("메모 · 선택") {
                    HlTextField(draft.note, { v -> actions.edit { it.copy(note = v.take(200)) } }, label = "메모", placeholder = "예: 통증 조금", keyboard = KeyboardType.Text, textSize = HlSize.t14)
                }
            }
        }

        // ── footer ──
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(c.line))
            Box(Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 16.dp)) {
                HlButton(
                    label = when {
                        editing -> "수정 저장"
                        draft.repeat -> "일정 만들기"
                        else -> "기록 저장"
                    },
                    onClick = if (draft.repeat) actions.savePlan else actions.save,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = draft.canSave,
                    container = if (draft.repeat) c.blue else null,
                    content = if (draft.repeat) c.bg else null,
                )
            }
        }
    }

    when (picker) {
        Picker.DATETIME -> DateTimePickerDialog(
            seedMillis = draft.pickedMillis ?: now.toEpochMilli(), zone = fmt.zone, is24Hour = fmt.clock24, now = now,
            onDismiss = { picker = Picker.NONE },
            onPicked = { millis -> actions.edit { it.copy(time = DoseTime.PICK, pickedMillis = millis) }; picker = Picker.NONE },
        )
        Picker.START_DATE -> DatePickDialog(
            seed = planStartDate(draft, now, fmt.zone), zone = fmt.zone,
            onDismiss = { picker = Picker.NONE },
            onPicked = { date: LocalDate -> actions.edit { it.copy(startMillis = date.atStartOfDay(fmt.zone).toInstant().toEpochMilli()) }; picker = Picker.NONE },
        )
        Picker.PLAN_TIME -> TimePickDialog(
            initialMinutes = draft.timeMinutes, is24Hour = fmt.clock24,
            onDismiss = { picker = Picker.NONE },
            onPicked = { m -> actions.edit { it.copy(timeMinutes = m) }; picker = Picker.NONE },
        )
        Picker.NONE -> Unit
    }
}

@Composable
private fun Field(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HlText(label, size = HlSize.t13, weight = FontWeight.SemiBold, color = Hl.colors.muted)
        content()
    }
}

@Composable
private fun PlanTile(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Hl.colors
    Column(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(HlRadius.chip))
            .background(c.input)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        HlText(label, size = HlSize.t12, color = c.muted)
        HlText(value, size = HlSize.t14, weight = FontWeight.Bold)
    }
}

/** "최근 · 7일 전" for the site used last, "14일 전" for the others, "기록 없음" for the never used. */
private fun siteRecency(doses: List<DoseEvent>, now: java.time.Instant): Map<InjectionSite, String> {
    val last = doses.filter { it.site != null && it.status.wasTaken }.groupBy { it.site!! }.mapValues { (_, v) -> v.maxOf { it.occurredAt } }
    val newest = last.maxByOrNull { it.value }?.key
    return InjectionSite.entries.associateWith { site ->
        val at = last[site] ?: return@associateWith "기록 없음"
        val days = ChronoUnit.DAYS.between(at, now).coerceAtLeast(0)
        val text = if (days == 0L) "오늘" else "${days}일 전"
        if (site == newest) "최근 · $text" else text
    }
}
