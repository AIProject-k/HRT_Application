package com.hormonelog.app.feature.onboarding

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hormonelog.app.feature.common.DatePickDialog
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.feature.common.shortLabel
import com.hormonelog.app.feature.common.summary
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.IntervalChoice
import com.hormonelog.app.state.OnboardingOps
import com.hormonelog.app.state.OnboardingState
import com.hormonelog.app.state.PATCH_STRENGTHS
import com.hormonelog.app.state.drugsFor
import com.hormonelog.app.state.plainNumber
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlButton
import com.hormonelog.app.ui.kit.HlChip
import com.hormonelog.app.ui.kit.HlDisclaimer
import com.hormonelog.app.ui.kit.HlFieldError
import com.hormonelog.app.ui.kit.HlIconButton
import com.hormonelog.app.ui.kit.HlMenuItem
import com.hormonelog.app.ui.kit.HlSegmented
import com.hormonelog.app.ui.kit.HlSheet
import com.hormonelog.app.ui.kit.HlSheetTitle
import com.hormonelog.app.ui.kit.SegItem
import com.hormonelog.app.ui.kit.HlTextField
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.core.domain.Drug
import com.hormonelog.core.domain.E2Unit
import com.hormonelog.core.domain.GonadalStatus
import com.hormonelog.core.domain.PatchCycle
import com.hormonelog.core.domain.Regimen
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.TUnit
import java.time.Instant
import java.time.LocalDate

class OnboardingActions(
    val back: () -> Unit,
    val skip: () -> Unit,
    val next: () -> Unit,
    val edit: ((OnboardingState) -> OnboardingState) -> Unit,
    val route: (Route) -> Unit,
)

private enum class Pick { NONE, START, BASELINE }

