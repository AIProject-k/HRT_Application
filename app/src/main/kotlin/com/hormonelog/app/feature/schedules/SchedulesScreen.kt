package com.hormonelog.app.feature.schedules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hormonelog.app.feature.common.DatePickDialog
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.TimePickDialog
import com.hormonelog.app.feature.common.WEEK_DISPLAY_ORDER
import com.hormonelog.app.feature.common.cadenceLabel
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.feature.common.shortLabel
import com.hormonelog.app.feature.common.summary
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.IntervalChoice
import com.hormonelog.app.state.PATCH_STRENGTHS
import com.hormonelog.app.state.ScheduleDraft
import com.hormonelog.app.state.ScheduleOps
import com.hormonelog.app.state.drugsFor
import com.hormonelog.app.state.plainNumber
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlButton
import com.hormonelog.app.ui.kit.HlButtonKind
import com.hormonelog.app.ui.kit.HlChip
import com.hormonelog.app.ui.kit.HlFieldError
import com.hormonelog.app.ui.kit.HlPill
import com.hormonelog.app.ui.kit.HlTextField
import com.hormonelog.app.ui.kit.HlTopBar
import com.hormonelog.app.ui.kit.ScreenScroll
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.PatchCycle
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ScheduleActions(
    val back: () -> Unit,
    val create: () -> Unit,
    val edit: (UUID) -> Unit,
    val endToday: (UUID) -> Unit,
    val draft: ((ScheduleDraft) -> ScheduleDraft) -> Unit,
    val route: (Route) -> Unit,
    val drug: (Drug) -> Unit,
    val amount: (String) -> Unit,
    val interval: (IntervalChoice) -> Unit,
    val patchCycle: (PatchCycle) -> Unit,
    val toggleDay: (DayOfWeek) -> Unit,
    val save: () -> Unit,
    val askDelete: (UUID) -> Unit,
)

private fun hours(r: Regimen, zone: java.time.ZoneId): Int = r.timeMinutes ?: r.startAt.atZone(zone).let { it.hour * 60 + it.minute }

// ── the list ──────────────────────────────────────────────────

@Composable
fun SchedulesScreen(s: AppState, fmt: Fmt, actions: ScheduleActions) {
    val c = Hl.colors
    val running = s.regimens.filter { it.isRunningAt(fmt.now) }.sortedByDescending { it.startAt }
    val ended = s.regimens.filterNot { it.isRunningAt(fmt.now) }.sortedByDescending { it.endAt ?: it.startAt }
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        HlTopBar("반복 일정", actions.back, action = "새 일정", onAction = actions.create)
        ScreenScroll(padding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 24.dp), gap = 10.dp, bottomInset = true) {
            if (s.regimens.isEmpty()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HlText("아직 반복 일정이 없어요", size = HlSize.t14, weight = FontWeight.Bold)
                    HlText("일정을 만들어 두면 매번 입력하지 않아도 돼요. 예정 시각이 되면 알려 주고, 예상 곡선도 일정에 맞춰 이어서 그려요.", size = HlSize.t13, color = c.muted, lineHeight = 1.5f)
                }
            }
            (running + ended).forEach { r -> ScheduleCard(r, fmt, active = r in running, actions = actions) }
            HlButton("새 일정", actions.create, Modifier.fillMaxWidth(), leading = HlIcon.Plus, size = HlSize.t14)
            HlText("종료한 일정의 지난 기록은 그대로 남아요. 일정을 지우려면 수정 화면 맨 아래에서 지울 수 있어요.", size = HlSize.t12, color = c.muted, lineHeight = 1.5f)
        }
    }
}

