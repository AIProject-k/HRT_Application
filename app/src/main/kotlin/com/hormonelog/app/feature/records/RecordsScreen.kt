package com.hormonelog.app.feature.records

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hormonelog.app.analysis.StockLogic
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.doseSummary
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.Combo
import com.hormonelog.app.state.JournalDraft
import com.hormonelog.app.state.RecordTab
import com.hormonelog.app.state.frequentCombos
import com.hormonelog.app.state.plainNumber
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlButton
import com.hormonelog.app.ui.kit.HlButtonKind
import com.hormonelog.app.ui.kit.HlChip
import com.hormonelog.app.ui.kit.HlFieldError
import com.hormonelog.app.ui.kit.HlMenuItem
import com.hormonelog.app.ui.kit.HlSegmented
import com.hormonelog.app.ui.kit.HlSheet
import com.hormonelog.app.ui.kit.HlSheetTitle
import com.hormonelog.app.ui.kit.HlTextAction
import com.hormonelog.app.ui.kit.HlTextField
import com.hormonelog.app.ui.kit.HlTopBar
import com.hormonelog.app.ui.kit.ScreenScroll
import com.hormonelog.app.ui.kit.SegItem
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.core.domain.ExtraLab
import com.hormonelog.core.domain.Route
import com.hormonelog.core.domain.StockItem
import com.hormonelog.core.domain.Symptom
import java.util.UUID

class RecordsActions(
    val back: () -> Unit,
    val tab: (RecordTab) -> Unit,
    val draft: ((JournalDraft) -> JournalDraft) -> Unit,
    val toggleSymptom: (Symptom) -> Unit,
    val save: () -> Unit,
    val stockChange: (UUID, Int) -> Unit,
    val stockRemove: (UUID) -> Unit,
    val stockAdd: () -> Unit,
)

private val SCORES = listOf(1 to "나쁨", 2 to "", 3 to "보통", 4 to "", 5 to "좋음")

@Composable
fun RecordsScreen(s: AppState, fmt: Fmt, actions: RecordsActions) {
    val d = s.journalDraft
    val editing = d.editingId != null
    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        HlTopBar(if (editing) "기록 수정" else "다른 기록", actions.back)
        ScreenScroll(padding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 24.dp), gap = 14.dp, bottomInset = true) {
            // An existing entry is edited as the kind it is; the other forms would only be a way to lose it.
            if (!editing) {
                HlSegmented(
                    listOf(SegItem(RecordTab.CONDITION, "컨디션"), SegItem(RecordTab.BODY, "몸"), SegItem(RecordTab.STOCK, "재고"), SegItem(RecordTab.LABS, "추가 검사")),
                    d.tab, actions.tab,
                )
            }
            when (d.tab) {
                RecordTab.CONDITION -> ConditionForm(d, actions)
                RecordTab.BODY -> BodyForm(s, d, fmt, actions)
                RecordTab.STOCK -> StockList(s, fmt, actions)
                RecordTab.LABS -> LabsForm(d, actions)
            }
            if (d.tab != RecordTab.STOCK) HlButton("저장", actions.save, Modifier.fillMaxWidth(), enabled = d.canSave)
        }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HlText(label, size = HlSize.t13, weight = FontWeight.SemiBold, color = Hl.colors.muted)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionForm(d: JournalDraft, actions: RecordsActions) {
    val c = Hl.colors
    Field("오늘 컨디션") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SCORES.forEach { (n, label) ->
                val on = d.score == n
                val shape = RoundedCornerShape(HlRadius.chip)
                Column(
                    Modifier
                        .weight(1f).heightIn(min = 56.dp).clip(shape)
                        .background(if (on) c.tealSoft else Color.Transparent)
                        .border(1.dp, if (on) c.teal else c.line, shape)
                        .selectable(selected = on, role = Role.RadioButton) { actions.draft { it.copy(score = n) } },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                ) {
                    HlText("$n", size = HlSize.t18, weight = FontWeight.Bold, color = if (on) c.teal else c.text)
                    HlText(label, size = HlSize.t11, color = if (on) c.teal else c.muted)
                }
            }
        }
    }
    Field("증상 · 여러 개 선택") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Symptom.entries.forEach { sym ->
                HlChip(sym.label, sym in d.symptoms, { actions.toggleSymptom(sym) }, minHeight = 44.dp, size = HlSize.t13, shape = RoundedCornerShape(999.dp), padding = PaddingValues(horizontal = 12.dp), multi = true)
            }
        }
    }
    Field("메모 · 선택") {
        HlTextField(d.note, { v -> actions.draft { it.copy(note = v.take(500)) } }, label = "메모", placeholder = "오늘 느낀 점", keyboard = KeyboardType.Text, textSize = HlSize.t14, singleLine = false, minLines = 3, minHeight = 80.dp)
    }
}

