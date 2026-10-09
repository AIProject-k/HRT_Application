package com.hormonelog.app.feature.memos

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hormonelog.app.feature.common.DatePickDialog
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.TimePickDialog
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.feature.common.shortLabel
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.ClinicDraft
import com.hormonelog.app.state.MemoDraft
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlButton
import com.hormonelog.app.ui.kit.HlButtonKind
import com.hormonelog.app.ui.kit.HlChip
import com.hormonelog.app.ui.kit.HlFieldError
import com.hormonelog.app.ui.kit.HlIconButton
import com.hormonelog.app.ui.kit.HlSectionLabel
import com.hormonelog.app.ui.kit.HlSheet
import com.hormonelog.app.ui.kit.HlSheetTitle
import com.hormonelog.app.ui.kit.HlTextField
import com.hormonelog.app.ui.kit.HlTopBar
import com.hormonelog.app.ui.kit.ScreenScroll
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import com.hormonelog.core.domain.Clinic
import com.hormonelog.core.domain.PrescriptionBasis
import com.hormonelog.core.domain.Telehealth
import com.hormonelog.core.domain.VisitMemo
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class MemoActions(
    val back: () -> Unit,
    val report: () -> Unit,
    val editVisit: () -> Unit,
    val newMemo: () -> Unit,
    val editMemo: (UUID) -> Unit,
    val askDeleteMemo: (UUID) -> Unit,
    val newClinic: () -> Unit,
    val editClinic: (UUID) -> Unit,
    val askDeleteClinic: (UUID) -> Unit,
)

/** Opens an address the user wrote down, but only a web one: a memo must not be a way to launch other apps. */
private fun openLink(context: android.content.Context, url: String) {
    val uri = Uri.parse(url.trim())
    if (uri.scheme != "http" && uri.scheme != "https") return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun hostOf(url: String): String = runCatching { Uri.parse(url.trim()).host }.getOrNull()?.removePrefix("www.") ?: url.trim()

@Composable
fun MemosScreen(s: AppState, fmt: Fmt, actions: MemoActions) {
    val c = Hl.colors
    val context = LocalContext.current
    val visit = s.nextVisitMillis?.let(Instant::ofEpochMilli)

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        HlTopBar("병원 메모", actions.back)
        ScreenScroll(padding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 24.dp), gap = 12.dp, bottomInset = true) {
            // ── next visit ──
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (visit != null) {
                    val days = fmt.daysBetween(fmt.now, visit)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        HlText("다음 진료", size = HlSize.t12, weight = FontWeight.Bold, color = c.teal)
                        HlText(if (days > 0) "${days}일 남음" else if (days == 0L && visit.isAfter(fmt.now)) "오늘" else "지났어요", size = HlSize.t12, color = c.muted)
                    }
                    HlText("${fmt.dateDay(visit)} ${fmt.time(visit)}", size = HlSize.t16, weight = FontWeight.Bold)
                    HlText("진료 전에 리포트를 만들어 두면 편해요.", size = HlSize.t13, color = c.muted)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HlButton("리포트 만들기", actions.report, Modifier.weight(1f), minHeight = 48.dp, size = HlSize.t13)
                        HlButton("예약 수정", actions.editVisit, Modifier.weight(1f), kind = HlButtonKind.Secondary, minHeight = 48.dp, size = HlSize.t13)
                    }
                } else {
                    HlText("다음 진료", size = HlSize.t12, weight = FontWeight.Bold, color = c.teal)
                    HlText("예약된 진료가 없어요", size = HlSize.t16, weight = FontWeight.Bold)
                    HlText("날짜를 적어 두면 하루 전에 알려 드릴 수 있어요. 알림은 내 정보 › 알림에서 켜요.", size = HlSize.t13, color = c.muted, lineHeight = 1.5f)
                    HlButton("진료 예약 추가", actions.editVisit, Modifier.fillMaxWidth(), kind = HlButtonKind.Secondary, minHeight = 48.dp, size = HlSize.t13, leading = HlIcon.Calendar)
                }
            }

            // ── memos ──
            s.memos.forEach { memo -> MemoCard(memo, fmt, actions, onLink = { openLink(context, it) }) }
            DashedButton("메모 추가", actions.newMemo)

            // ── clinics ──
            HlSectionLabel("병원 정보", Modifier.padding(top = 8.dp))
            if (s.clinics.isEmpty()) {
                HlText("다녀 본 병원이나 알아본 병원을 적어 두세요. 처방 조건, 비대면 여부, 가격 메모를 한곳에 모아 볼 수 있어요.", size = HlSize.t13, color = c.muted, lineHeight = 1.5f)
            }
            s.clinics.forEach { clinic -> ClinicCard(clinic, actions, onLink = { openLink(context, it) }) }
            DashedButton("병원 추가", actions.newClinic)
            if (s.memos.isEmpty() && s.clinics.isEmpty() && visit == null) {
                HlText("메모와 병원 정보는 이 기기에만 저장돼요. 백업 파일에는 함께 담겨요.", size = HlSize.t12, color = c.muted, lineHeight = 1.5f)
            }
        }
    }
}