@Composable
private fun ScheduleCard(r: Regimen, fmt: Fmt, active: Boolean, actions: ScheduleActions) {
    val c = Hl.colors
    val injection = r.route == Route.IM_INJECTION || r.route == Route.SC_INJECTION
    val (icon, bg, fg) = when {
        injection -> Triple(HlIcon.Injection, c.blueSoft, c.blue)
        r.route == Route.PATCH -> Triple(HlIcon.Patch, c.violetSoft, c.violet)
        r.route == Route.GEL -> Triple(HlIcon.Gel, c.violetSoft, c.violet)
        else -> Triple(HlIcon.Pill, c.violetSoft, c.violet)
    }
    val upcoming = r.startAt.isAfter(fmt.now)
    val period = r.endAt?.let { "${fmt.date(r.startAt)} – ${fmt.date(it)}" } ?: "${fmt.date(r.startAt)}부터"
    Column(
        Modifier.fillMaxWidth().alpha(if (active) 1f else 0.7f).clip(RoundedCornerShape(HlRadius.card)).background(c.card).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(HlRadius.chipSmall)).background(bg), contentAlignment = Alignment.Center) {
                HlIcon(icon, size = 20.dp, tint = fg)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                HlText(r.summary(), size = HlSize.t14, weight = FontWeight.Bold)
                HlText("${r.cadenceLabel()} · ${fmt.minutes(hours(r, fmt.zone))}", size = HlSize.t13, color = c.muted, lineHeight = 1.45f)
                HlText(period, size = HlSize.t12, color = c.muted)
            }
            HlPill(if (!active) "종료" else if (upcoming) "예정" else "진행 중", if (!active) c.muted else if (upcoming) c.blue else c.teal)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HlButton("수정", { actions.edit(r.id) }, Modifier.weight(1f), kind = HlButtonKind.Secondary, minHeight = 48.dp, size = HlSize.t13)
            if (active) HlButton("오늘부로 종료", { actions.endToday(r.id) }, Modifier.weight(1f), kind = HlButtonKind.Secondary, minHeight = 48.dp, size = HlSize.t13, content = c.orange)
        }
    }
}

// ── the editor ────────────────────────────────────────────────

private enum class Pick { NONE, START, END, TIME }