/**
 * The six steps of first-run setup. Every step but the first can be skipped, and the
 * schedule step can be left empty: the app is fully usable with nothing entered.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(s: AppState, fmt: Fmt, actions: OnboardingActions) {
    val c = Hl.colors
    val o = s.onboarding
    var picker by remember { mutableStateOf(Pick.NONE) }
    val today = fmt.now.atZone(fmt.zone).toLocalDate()

    Column(Modifier.fillMaxSize().padding(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // ── progress: back, six bars, skip ──
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (o.step > 0) HlIconButton(HlIcon.Back, "뒤로", actions.back, modifier = Modifier.offset(x = (-12).dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(OnboardingState.STEPS) { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= o.step) c.teal else c.line))
                }
            }
            // The last step has nothing after it to skip to; its button finishes the setup.
            if (o.step > 0 && o.step < OnboardingState.STEPS - 1) {
                Box(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = actions.skip).padding(start = 12.dp, end = 4.dp), contentAlignment = Alignment.Center) {
                    HlText("건너뛰기", size = HlSize.t14, weight = FontWeight.SemiBold, color = c.muted)
                }
            }
        }
        HlText("${o.step + 1} / ${OnboardingState.STEPS}", size = HlSize.t12, weight = FontWeight.SemiBold, color = c.teal)

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            when (o.step) {
                0 -> Welcome()
                1 -> ScheduleStep(o, actions)
                2 -> StartStep(s, o, fmt, today, onPickStart = { picker = Pick.START }, actions = actions)
                3 -> GonadalStep(o, actions)
                4 -> UnitsStep(o, actions)
                else -> BaselineStep(o, fmt, onPickDate = { picker = Pick.BASELINE }, actions = actions)
            }
        }

        val ready = o.step != 1 || OnboardingOps.scheduleReady(o)
        HlButton(CTA[o.step], actions.next, Modifier.fillMaxWidth(), enabled = ready)
        HlDisclaimer()
    }

    when (picker) {
        Pick.START -> DatePickDialog(
            seed = OnboardingOps.startDate(o, fmt.now, fmt.zone), zone = fmt.zone, latest = today,
            onDismiss = { picker = Pick.NONE },
            onPicked = { d: LocalDate -> actions.edit { it.copy(startMillis = d.atStartOfDay(fmt.zone).toInstant().toEpochMilli()) }; picker = Pick.NONE },
        )
        Pick.BASELINE -> DatePickDialog(
            seed = o.baselineMillis?.let { Instant.ofEpochMilli(it).atZone(fmt.zone).toLocalDate() } ?: today, zone = fmt.zone, latest = today,
            onDismiss = { picker = Pick.NONE },
            onPicked = { d: LocalDate -> actions.edit { it.copy(baselineMillis = d.atStartOfDay(fmt.zone).toInstant().plusSeconds(9 * 3600L).toEpochMilli()) }; picker = Pick.NONE },
        )
        Pick.NONE -> Unit
    }
}

private val CTA = listOf("시작하기", "이 내용으로 일정 만들기", "다음", "다음", "다음", "완료")

@Composable
private fun StepTitle(text: String) {
    HlText(text, size = HlSize.t22, weight = FontWeight.Bold, lineHeight = 1.35f)
}

@Composable
private fun StepHint(text: String) {
    HlText(text, size = HlSize.t13, color = Hl.colors.muted, lineHeight = 1.5f)
}

@Composable
private fun Group(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HlText(label, size = HlSize.t13, weight = FontWeight.SemiBold, color = Hl.colors.muted)
        content()
    }
}

// ── 1 / 6 ─────────────────────────────────────────────────────

@Composable
private fun Welcome() {
    val c = Hl.colors
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepTitle("투약과 검사를 기록하면\n예상 곡선을 그려 드려요")
        HlText("문헌 기반 약동학 모델로 E2·Total T의 예상 범위를 보여주고, 검사값이 쌓이면 내 몸에 맞게 보정해요.", size = HlSize.t14, color = c.muted, lineHeight = 1.6f)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card).padding(horizontal = 16.dp, vertical = 6.dp)) {
            Promise(HlIcon.Phone, "기기 안에만 저장해요", "계정·서버·네트워크를 쓰지 않아요.", divider = true)
            Promise(HlIcon.Flow, "참고용 추정이에요", "예상값은 항상 범위로 보여주고, 실측값과 구분해요.", divider = true)
            Promise(HlIcon.CircleMinus, "용량을 권하지 않아요", "용량 판단은 의료진과 함께 해 주세요.", divider = false)
        }
    }
}

@Composable
private fun Promise(icon: HlIcon, title: String, body: String, divider: Boolean) {
    val c = Hl.colors
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            HlIcon(icon, size = 22.dp, tint = c.teal, strokeWidth = 1.8f)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HlText(title, size = HlSize.t14, weight = FontWeight.SemiBold)
                HlText(body, size = HlSize.t13, color = c.muted, lineHeight = 1.5f)
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(c.line))
    }
}

// ── 2 / 6 ─────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleStep(o: OnboardingState, actions: OnboardingActions) {
    val c = Hl.colors
    var drugMenu by remember { mutableStateOf(false) }
    // Anti-androgens are not part of the first schedule; they can be added later as their own schedule.
    val drugs = drugsFor(o.route).filterNot { it == Drug.CYPROTERONE || it == Drug.SPIRONOLACTONE }
    val injection = o.route == Route.IM_INJECTION || o.route == Route.SC_INJECTION

    StepTitle("지금 쓰는 약을 알려 주세요")
    StepHint("반복 일정을 만들어 두면 매번 입력하지 않아도 돼요. 나중에 추가해도 괜찮아요.")
    Group("경로") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("주사", Route.IM_INJECTION) { r: Route -> r == Route.IM_INJECTION || r == Route.SC_INJECTION },
                Triple("경구", Route.ORAL) { r: Route -> r == Route.ORAL },
                Triple("설하", Route.SUBLINGUAL) { r: Route -> r == Route.SUBLINGUAL },
                Triple("패치", Route.PATCH) { r: Route -> r == Route.PATCH },
                Triple("젤", Route.GEL) { r: Route -> r == Route.GEL },
            ).forEach { (label, route, matches) ->
                HlChip(label, matches(o.route), { if (!matches(o.route)) actions.route(route) })
            }
        }
        if (injection) {
            HlSegmented(
                listOf(SegItem(Route.IM_INJECTION, "근육(IM)"), SegItem(Route.SC_INJECTION, "피하(SC)")),
                o.route, actions.route,
            )
        }
    }

    // Drug and amount sit on one line, as in the design; the drug opens a short list when there is a choice.
    Group(if (o.route == Route.PATCH) "약물" else "약물 · 용량") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val shape = RoundedCornerShape(HlRadius.chip)
            Row(
                Modifier
                    .weight(1f).heightIn(min = 56.dp).clip(shape).background(c.input).border(1.dp, c.line, shape)
                    .then(if (drugs.size > 1) Modifier.clickable(role = Role.Button) { drugMenu = true } else Modifier)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HlText(o.drug.label, size = HlSize.t14, modifier = Modifier.weight(1f))
                if (drugs.size > 1) HlIcon(HlIcon.Chevron, size = 14.dp, tint = c.muted, strokeWidth = 2f, modifier = Modifier.rotate(90f))
            }
            if (o.route != Route.PATCH) {
                val invalid = o.amountText.isNotEmpty() && !OnboardingOps.scheduleReady(o)
                HlTextField(
                    value = o.amountText,
                    onValueChange = { v -> actions.edit { it.copy(amountText = v.filter { ch -> ch.isDigit() || ch == '.' }.take(7)) } },
                    modifier = Modifier.width(112.dp),
                    label = "용량", placeholder = "예: 5", unit = "mg", keyboard = KeyboardType.Decimal, error = invalid, textSize = HlSize.t16,
                )
            }
        }
        if (o.route != Route.PATCH && o.amountText.isNotEmpty() && !OnboardingOps.scheduleReady(o)) HlFieldError("0보다 크고 500 이하인 숫자로 입력해 주세요")
    }

    if (o.route == Route.PATCH) {
        Group("패치 규격 · µg/일") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PATCH_STRENGTHS.forEach { v -> HlChip(plainNumber(v), o.patchStrength == v, { actions.edit { it.copy(patchStrength = v) } }) }
            }
        }
        Group("교체 주기") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PatchCycle.entries.forEach { p -> HlChip(p.label, o.patchCycle == p, { actions.edit { it.copy(patchCycle = p) } }) }
            }
        }
    } else {
        Group("간격") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    IntervalChoice.DAILY to "매일", IntervalChoice.TWICE_WEEKLY to "주 2회", IntervalChoice.WEEKLY to "매주", IntervalChoice.BIWEEKLY to "2주",
                ).forEach { (v, l) -> HlChip(l, o.interval == v, { actions.edit { it.copy(interval = v) } }) }
            }
        }
    }
    HlText("항안드로젠 같은 다른 약은 내 정보 › 반복 일정에서 따로 추가할 수 있어요.", size = HlSize.t12, color = c.muted, lineHeight = 1.5f)

    if (drugMenu) {
        HlSheet(onDismiss = { drugMenu = false }) {
            HlSheetTitle("약물")
            Column(Modifier.padding(horizontal = 12.dp).navigationBarsPadding().padding(bottom = 12.dp)) {
                drugs.forEach { d -> HlMenuItem(d.label, { actions.edit { it.copy(drug = d) }; drugMenu = false }, selected = o.drug == d) }
            }
        }
    }
}
// ── 3 / 6 ─────────────────────────────────────────────────────

@Composable
private fun StartStep(s: AppState, o: OnboardingState, fmt: Fmt, today: LocalDate, onPickStart: () -> Unit, actions: OnboardingActions) {
    val c = Hl.colors
    val start = OnboardingOps.startDate(o, fmt.now, fmt.zone)
    val count = OnboardingOps.backfillCount(s, fmt.now, fmt.zone)
    val regimen = OnboardingOps.regimenOf(o, fmt.now, fmt.zone)
    StepTitle("언제부터 투약했나요?")
    DateField(dateText(start), onPickStart)
    if (regimen == null) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (count == 0) {
            HlText("기록할 지난 투약이 없어요", size = HlSize.t16, weight = FontWeight.Bold, lineHeight = 1.4f)
            HlText(
                if (start.isBefore(today)) "이 날짜 이후로 일정에 맞는 투약이 아직 없거나, 이미 기록돼 있어요." else "오늘부터 시작하는 일정이라 지난 투약은 없어요. 앞으로는 홈에서 한 번에 기록할 수 있어요.",
                size = HlSize.t13, color = c.muted, lineHeight = 1.6f,
            )
        } else {
            HlText("지난 투약 ${count}건을 기록할까요?", size = HlSize.t16, weight = FontWeight.Bold, lineHeight = 1.4f)
            HlText(
                "${regimen.summary()} · ${everyLabel(regimen)}\n${shortDate(start, today)} – ${shortDate(today, today)}",
                size = HlSize.t13, color = c.muted, lineHeight = 1.6f,
            )
            HlText("기록은 모두 ‘일정’ 출처로 표시되고, 타임라인에서 하나씩 고칠 수 있어요.", size = HlSize.t13, color = c.muted, lineHeight = 1.5f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HlChip("${count}건 기록하기", o.backfill, { actions.edit { it.copy(backfill = true) } }, Modifier.weight(1f))
                HlChip("기록 안 함", !o.backfill, { actions.edit { it.copy(backfill = false) } }, Modifier.weight(1f))
            }
        }
    }
}

/** "2026년 3월 1일 – 10월 4일": the year appears only when it differs from today's. */
private fun shortDate(d: LocalDate, today: LocalDate): String =
    "${if (d.year != today.year) "${d.year}년 " else ""}${d.monthValue}월 ${d.dayOfMonth}일"