@Composable
private fun DashedButton(label: String, onClick: () -> Unit) {
    val c = Hl.colors
    val line = c.line
    Box(
        Modifier
            .fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(HlRadius.button))
            .drawBehind {
                drawRoundRect(line, size = Size(size.width, size.height), cornerRadius = CornerRadius(14.dp.toPx()), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { HlText(label, size = HlSize.t14, weight = FontWeight.Bold) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoCard(memo: VisitMemo, fmt: Fmt, actions: MemoActions, onLink: (String) -> Unit) {
    val c = Hl.colors
    val dateInstant = memo.date.atTime(12, 0).atZone(fmt.zone).toInstant()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card)
            .padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = if (memo.prescription != null || memo.link != null) 6.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).clickable(role = Role.Button) { actions.editMemo(memo.id) }.padding(top = 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                HlText(fmt.date(dateInstant), size = HlSize.t12, color = c.muted)
                HlText(memo.title, size = HlSize.t14, weight = FontWeight.Bold)
            }
            HlIconButton(HlIcon.Trash, "${memo.title} 삭제", { actions.askDeleteMemo(memo.id) }, tint = c.muted, iconSize = 18.dp)
        }
        if (memo.body.isNotBlank()) HlText(memo.body, size = HlSize.t13, lineHeight = 1.6f, modifier = Modifier.padding(end = 12.dp).clickable(role = Role.Button) { actions.editMemo(memo.id) })
        if (memo.prescription != null || memo.link != null) {
            FlowRow(Modifier.padding(end = 12.dp, bottom = 8.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                memo.prescription?.let { rx ->
                    Box(Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(HlRadius.chipSmall)).background(c.blueSoft).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                        HlText("처방 · $rx", size = HlSize.t12, weight = FontWeight.Bold, color = c.blue)
                    }
                }
                memo.link?.let { link -> LinkChip(hostOf(link)) { onLink(link) } }
            }
        }
    }
}

@Composable
private fun LinkChip(text: String, onClick: () -> Unit) {
    val c = Hl.colors
    Row(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(HlRadius.chipSmall)).border(1.dp, c.line, RoundedCornerShape(HlRadius.chipSmall)).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HlIcon(HlIcon.External, size = 14.dp, tint = c.teal, strokeWidth = 2f)
        HlText(text, size = HlSize.t12, weight = FontWeight.Bold, color = c.teal)
    }
}

@Composable
private fun ClinicCard(clinic: Clinic, actions: MemoActions, onLink: (String) -> Unit) {
    val c = Hl.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card).padding(start = 16.dp, top = 8.dp, end = 4.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f).clickable(role = Role.Button) { actions.editClinic(clinic.id) }.padding(top = 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                HlText(clinic.name, size = HlSize.t14, weight = FontWeight.Bold)
                val facts = listOfNotNull(
                    clinic.region.ifBlank { null },
                    clinic.prescriptionBasis.takeIf { it != PrescriptionBasis.UNKNOWN }?.label,
                    clinic.telehealth.takeIf { it != Telehealth.UNKNOWN }?.label,
                )
                if (facts.isNotEmpty()) HlText(facts.joinToString(" · "), size = HlSize.t12, color = c.muted)
            }
            HlIconButton(HlIcon.Trash, "${clinic.name} 삭제", { actions.askDeleteClinic(clinic.id) }, tint = c.muted, iconSize = 18.dp)
        }
        if (clinic.priceNote.isNotBlank()) HlText("가격 · ${clinic.priceNote}", size = HlSize.t13, lineHeight = 1.5f, modifier = Modifier.padding(end = 12.dp))
        if (clinic.memo.isNotBlank()) HlText(clinic.memo, size = HlSize.t13, color = c.muted, lineHeight = 1.5f, modifier = Modifier.padding(end = 12.dp))
        if (clinic.sourceUrl.isNotBlank()) Box(Modifier.padding(end = 12.dp)) { LinkChip(hostOf(clinic.sourceUrl)) { onLink(clinic.sourceUrl) } }
    }
}