@Composable
private fun BodyForm(s: AppState, d: JournalDraft, fmt: Fmt, actions: RecordsActions) {
    val c = Hl.colors
    val lastWeight = s.journal.filter { it.weightKg != null && it.id != d.editingId }.maxByOrNull { it.at }
    Field("체중") {
        HlTextField(
            d.weight, { v -> actions.draft { it.copy(weight = v.filter { ch -> ch.isDigit() || ch == '.' }.take(6)) } },
            label = "체중", placeholder = "예: 58.4", unit = "kg", error = d.weightError != null,
        )
        d.weightError?.let { HlFieldError(it) }
        if (lastWeight != null) HlText("지난 기록 ${plainNumber(lastWeight.weightKg!!)} kg · ${fmt.date(lastWeight.at)}", size = HlSize.t12, color = c.muted)
    }
    Field("혈압") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                HlTextField(d.systolic, { v -> actions.draft { it.copy(systolic = v.filter(Char::isDigit).take(3)) } }, label = "수축기 혈압", placeholder = "수축기", keyboard = KeyboardType.Number, textSize = HlSize.t16, error = d.pressureError != null)
            }
            HlText("/", color = c.muted)
            Box(Modifier.weight(1f)) {
                HlTextField(d.diastolic, { v -> actions.draft { it.copy(diastolic = v.filter(Char::isDigit).take(3)) } }, label = "이완기 혈압", placeholder = "이완기", keyboard = KeyboardType.Number, textSize = HlSize.t16, error = d.pressureError != null)
            }
            HlText("mmHg", size = HlSize.t13, color = c.muted)
        }
        d.pressureError?.let { HlFieldError(it) }
        HlText("스피로노락톤을 쓰면 혈압·칼륨을 함께 기록해 두면 진료 때 보기 좋아요.", size = HlSize.t12, color = c.muted, lineHeight = 1.5f)
    }
}

@Composable
private fun LabsForm(d: JournalDraft, actions: RecordsActions) {
    val c = Hl.colors
    HlText("검사지에 있는 항목만 채우면 돼요. 곡선에는 쓰지 않고 기록·리포트에만 보여요.", size = HlSize.t13, color = c.muted, lineHeight = 1.5f)
    ExtraLab.entries.forEach { lab ->
        val value = d.labs[lab].orEmpty()
        val bad = value.isNotBlank() && value.trim().toDoubleOrNull()?.let { it > 0 } != true
        val shape = RoundedCornerShape(HlRadius.chip)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape).background(c.input).border(if (bad) 1.5.dp else 1.dp, if (bad) c.danger else c.line, shape).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HlText(lab.label, size = HlSize.t14, weight = FontWeight.SemiBold, modifier = Modifier.width(84.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                BasicTextField(
                    value = value,
                    onValueChange = { v -> actions.draft { it.copy(labs = it.labs + (lab to v.filter { ch -> ch.isDigit() || ch == '.' }.take(8))) } },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = HlSize.t16, fontWeight = FontWeight.Bold, textAlign = TextAlign.End),
                    cursorBrush = SolidColor(c.teal),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${lab.label} 값" },
                    decorationBox = { inner -> Box(contentAlignment = Alignment.CenterEnd) { if (value.isEmpty()) HlText("—", size = HlSize.t16, color = c.dim); inner() } },
                )
            }
            HlText(lab.unit, size = HlSize.t12, color = c.muted, modifier = Modifier.width(52.dp), align = TextAlign.End)
        }
    }
}

// ── stock ─────────────────────────────────────────────────────

private fun unitWord(item: StockItem): String = when (item.route) {
    Route.IM_INJECTION, Route.SC_INJECTION -> "개"
    Route.ORAL, Route.SUBLINGUAL -> "정"
    Route.PATCH -> "매"
    Route.GEL -> "개"
}

private fun stockName(item: StockItem): String {
    val base = doseSummary(item.drug, item.route, item.amountEntered, item.enteredUnit).substringBefore(" · ")
    return if (item.route == Route.IM_INJECTION || item.route == Route.SC_INJECTION) "$base 앰플" else base
}