private val TIME_PRESETS = listOf(8 * 60 + 30, 9 * 60, 21 * 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditScreen(s: AppState, fmt: Fmt, actions: ScheduleActions) {
    val c = Hl.colors
    val d = s.scheduleDraft ?: return
    val existing = d.editingId?.let { id -> s.regimens.firstOrNull { it.id == id } }
    var picker by remember { mutableStateOf(Pick.NONE) }
    val today = fmt.now.atZone(fmt.zone).toLocalDate()
    val todayMillis = today.atStartOfDay(fmt.zone).toInstant().toEpochMilli()
    val startDate = d.startMillis?.let { Instant.ofEpochMilli(it).atZone(fmt.zone).toLocalDate() } ?: existing?.startAt?.atZone(fmt.zone)?.toLocalDate() ?: today
    val endDate = d.endMillis?.let { Instant.ofEpochMilli(it).atZone(fmt.zone).toLocalDate() }
    val endError = d.endBeforeStart(todayMillis)
    val valid = d.canSave(todayMillis)
    val preview = remember(d, fmt.now.epochSecond / 60) { ScheduleOps.preview(d, fmt.now, fmt.zone) }

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        HlTopBar(if (existing != null) "일정 수정" else "새 일정", actions.back)
        ScreenScroll(padding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 24.dp), gap = 16.dp, bottomInset = true) {
            if (existing != null) {
                // The plan's drug, route and amount are what past records point to; changing them is a new plan.
                Field("약물 · 경로 · 용량") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(
                            Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(HlRadius.chip)).background(c.input).border(1.dp, c.line, RoundedCornerShape(HlRadius.chip)).padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
                        ) {
                            HlText(existing.drug.label, size = HlSize.t14, weight = FontWeight.Bold)
                            HlText(existing.route.label, size = HlSize.t12, color = c.muted)
                        }
                        Row(
                            Modifier.width(96.dp).heightIn(min = 56.dp).clip(RoundedCornerShape(HlRadius.chip)).background(c.input).border(1.dp, c.line, RoundedCornerShape(HlRadius.chip)).padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            HlText(plainNumber(existing.amountEntered), size = HlSize.t14, weight = FontWeight.Bold)
                            HlText(if (existing.route == Route.PATCH) "µg/일" else "mg", size = HlSize.t12, color = c.muted)
                        }
                    }
                    HlText("약물이나 용량이 바뀌면 이 일정을 종료하고 새 일정을 만들어 주세요. 지난 기록은 그대로 남아요.", size = HlSize.t12, color = c.muted, lineHeight = 1.5f)
                }
            } else {
                Field("경로") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            Triple("주사", Route.IM_INJECTION) { r: Route -> r == Route.IM_INJECTION || r == Route.SC_INJECTION },
                            Triple("경구", Route.ORAL) { r: Route -> r == Route.ORAL },
                            Triple("설하", Route.SUBLINGUAL) { r: Route -> r == Route.SUBLINGUAL },
                            Triple("패치", Route.PATCH) { r: Route -> r == Route.PATCH },
                            Triple("젤", Route.GEL) { r: Route -> r == Route.GEL },
                        ).forEach { (label, route, matches) ->
                            HlChip(label, matches(d.route), { if (!matches(d.route)) actions.route(route) }, padding = PaddingValues(horizontal = 14.dp))
                        }
                    }
                }
                if (d.route == Route.IM_INJECTION || d.route == Route.SC_INJECTION) {
                    Field("주사 방식") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(Route.IM_INJECTION to "근육(IM)", Route.SC_INJECTION to "피하(SC)").forEach { (r, l) -> HlChip(l, d.route == r, { actions.route(r) }) }
                        }
                    }
                }
                Field("약물") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        drugsFor(d.route).forEach { drug -> HlChip(drug.label, d.drug == drug, { actions.drug(drug) }, padding = PaddingValues(horizontal = 14.dp)) }
                    }
                }
                if (d.isPatch) {
                    Field("패치 규격 · µg/일") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PATCH_STRENGTHS.forEach { v -> HlChip(plainNumber(v), d.patchStrength == v, { actions.draft { it.copy(patchStrength = v) } }, padding = PaddingValues(horizontal = 12.dp)) }
                        }
                    }
                } else {
                    Field("용량") {
                        HlTextField(
                            d.amountText, actions.amount, label = "용량", placeholder = "예: 5", unit = "mg", keyboard = KeyboardType.Decimal,
                            error = d.amountError != null && d.amountText.isNotEmpty(),
                        )
                        val e = d.amountError
                        if (e != null && d.amountText.isNotEmpty()) HlFieldError(e)
                    }
                }
            }

            if (d.isPatch) {
                Field("교체 주기") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PatchCycle.entries.forEach { p -> HlChip(p.label, d.patchCycle == p, { actions.patchCycle(p) }) }
                    }
                }
            } else {
                Field("간격") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            IntervalChoice.DAILY to "매일", IntervalChoice.TWICE_WEEKLY to "주 2회", IntervalChoice.WEEKLY to "매주",
                            IntervalChoice.BIWEEKLY to "2주", IntervalChoice.CUSTOM to "직접 입력",
                        ).forEach { (v, l) -> HlChip(l, d.interval == v, { actions.interval(v) }, padding = PaddingValues(horizontal = 14.dp)) }
                    }
                    if (d.interval == IntervalChoice.CUSTOM) {
                        HlTextField(
                            d.customDays, { v -> actions.draft { it.copy(customDays = v.filter(Char::isDigit).take(2)) } },
                            label = "간격 일수", unit = "일마다", keyboard = KeyboardType.Number, error = d.customDaysError != null && d.customDays.isNotEmpty(),
                        )
                        val e = d.customDaysError
                        if (e != null && d.customDays.isNotEmpty()) HlFieldError(e)
                    }
                }
            }

            if (d.effectiveInterval == IntervalChoice.WEEKLY || d.effectiveInterval == IntervalChoice.TWICE_WEEKLY) {
                Field("요일") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WEEK_DISPLAY_ORDER.forEach { day ->
                            val on = day in d.weekdays
                            Box(
                                Modifier
                                    .weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(HlRadius.chip))
                                    .background(if (on) c.tealSoft else Color.Transparent)
                                    .border(1.dp, if (on) c.teal else c.line, RoundedCornerShape(HlRadius.chip))
                                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { actions.toggleDay(day) }),
                                contentAlignment = Alignment.Center,
                            ) { HlText(day.shortLabel, size = HlSize.t14, weight = FontWeight.Bold, color = if (on) c.teal else c.text) }
                        }
                    }
                    HlText(
                        d.weekdaysError ?: if (d.effectiveInterval == IntervalChoice.TWICE_WEEKLY) "3–4일 간격이 되도록 골라 주세요" else "한 요일을 골라 주세요",
                        size = HlSize.t12, color = if (d.weekdaysError != null) c.danger else c.muted, weight = if (d.weekdaysError != null) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }

            Field("투약 시각") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TIME_PRESETS.forEach { m -> HlChip(fmt.minutes(m), d.timeMinutes == m, { actions.draft { it.copy(timeMinutes = m) } }, padding = PaddingValues(horizontal = 14.dp)) }
                    val custom = d.timeMinutes !in TIME_PRESETS
                    HlChip(if (custom) fmt.minutes(d.timeMinutes) else "직접 선택", custom, { picker = Pick.TIME }, padding = PaddingValues(horizontal = 14.dp))
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HlText("시작일", size = HlSize.t13, weight = FontWeight.SemiBold, color = c.muted)
                    DateTile(
                        if (startDate == today) "오늘 · ${startDate.monthValue}월 ${startDate.dayOfMonth}일" else dateLabel(startDate, today),
                        error = false, onClick = if (existing == null) ({ picker = Pick.START }) else null,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HlText("종료일 · 선택", size = HlSize.t13, weight = FontWeight.SemiBold, color = c.muted)
                    DateTile(endDate?.let { dateLabel(it, today) } ?: "없음", error = endError, onClick = { picker = Pick.END })
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HlChip("종료일 없음", endDate == null, { actions.draft { it.copy(endMillis = null) } }, minHeight = 44.dp, size = HlSize.t13, shape = RoundedCornerShape(HlRadius.chipSmall), padding = PaddingValues(horizontal = 12.dp))
                HlChip("날짜 선택", endDate != null, { picker = Pick.END }, minHeight = 44.dp, size = HlSize.t13, shape = RoundedCornerShape(HlRadius.chipSmall), padding = PaddingValues(horizontal = 12.dp))
            }
            if (endError) HlFieldError("종료일이 시작일(${startDate.monthValue}월 ${startDate.dayOfMonth}일)보다 빨라요")

            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.chip)).background(c.blueSoft).padding(horizontal = 14.dp, vertical = 12.dp)) {
                HlText(previewText(d, preview, fmt, valid), size = HlSize.t13, lineHeight = 1.5f)
            }

            HlButton("일정 저장", actions.save, Modifier.fillMaxWidth(), enabled = valid)
            if (existing != null) {
                HlButton("일정 지우기", { actions.askDelete(existing.id) }, Modifier.fillMaxWidth(), kind = HlButtonKind.Text, minHeight = 48.dp, content = c.danger, size = HlSize.t14)
            }
        }
    }

    when (picker) {
        Pick.START -> DatePickDialog(
            seed = startDate, zone = fmt.zone, onDismiss = { picker = Pick.NONE },
            onPicked = { date: LocalDate -> actions.draft { it.copy(startMillis = date.atStartOfDay(fmt.zone).toInstant().toEpochMilli()) }; picker = Pick.NONE },
        )
        Pick.END -> DatePickDialog(
            seed = endDate ?: maxOf(startDate, today), zone = fmt.zone, onDismiss = { picker = Pick.NONE },
            onPicked = { date: LocalDate -> actions.draft { it.copy(endMillis = date.atStartOfDay(fmt.zone).toInstant().toEpochMilli()) }; picker = Pick.NONE },
        )
        Pick.TIME -> TimePickDialog(
            initialMinutes = d.timeMinutes, is24Hour = fmt.clock24, onDismiss = { picker = Pick.NONE },
            onPicked = { m -> actions.draft { it.copy(timeMinutes = m) }; picker = Pick.NONE },
        )
        Pick.NONE -> Unit
    }
}