private fun dateText(d: LocalDate): String = "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일 (${d.dayOfWeek.shortLabel})"

private fun everyLabel(r: Regimen): String = when {
    r.route == Route.PATCH -> if (r.everyDays == 7) "주 1회 교체" else "주 2회 교체"
    r.isWeekdayPlan && r.weekdaySet.size == 2 -> "주 2회"
    r.everyDays == 1 -> "매일"
    r.everyDays == 7 -> "7일마다"
    else -> "${r.everyDays}일마다"
}

@Composable
private fun DateField(text: String, onClick: () -> Unit, prefix: String? = null, placeholder: Boolean = false) {
    val c = Hl.colors
    val shape = RoundedCornerShape(HlRadius.chip)
    Row(
        Modifier
            .fillMaxWidth().heightIn(min = 56.dp).clip(shape).background(c.input).border(1.dp, c.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (prefix != null) HlText(prefix, size = HlSize.t14, color = c.muted)
        HlText(text, size = if (prefix == null) HlSize.t16 else HlSize.t14, weight = FontWeight.SemiBold, color = if (placeholder) c.dim else c.text)
        if (prefix == null) HlIcon(HlIcon.Calendar, size = 20.dp, tint = c.muted, strokeWidth = 1.8f)
    }
}

// ── 4 / 6 ─────────────────────────────────────────────────────

@Composable
private fun RadioCard(label: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    val c = Hl.colors
    val shape = RoundedCornerShape(HlRadius.button)
    Row(
        Modifier
            .fillMaxWidth().heightIn(min = 56.dp).clip(shape)
            .background(if (selected) c.tealSoft else Color.Transparent)
            .border(1.dp, if (selected) c.teal else c.line, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(20.dp).border(2.dp, if (selected) c.teal else c.line, CircleShape), contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(c.teal))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            HlText(label, size = HlSize.t14, weight = FontWeight.SemiBold)
            if (sub != null) HlText(sub, size = HlSize.t12, color = c.muted, lineHeight = 1.4f)
        }
    }
}