@Composable
private fun StockList(s: AppState, fmt: Fmt, actions: RecordsActions) {
    val c = Hl.colors
    if (s.stock.isEmpty()) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HlText("남은 약을 적어 두세요", size = HlSize.t14, weight = FontWeight.Bold)
            HlText("투약을 기록하면 같은 약의 재고가 하나씩 줄고, 일정이 있으면 언제 떨어질지 알려 줘요.", size = HlSize.t13, color = c.muted, lineHeight = 1.5f)
        }
    }
    s.stock.forEach { item ->
        val plan = StockLogic.planFor(item, s.regimens, fmt.now)
        val days = StockLogic.daysLeft(item, s.regimens, fmt.now)
        val low = days != null && days <= StockLogic.LOW_DAYS
        val eta = when {
            item.count == 0 -> "남은 재고 없음"
            days == null -> "일정이 있으면 소진 시점을 알려 드려요"
            else -> StockLogic.runsOutOn(days, fmt.now, fmt.zone).let { "약 ${days}일분 · ${it.monthValue}월 ${it.dayOfMonth}일경 소진 예상" }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    HlText(stockName(item), size = HlSize.t14, weight = FontWeight.Bold)
                    HlText(eta, size = HlSize.t12, weight = FontWeight.Bold, color = if (low || item.count == 0) c.orange else c.muted)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StepButton("−", "하나 줄이기", enabled = item.count > 0) { actions.stockChange(item.id, -1) }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                    HlText("${item.count}", size = HlSize.t22, weight = FontWeight.Bold, tabular = true)
                    HlText(" ${unitWord(item)}", size = HlSize.t13, color = c.muted, modifier = Modifier.padding(bottom = 3.dp))
                }
                StepButton("+", "하나 늘리기", enabled = true) { actions.stockChange(item.id, 1) }
            }
            HlText(
                (StockLogic.usageText(plan, unitWord(item))?.let { "$it · " } ?: "") + "투약을 기록하면 자동으로 줄어요",
                size = HlSize.t12, color = c.muted,
            )
            HlTextActionRow("이 항목 지우기") { actions.stockRemove(item.id) }
        }
    }
    HlButton("재고 항목 추가", actions.stockAdd, Modifier.fillMaxWidth(), kind = HlButtonKind.Secondary, leading = HlIcon.Plus, size = HlSize.t14)
}

@Composable
private fun HlTextActionRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth()) { HlTextAction(label, onClick, color = Hl.colors.muted, size = HlSize.t12) }
}

@Composable
private fun StepButton(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Hl.colors
    val shape = RoundedCornerShape(HlRadius.chip)
    Box(
        Modifier
            .width(48.dp).heightIn(min = 48.dp).clip(shape).border(1.dp, c.line, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { HlText(label, size = HlSize.t18, color = if (enabled) c.text else c.dim) }
}

/** Which product to start counting: the plans that are running and what has been taken most. */
@Composable
fun StockAddSheet(s: AppState, fmt: Fmt, onPick: (Combo) -> Unit, onDismiss: () -> Unit) {
    val options = remember(s.regimens, s.doses, s.stock) { stockOptions(s, fmt) }
    HlSheet(onDismiss = onDismiss) {
        HlSheetTitle("어떤 약의 재고인가요?", "투약 기록과 일정에서 골랐어요")
        Column(Modifier.padding(horizontal = 12.dp).navigationBarsPadding().padding(bottom = 12.dp)) {
            if (options.isEmpty()) {
                HlText("먼저 투약을 기록하거나 반복 일정을 만들면 여기서 고를 수 있어요.", size = HlSize.t13, color = Hl.colors.muted, lineHeight = 1.5f, modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp))
            }
            options.forEach { (combo, note) ->
                HlMenuItem(doseSummary(combo.drug, combo.route, combo.amount, combo.unit), { onPick(combo) }, note = note)
            }
        }
    }
}

private fun stockOptions(s: AppState, fmt: Fmt): List<Pair<Combo, String>> {
    val have = s.stock.map { Triple(it.drug to it.route, it.amountEntered, it.enteredUnit) }.toSet()
    fun owned(c: Combo) = Triple(c.drug to c.route, c.amount, c.unit) in have
    val fromPlans = s.regimens.filter { it.isRunningAt(fmt.now) }.map { Combo(it.drug, it.route, it.amountEntered, it.enteredUnit, it.patchCycle, 0, it.startAt) to "일정" }
    val fromDoses = frequentCombos(s.doses, fmt.now, days = 36_500, limit = 6).map { it to "${it.uses}회 기록" }
    return (fromPlans + fromDoses).filterNot { owned(it.first) }.distinctBy { Triple(it.first.drug to it.first.route, it.first.amount, it.first.unit) }
}
