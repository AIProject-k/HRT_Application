package com.hormonelog.app.feature.me

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.remember
import com.hormonelog.app.feature.common.label
import com.hormonelog.app.feature.dashboard.CalibrationStatus
import com.hormonelog.app.feature.dashboard.DashboardState
import com.hormonelog.app.feature.flow.chartWindow
import com.hormonelog.app.feature.flow.computeCurves
import com.hormonelog.app.ui.components.ConfirmDialog
import com.hormonelog.app.ui.components.Disclaimer
import com.hormonelog.app.ui.components.HlCard
import com.hormonelog.app.ui.theme.HlColor
import com.hormonelog.app.ui.theme.HlType
import com.hormonelog.core.domain.Regimen
import java.util.UUID

private data class MeRow(val icon: String, val title: String, val sub: String, val value: String)

private val ME_ROWS = listOf(
    MeRow("🔒", "기기 안에만 저장", "서버 업로드 없음 · 계정 불필요", "켜짐"),
    MeRow("🔑", "앱 잠금", "지문 또는 화면 잠금으로 보호", "켜짐"),
    MeRow("⬇️", "데이터 내보내기", "CSV로 저장해 진료 때 보여주기", "›"),
    MeRow("🔤", "언어", "한국어 · English (준비 중)", "한국어"),
    MeRow("🌙", "화면", "다크 모드 · 큰 글자", "다크"),
)

private sealed interface MePending {
    data class RegimenDelete(val id: UUID, val label: String) : MePending
    data object ClearDoses : MePending
    data object ClearLabs : MePending
    data object ClearAll : MePending
}

private fun Regimen.summary(): String {
    val amt = if (amountEntered % 1.0 == 0.0) amountEntered.toInt().toString() else amountEntered.toString()
    val every = if (everyDays == 1) "매일" else "${everyDays}일마다"
    return "${drug.label} · ${route.label} ${amt}${enteredUnit.label} · $every"
}