@Composable
private fun GonadalStep(o: OnboardingState, actions: OnboardingActions) {
    StepTitle("고환이 있나요?")
    StepHint("Total T 예측에만 써요. 기기 밖으로 나가지 않아요.")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(GonadalStatus.INTACT, "있음", "고환에서 만드는 T를 예측에 포함해요"),
            Triple(GonadalStatus.POST_ORCHIECTOMY, "수술함", "고환 절제 후 기준으로 예측해요"),
            Triple(GonadalStatus.DECLINED, "말하고 싶지 않음", "Total T 곡선을 그리지 않아요"),
        ).forEach { (g, label, sub) -> RadioCard(label, sub, o.gonadal == g) { actions.edit { it.copy(gonadal = g) } } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnitsStep(o: OnboardingState, actions: OnboardingActions) {
    StepTitle("검사지에 적힌 단위를 골라 주세요")
    Group("E2 (에스트라디올)") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            E2Unit.entries.forEach { u -> HlChip(u.label, o.e2Unit == u, { actions.edit { it.copy(e2Unit = u) } }) }
        }
    }
    Group("Total T (테스토스테론)") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TUnit.entries.forEach { u -> HlChip(u.label, o.tUnit == u, { actions.edit { it.copy(tUnit = u) } }) }
        }
    }
    StepHint("검사마다 단위를 따로 바꿀 수도 있어요.")
}

@Composable
private fun BaselineStep(o: OnboardingState, fmt: Fmt, onPickDate: () -> Unit, actions: OnboardingActions) {
    val e2Error = o.baselineE2.isNotBlank() && o.baselineE2.trim().toDoubleOrNull()?.let { it > 0 } != true
    val tError = o.baselineT.isNotBlank() && o.baselineT.trim().toDoubleOrNull()?.let { it > 0 } != true
    StepTitle("HRT 시작 전 검사값이 있나요?")
    StepHint("선택이에요. 있으면 Total T 예측의 출발점으로 써요.")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Group("E2 · 실측") {
            HlTextField(
                o.baselineE2, { v -> actions.edit { it.copy(baselineE2 = v.filter { ch -> ch.isDigit() || ch == '.' }.take(8)) } },
                label = "HRT 시작 전 E2", placeholder = if (o.e2Unit == E2Unit.PG_ML) "예: 28" else "예: 103", unit = o.e2Unit.label, error = e2Error,
            )
            if (e2Error) HlFieldError("올바른 숫자를 입력해 주세요")
        }
        Group("Total T · 실측") {
            HlTextField(
                o.baselineT, { v -> actions.edit { it.copy(baselineT = v.filter { ch -> ch.isDigit() || ch == '.' }.take(8)) } },
                label = "HRT 시작 전 Total T",
                placeholder = when (o.tUnit) { TUnit.NG_DL -> "예: 520"; TUnit.NG_ML -> "예: 5.2"; TUnit.NMOL_L -> "예: 18" },
                unit = o.tUnit.label, error = tError,
            )
            if (tError) HlFieldError("올바른 숫자를 입력해 주세요")
        }
        val picked = o.baselineMillis?.let { Instant.ofEpochMilli(it).atZone(fmt.zone).toLocalDate() }
        DateField(
            text = picked?.let { dateText(it) } ?: "날짜 선택 · 모르면 비워 둬도 돼요",
            onClick = onPickDate, prefix = "채혈일", placeholder = picked == null,
        )
    }
}
