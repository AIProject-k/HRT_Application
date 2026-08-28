package com.hormonelog.app.feature.clinics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.hormonelog.app.feature.common.PRESCRIPTION_BASES
import com.hormonelog.app.feature.common.TELEHEALTH_OPTIONS
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.feature.dashboard.ClinicDraft
import com.hormonelog.app.feature.dose.FieldBlock
import com.hormonelog.app.feature.dose.NoteInput
import com.hormonelog.app.feature.dose.SheetFooter
import com.hormonelog.app.feature.dose.SheetHeader
import com.hormonelog.app.ui.components.Disclaimer
import com.hormonelog.app.ui.components.HlCard
import com.hormonelog.app.ui.components.HlChip
import com.hormonelog.app.ui.theme.HlColor
import com.hormonelog.app.ui.theme.HlType
import com.hormonelog.core.domain.Clinic
import java.util.UUID

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClinicsScreen(
    clinics: List<Clinic>,
    draft: ClinicDraft?,
    onClose: () -> Unit,
    onNew: () -> Unit,
    onEdit: (UUID) -> Unit,
    onEditDraft: ((ClinicDraft) -> ClinicDraft) -> Unit,
    onCancelDraft: () -> Unit,
    onSave: () -> Unit,
    onDelete: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(HlColor.Background)) {
        if (draft == null) {
            SheetHeader("병원 메모", onClose)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Disclaimer("직접 적어 두는 메모예요. 병원 정보(가격·처방 방식·진료 여부)는 자주 바뀌니 방문 전 꼭 확인하세요.")

                Text(
                    "+ 병원 추가",
                    style = HlType.ButtonLabel.copy(fontSize = 14.sp),
                    color = HlColor.Teal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(HlColor.TealTintSoft)
                        .border(1.dp, HlColor.Teal, RoundedCornerShape(14.dp))
                        .clickable(onClick = onNew)
                        .padding(vertical = 14.dp),
                )

                if (clinics.isEmpty()) {
                    HlCard(borderColor = HlColor.Border06) {
                        Text("아직 추가한 병원이 없어요", style = HlType.CardTitle, color = HlColor.TextMuted)
                        Text("아카라이브 등에서 본 정보를 여기 옮겨 두면 돼요.", style = HlType.BodySm, color = HlColor.TextDim)
                    }
                } else {
                    clinics.forEach { c -> ClinicCard(c, onEdit = { onEdit(c.id) }, onDelete = { onDelete(c.id) }) }
                }
            }
        } else {
            SheetHeader(if (draft.editingId == null) "병원 추가" else "병원 수정", onCancelDraft)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FieldBlock("이름") {
                    NoteInput(draft.name, "예: OO의원") { onEditDraft { d -> d.copy(name = it) } }
                }
                FieldBlock("지역") {
                    NoteInput(draft.region, "예: 서울 강남 / 온라인") { onEditDraft { d -> d.copy(region = it) } }
                }
                FieldBlock("처방 근거") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        PRESCRIPTION_BASES.forEach { b ->
                            HlChip(b.label, draft.basis == b, { onEditDraft { d -> d.copy(basis = b) } })
                        }
                    }
                }
                FieldBlock("진료 방식") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        TELEHEALTH_OPTIONS.forEach { t ->
                            HlChip(t.label, draft.telehealth == t, { onEditDraft { d -> d.copy(telehealth = t) } })
                        }
                    }
                }
                FieldBlock("가격 메모 (선택)") {
                    NoteInput(draft.priceNote, "예: 초진 3만 · 처방 1.5만") { onEditDraft { d -> d.copy(priceNote = it) } }
                }
                FieldBlock("메모 (선택)") {
                    NoteInput(draft.memo, "예약 방법, 대기시간, 분위기 등") { onEditDraft { d -> d.copy(memo = it) } }
                }
                FieldBlock("출처 링크 (선택)") {
                    NoteInput(draft.sourceUrl, "아카라이브 글 주소 등") { onEditDraft { d -> d.copy(sourceUrl = it) } }
                }
            }
            SheetFooter(
                label = if (draft.canSave) "저장" else "이름을 입력해 주세요",
                background = if (draft.canSave) HlColor.Teal else HlColor.KeyAlt,
                foreground = if (draft.canSave) HlColor.OnTeal else HlColor.TextDim,
                enabled = draft.canSave,
                onClick = onSave,
            )
        }
    }
}

@Composable
private fun ClinicCard(clinic: Clinic, onEdit: () -> Unit, onDelete: () -> Unit) {
    HlCard(
        modifier = Modifier.clickable(onClick = onEdit),
        borderColor = HlColor.Border06,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(clinic.name, style = HlType.CardTitleLg, color = HlColor.TextPrimary)
            Text(
                "삭제",
                style = HlType.Caption,
                color = HlColor.TextDim,
                modifier = Modifier.clickable(onClick = onDelete).padding(start = 8.dp),
            )
        }
        val tags = buildList {
            if (clinic.region.isNotBlank()) add(clinic.region)
            add(clinic.prescriptionBasis.label)
            add(clinic.telehealth.label)
        }
        Text(tags.joinToString("  ·  "), style = HlType.BodySm, color = HlColor.TextMuted)
        if (clinic.priceNote.isNotBlank()) {
            Text("💰 ${clinic.priceNote}", style = HlType.Caption, color = HlColor.TextDim)
        }
        if (clinic.memo.isNotBlank()) {
            Text(clinic.memo, style = HlType.BodySm, color = HlColor.TextSecondary)
        }
        if (clinic.sourceUrl.isNotBlank()) {
            Text(clinic.sourceUrl, style = HlType.Caption, color = HlColor.Blue)
        }
    }
}