@Composable
fun MeScreen(
    state: DashboardState,
    now: java.time.Instant,
    onLoadSample: () -> Unit,
    onOpenClinics: () -> Unit,
    onImportCsv: (String) -> Unit,
    exportCsvText: () -> String,
    onDeleteRegimen: (UUID) -> Unit = {},
    onClearDoses: () -> Unit = {},
    onClearLabs: () -> Unit = {},
    onClearAll: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<MePending?>(null) }

    when (val p = pending) {
        is MePending.RegimenDelete -> ConfirmDialog(
            title = "반복 일정 삭제",
            body = "${p.label}\n\n반복 일정만 지워요. 이미 타임라인에 쌓인 투약 기록은 그대로 남아요.",
            onConfirm = { onDeleteRegimen(p.id) },
            onDismiss = { pending = null },
        )
        MePending.ClearDoses -> ConfirmDialog(
            title = "투약 기록 전체 삭제",
            body = "투약 기록 ${state.doses.size}건을 모두 삭제할까요? 되돌릴 수 없어요.",
            onConfirm = onClearDoses,
            onDismiss = { pending = null },
        )
        MePending.ClearLabs -> ConfirmDialog(
            title = "검사 결과 전체 삭제",
            body = "검사 결과 ${state.labs.size}건을 모두 삭제할까요? 되돌릴 수 없어요.",
            onConfirm = onClearLabs,
            onDismiss = { pending = null },
        )
        MePending.ClearAll -> ConfirmDialog(
            title = "전체 초기화",
            body = "투약 ${state.doses.size}건 · 검사 ${state.labs.size}건 · 반복 일정 ${state.regimens.size}개를 모두 삭제할까요? 병원 메모는 남아요. 되돌릴 수 없어요.",
            onConfirm = onClearAll,
            onDismiss = { pending = null },
        )
        null -> Unit
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text.isNullOrBlank()) {
                Toast.makeText(context, "파일을 읽지 못했어요", Toast.LENGTH_SHORT).show()
            } else {
                onImportCsv(text)
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(exportCsvText()) }
            }.onSuccess { Toast.makeText(context, "CSV로 저장했어요", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "저장 실패", Toast.LENGTH_SHORT).show() }
        }
    }
    val window = remember(now) { chartWindow(30, now) }
    val curves = remember(state.doses, state.regimens, state.labs, now) {
        computeCurves(state.doses, state.regimens, state.labs, now, window.first, window.second)
    }
    val model = CalibrationStatus.of(curves.cal.includedLabIds.size, curves.cal.exposureScale, curves.canEstimate)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("내 정보", style = HlType.ScreenTitle, color = HlColor.TextPrimary, modifier = Modifier.padding(top = 6.dp))

        HlCard(borderColor = HlColor.Border06, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(HlColor.OrangeTintSoft),
                    contentAlignment = Alignment.Center,
                ) { Text("◐", fontSize = 16.sp, color = HlColor.Orange) }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(model.title, style = HlType.CardTitleLg, color = HlColor.Orange)
                    Text("모델 상태", style = HlType.BodySm, color = HlColor.TextMuted)
                }
            }
            Text(model.detail, style = HlType.Body, color = HlColor.TextSecondary)

            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("개인화 진행도", style = HlType.Label, color = HlColor.TextMuted)
                    Text(model.progressLabel, style = HlType.Label, color = HlColor.Orange)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(HlColor.KeyAlt),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(model.progressPct / 100f)
                            .height(7.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(HlColor.Orange),
                    )
                }
                Text(model.nextStep, style = HlType.Caption, color = HlColor.TextDim)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 2.dp)) {
                model.steps.forEach { step ->
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(17.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (step.done || step.active) HlColor.OrangeTintSoft else HlColor.KeyAlt),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (step.done) "✓" else (step.index + 1).toString(),
                                style = HlType.Badge.copy(fontSize = 9.5.sp),
                                color = if (step.done || step.active) HlColor.Orange else HlColor.TextDim,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                step.title,
                                style = HlType.LabelStrong.copy(fontSize = 12.5.sp),
                                color = if (step.done || step.active) HlColor.TextBright else HlColor.TextDim,
                            )
                            Text(step.subtitle, style = HlType.Caption, color = HlColor.TextDim)
                        }
                    }
                }
            }
        }

        // settings rows (display-only in this build)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(HlColor.Card)
                .border(1.dp, HlColor.Border06, RoundedCornerShape(18.dp)),
        ) {
            ME_ROWS.forEachIndexed { i, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Text(row.icon, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(row.title, style = HlType.CardTitle.copy(fontSize = 13.sp), color = HlColor.TextPrimary)
                        Text(row.sub, style = HlType.Caption, color = HlColor.TextDim)
                    }
                    Text(row.value, style = HlType.Label, color = HlColor.TextDim)
                }
                if (i < ME_ROWS.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(HlColor.Border06))
                }
            }
        }

        // CSV import / export.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HlColor.Card)
                .border(1.dp, HlColor.Border06, RoundedCornerShape(14.dp)),
        ) {
            Text(
                "CSV 불러오기",
                style = HlType.CardTitle.copy(fontSize = 13.sp),
                color = HlColor.Teal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable { importLauncher.launch(arrayOf("text/*", "text/csv", "text/comma-separated-values", "application/octet-stream")) }
                    .padding(vertical = 16.dp),
            )
            Box(Modifier.width(1.dp).height(48.dp).background(HlColor.Border06))
            Text(
                "CSV 내보내기",
                style = HlType.CardTitle.copy(fontSize = 13.sp),
                color = if (state.doses.isEmpty() && state.labs.isEmpty()) HlColor.TextDim else HlColor.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = state.doses.isNotEmpty() || state.labs.isNotEmpty()) {
                        exportLauncher.launch("hormonelog_${now.epochSecond}.csv")
                    }
                    .padding(vertical = 16.dp),
            )
        }
        Text(
            "열: type,datetime,drug,route,amount,unit,e2,tt,e2_unit,assay,note  ·  type = dose/lab",
            style = HlType.Caption,
            color = HlColor.TextDim,
            modifier = Modifier.padding(horizontal = 2.dp),
        )

        // 반복 일정 — list + per-item delete (records stay).
        if (state.regimens.isNotEmpty()) {
            Text("반복 일정", style = HlType.SectionHeader, color = HlColor.TextPrimary, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HlColor.Card)
                    .border(1.dp, HlColor.Border06, RoundedCornerShape(14.dp)),
            ) {
                state.regimens.forEachIndexed { i, r ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(r.summary(), style = HlType.BodySm, color = HlColor.TextSecondary, modifier = Modifier.weight(1f))
                        Text(
                            "삭제",
                            style = HlType.Caption,
                            color = HlColor.TextDim,
                            modifier = Modifier.clickable { pending = MePending.RegimenDelete(r.id, r.summary()) },
                        )
                    }
                    if (i < state.regimens.lastIndex) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(HlColor.Border06))
                    }
                }
            }
        }

        // 기록 삭제 — bulk clear with confirmation.
        Text("기록 삭제", style = HlType.SectionHeader, color = HlColor.TextPrimary, modifier = Modifier.padding(top = 4.dp, start = 2.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HlColor.Card)
                .border(1.dp, HlColor.Border06, RoundedCornerShape(14.dp)),
        ) {
            ClearRow("투약 기록 전체 삭제", state.doses.size, enabled = state.doses.isNotEmpty()) { pending = MePending.ClearDoses }
            Box(Modifier.fillMaxWidth().height(1.dp).background(HlColor.Border06))
            ClearRow("검사 결과 전체 삭제", state.labs.size, enabled = state.labs.isNotEmpty()) { pending = MePending.ClearLabs }
            Box(Modifier.fillMaxWidth().height(1.dp).background(HlColor.Border06))
            ClearRow("전체 초기화 (병원 메모 제외)", state.doses.size + state.labs.size + state.regimens.size, enabled = state.doses.isNotEmpty() || state.labs.isNotEmpty() || state.regimens.isNotEmpty()) { pending = MePending.ClearAll }
        }

        // 병원 메모 — user-authored clinic notes.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HlColor.Card)
                .border(1.dp, HlColor.Border06, RoundedCornerShape(14.dp))
                .clickable(onClick = onOpenClinics)
                .padding(horizontal = 15.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Text("🏥", fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("병원 메모", style = HlType.CardTitle.copy(fontSize = 13.sp), color = HlColor.TextPrimary)
                Text("호르몬 처방 병원 직접 기록", style = HlType.Caption, color = HlColor.TextDim)
            }
            Text(
                if (state.clinics.isEmpty()) "›" else "${state.clinics.size}곳 ›",
                style = HlType.Label,
                color = HlColor.TextDim,
            )
        }

        // Example data — populate the app with a sample regimen for exploration.
        Text(
            "예시 데이터 넣기",
            style = HlType.ButtonLabel.copy(fontSize = 14.sp),
            color = HlColor.Teal,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HlColor.TealTintSoft)
                .border(1.dp, HlColor.Teal, RoundedCornerShape(14.dp))
                .clickable(onClick = onLoadSample)
                .padding(vertical = 14.dp),
        )
        Text(
            "에스트라디올 발레레이트 IM 10mg / 2주 + 사이프로테론 경구 25mg / 매일, 2개월 전부터. 문헌 인구집단 곡선 확인용.",
            style = HlType.Caption,
            color = HlColor.TextDim,
            modifier = Modifier.padding(horizontal = 2.dp),
        )

        Disclaimer("호르몬로그는 기록 도구입니다. 진단·처방·용량 변경을 제안하지 않습니다.")
    }
}

@Composable
private fun ClearRow(label: String, count: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            label,
            style = HlType.CardTitle.copy(fontSize = 13.sp),
            color = if (enabled) HlColor.Danger else HlColor.TextDim,
            modifier = Modifier.weight(1f),
        )
        Text("${count}건", style = HlType.Label, color = HlColor.TextDim)
    }
}