private fun dateLabel(date: LocalDate, today: LocalDate): String =
    "${if (date.year != today.year) "${date.year}년 " else ""}${date.monthValue}월 ${date.dayOfMonth}일 (${date.dayOfWeek.shortLabel})"

/** "매주 오전 9:00 · 첫 예정 10월 10일 (토) · 이후 4회: 10/17 · 10/24 · 10/31 · 11/7". */
private fun previewText(d: ScheduleDraft, dates: List<Instant>, fmt: Fmt, valid: Boolean): String {
    if (!valid || dates.isEmpty()) return "입력을 확인하면 다음 예정일을 보여드려요."
    val every = when (d.effectiveInterval) {
        IntervalChoice.DAILY -> "매일"
        IntervalChoice.TWICE_WEEKLY -> "주 2회"
        IntervalChoice.WEEKLY -> "매주"
        IntervalChoice.BIWEEKLY -> "2주마다"
        IntervalChoice.CUSTOM -> "${d.customDays.trim()}일마다"
    }
    val first = dates.first()
    val firstText = if (first.atZone(fmt.zone).toLocalDate() == fmt.now.atZone(fmt.zone).toLocalDate()) "오늘" else fmt.dateDay(first)
    val rest = dates.drop(1).take(4).joinToString(" · ") { fmt.short(it) }
    return "$every ${fmt.minutes(d.timeMinutes)} · 첫 예정 $firstText${if (rest.isNotEmpty()) " · 이후 ${dates.drop(1).take(4).size}회: $rest" else ""}"
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HlText(label, size = HlSize.t13, weight = FontWeight.SemiBold, color = Hl.colors.muted)
        content()
    }
}

@Composable
private fun DateTile(text: String, error: Boolean, onClick: (() -> Unit)?) {
    val c = Hl.colors
    val shape = RoundedCornerShape(HlRadius.chip)
    Row(
        Modifier
            .fillMaxWidth().heightIn(min = 56.dp).clip(shape).background(c.input)
            .border(if (error) 1.5.dp else 1.dp, if (error) c.danger else c.line, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { HlText(text, size = HlSize.t14, weight = FontWeight.Bold) }
}