// ── sheets ────────────────────────────────────────────────────

private enum class Pick { NONE, DATE, TIME }

/** A bottom sheet for a form: fixed header and footer around a scrolling body. */
@Composable
private fun FormSheet(title: String, onDismiss: () -> Unit, footer: @Composable () -> Unit, body: @Composable () -> Unit) {
    val c = Hl.colors
    HlSheet(onDismiss = onDismiss, fillHeight = true) {
        HlSheetTitle(title)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { body() }
        Box(Modifier.fillMaxWidth().heightIn(min = 1.dp).background(c.line))
        Column(Modifier.padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { footer() }
    }
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HlText(label, size = HlSize.t13, weight = FontWeight.SemiBold, color = Hl.colors.muted)
        content()
    }
}

@Composable
private fun TileButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: HlIcon = HlIcon.Calendar) {
    val c = Hl.colors
    val shape = RoundedCornerShape(HlRadius.chip)
    Row(
        modifier.heightIn(min = 56.dp).clip(shape).background(c.input).border(1.dp, c.line, shape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HlText(text, size = HlSize.t14, weight = FontWeight.Bold)
        HlIcon(icon, size = 18.dp, tint = c.muted)
    }
}

@Composable
fun MemoEditorSheet(draft: MemoDraft, fmt: Fmt, onEdit: ((MemoDraft) -> MemoDraft) -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val c = Hl.colors
    var picker by remember { mutableStateOf(false) }
    val linkError = draft.linkError
    FormSheet(
        title = if (draft.editingId != null) "메모 수정" else "메모 추가",
        onDismiss = onDismiss,
        footer = {
            HlButton("저장", onSave, Modifier.fillMaxWidth(), enabled = draft.canSave && linkError == null)
            if (onDelete != null) HlButton("메모 삭제", onDelete, Modifier.fillMaxWidth(), kind = HlButtonKind.Text, minHeight = 48.dp, content = c.danger, size = HlSize.t14)
        },
    ) {
        Field("진료일") {
            TileButton("${draft.date.year}년 ${draft.date.monthValue}월 ${draft.date.dayOfMonth}일 (${draft.date.dayOfWeek.shortLabel})", { picker = true }, Modifier.fillMaxWidth())
        }
        Field("제목") {
            HlTextField(draft.title, { v -> onEdit { it.copy(title = v.take(60)) } }, label = "제목", placeholder = "예: 용량 조정", keyboard = KeyboardType.Text, textSize = HlSize.t14)
        }
        Field("내용 · 선택") {
            HlTextField(draft.body, { v -> onEdit { it.copy(body = v.take(2000)) } }, label = "내용", placeholder = "의사가 한 말, 다음에 확인할 것", keyboard = KeyboardType.Text, textSize = HlSize.t14, singleLine = false, minLines = 4, minHeight = 96.dp)
        }
        Field("처방 · 선택") {
            HlTextField(draft.prescription, { v -> onEdit { it.copy(prescription = v.take(100)) } }, label = "처방", placeholder = "예: EV 5 mg / 7일", keyboard = KeyboardType.Text, textSize = HlSize.t14)
        }
        Field("링크 · 선택") {
            HlTextField(draft.link, { v -> onEdit { it.copy(link = v.take(300)) } }, label = "링크", placeholder = "https://", keyboard = KeyboardType.Uri, textSize = HlSize.t14, error = linkError != null)
            if (linkError != null) HlFieldError(linkError)
        }
    }
    if (picker) {
        DatePickDialog(seed = draft.date, zone = fmt.zone, onDismiss = { picker = false }, onPicked = { d: LocalDate -> onEdit { it.copy(date = d) }; picker = false })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClinicEditorSheet(draft: ClinicDraft, onEdit: ((ClinicDraft) -> ClinicDraft) -> Unit, onSave: () -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    val c = Hl.colors
    val linkError = draft.linkError
    FormSheet(
        title = if (draft.editingId != null) "병원 정보 수정" else "병원 추가",
        onDismiss = onDismiss,
        footer = {
            HlButton("저장", onSave, Modifier.fillMaxWidth(), enabled = draft.canSave && linkError == null)
            if (onDelete != null) HlButton("병원 삭제", onDelete, Modifier.fillMaxWidth(), kind = HlButtonKind.Text, minHeight = 48.dp, content = c.danger, size = HlSize.t14)
        },
    ) {
        Field("이름") { HlTextField(draft.name, { v -> onEdit { it.copy(name = v.take(60)) } }, label = "병원 이름", placeholder = "예: OO의원", keyboard = KeyboardType.Text, textSize = HlSize.t14) }
        Field("지역 · 선택") { HlTextField(draft.region, { v -> onEdit { it.copy(region = v.take(40)) } }, label = "지역", placeholder = "예: 서울 마포", keyboard = KeyboardType.Text, textSize = HlSize.t14) }
        Field("처방 근거") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PrescriptionBasis.entries.forEach { b -> HlChip(b.label, draft.basis == b, { onEdit { it.copy(basis = b) } }, padding = PaddingValues(horizontal = 14.dp)) }
            }
        }
        Field("비대면") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Telehealth.entries.forEach { t -> HlChip(t.label, draft.telehealth == t, { onEdit { it.copy(telehealth = t) } }, padding = PaddingValues(horizontal = 14.dp)) }
            }
        }
        Field("가격 메모 · 선택") { HlTextField(draft.priceNote, { v -> onEdit { it.copy(priceNote = v.take(100)) } }, label = "가격 메모", placeholder = "예: 초진 3만원", keyboard = KeyboardType.Text, textSize = HlSize.t14) }
        Field("메모 · 선택") { HlTextField(draft.memo, { v -> onEdit { it.copy(memo = v.take(1000)) } }, label = "메모", placeholder = "예약 방법, 준비물", keyboard = KeyboardType.Text, textSize = HlSize.t14, singleLine = false, minLines = 3, minHeight = 80.dp) }
        Field("출처 링크 · 선택") {
            HlTextField(draft.sourceUrl, { v -> onEdit { it.copy(sourceUrl = v.take(300)) } }, label = "출처 링크", placeholder = "https://", keyboard = KeyboardType.Uri, textSize = HlSize.t14, error = linkError != null)
            if (linkError != null) HlFieldError(linkError)
        }
    }
}

