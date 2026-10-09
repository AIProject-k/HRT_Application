package com.hormonelog.app.feature.timeline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.doseSummary
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.RowTarget
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlMenuItem
import com.hormonelog.app.ui.kit.HlSheet
import com.hormonelog.app.ui.kit.HlSheetTitle

/** What a timeline row stands for, in two lines for the head of its menu. */
private fun describe(s: AppState, target: RowTarget, fmt: Fmt): Pair<String, String> = when (target) {
    is RowTarget.Dose -> s.doses.firstOrNull { it.id == target.id }?.let {
        doseSummary(it.drug, it.route, it.amountEntered, it.enteredUnit) to "${fmt.dateDay(it.occurredAt)} ${fmt.time(it.occurredAt)} · ${it.source.label}"
    }
    is RowTarget.Lab -> s.labs.firstOrNull { it.id == target.id }?.let { l ->
        "검사 결과" to (l.collectedAt?.let { "${fmt.dateDay(it)} ${fmt.time(it)}" } ?: "채혈 시각 모름")
    }
    is RowTarget.Journal -> s.journal.firstOrNull { it.id == target.id }?.let { "다른 기록" to "${fmt.dateDay(it.at)} ${fmt.time(it.at)}" }
    is RowTarget.Memo -> s.memos.firstOrNull { it.id == target.id }?.let { it.title to fmt.dateDay(it.date.atTime(12, 0).atZone(fmt.zone).toInstant()) }
} ?: ("기록" to "")

/** 수정 · 복제 · 삭제 for one row; the touch targets are the whole 56dp rows. */
@Composable
fun RowMenuSheet(
    s: AppState, target: RowTarget, fmt: Fmt,
    onEdit: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit,
) {
    val (title, sub) = describe(s, target, fmt)
    HlSheet(onDismiss = onDismiss) {
        HlSheetTitle(title, sub.ifEmpty { null })
        Column(Modifier.padding(horizontal = 12.dp).navigationBarsPadding().padding(bottom = 12.dp)) {
            HlMenuItem("수정", onEdit, icon = HlIcon.Edit)
            HlMenuItem(if (target is RowTarget.Memo) "복제해서 오늘 기록" else "복제해서 지금 기록", onDuplicate, icon = HlIcon.Copy)
            HlMenuItem("삭제", onDelete, icon = HlIcon.Trash, danger = true)
        }
    }
}

/** "무엇을 기록할까요?" */
@Composable
fun AddRecordSheet(
    onDose: () -> Unit, onLab: () -> Unit, onCondition: () -> Unit, onBody: () -> Unit,
    onStock: () -> Unit, onExtraLabs: () -> Unit, onMemo: () -> Unit, onDismiss: () -> Unit,
) {
    HlSheet(onDismiss = onDismiss) {
        HlSheetTitle("무엇을 기록할까요?")
        Column(Modifier.padding(horizontal = 12.dp).navigationBarsPadding().padding(bottom = 12.dp)) {
            HlMenuItem("투약", onDose, icon = HlIcon.Injection)
            HlMenuItem("검사 결과", onLab, icon = HlIcon.Lab)
            HlMenuItem("컨디션·증상", onCondition, icon = HlIcon.Condition)
            HlMenuItem("체중·혈압", onBody, icon = HlIcon.Body)
            HlMenuItem("재고", onStock, icon = HlIcon.Box)
            HlMenuItem("추가 검사 (LH·FSH 등)", onExtraLabs, icon = HlIcon.Lab)
            HlMenuItem("병원 메모", onMemo, icon = HlIcon.Memo)
        }
    }
}