/** 다음 진료: a day and a time, which may be ahead; clearing it removes the reminder with it. */
@Composable
fun AppointmentSheet(currentMillis: Long?, fmt: Fmt, onSave: (Long) -> Unit, onClear: (() -> Unit)?, onDismiss: () -> Unit) {
    val c = Hl.colors
    val tomorrow = fmt.now.atZone(fmt.zone).toLocalDate().plusDays(1)
    var date by remember { mutableStateOf(currentMillis?.let { Instant.ofEpochMilli(it).atZone(fmt.zone).toLocalDate() } ?: tomorrow) }
    var minutes by remember { mutableStateOf(currentMillis?.let { Instant.ofEpochMilli(it).atZone(fmt.zone).let { z -> z.hour * 60 + z.minute } } ?: (10 * 60)) }
    var picker by remember { mutableStateOf(Pick.NONE) }
    FormSheet(
        title = "다음 진료 예약",
        onDismiss = onDismiss,
        footer = {
            HlButton("저장", { onSave(date.atTime(minutes / 60, minutes % 60).atZone(fmt.zone).toInstant().toEpochMilli()) }, Modifier.fillMaxWidth())
            if (onClear != null) HlButton("예약 지우기", onClear, Modifier.fillMaxWidth(), kind = HlButtonKind.Text, minHeight = 48.dp, content = c.danger, size = HlSize.t14)
        },
    ) {
        Field("날짜") { TileButton("${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일 (${date.dayOfWeek.shortLabel})", { picker = Pick.DATE }, Modifier.fillMaxWidth()) }
        Field("시각") { TileButton(fmt.minutes(minutes), { picker = Pick.TIME }, Modifier.fillMaxWidth(), icon = HlIcon.Clock) }
        HlText("진료 알림은 하루 전 저녁에 와요. 내 정보 › 알림에서 켜고 끌 수 있어요.", size = HlSize.t12, color = c.muted, lineHeight = 1.5f)
    }
    when (picker) {
        Pick.DATE -> DatePickDialog(seed = date, zone = fmt.zone, onDismiss = { picker = Pick.NONE }, onPicked = { d: LocalDate -> date = d; picker = Pick.NONE })
        Pick.TIME -> TimePickDialog(initialMinutes = minutes, is24Hour = fmt.clock24, onDismiss = { picker = Pick.NONE }, onPicked = { m -> minutes = m; picker = Pick.NONE })
        Pick.NONE -> Unit
    }
}
