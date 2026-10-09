package com.hormonelog.app.feature.security

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hormonelog.app.R
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.BackupUi
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlBanner
import com.hormonelog.app.ui.kit.HlButton
import com.hormonelog.app.ui.kit.HlNotice
import com.hormonelog.app.ui.kit.HlSectionLabel
import com.hormonelog.app.ui.kit.HlSettingRow
import com.hormonelog.app.ui.kit.HlSwitchRow
import com.hormonelog.app.ui.kit.HlTextAction
import com.hormonelog.app.ui.kit.HlTopBar
import com.hormonelog.app.ui.kit.HlDivider
import com.hormonelog.app.ui.kit.ScreenScroll
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText

class SecurityActions(
    val back: () -> Unit,
    val createBackup: () -> Unit,
    val writeBackup: (Uri) -> Unit,
    val cancelBackupSave: () -> Unit,
    val resetBackup: () -> Unit,
    val openRestore: (Uri, String) -> Unit,
    val writeCsv: (Uri, (Boolean) -> Unit) -> Unit,
    val disguiseLauncher: (Boolean) -> Unit,
    val hideInRecents: (Boolean) -> Unit,
    val neutralNotifications: (Boolean) -> Unit,
)

private fun displayName(context: Context, uri: Uri): String =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment.orEmpty()

@Composable
fun SecurityScreen(s: AppState, fmt: Fmt, actions: SecurityActions) {
    val c = Hl.colors
    val context = LocalContext.current
    val settings = s.settings

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) actions.writeBackup(uri) else actions.cancelBackupSave()
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) actions.openRestore(uri, displayName(context, uri))
    }
    val csvOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) actions.writeCsv(uri) { ok -> Toast.makeText(context, if (ok) "CSV로 저장했어요" else "저장하지 못했어요", Toast.LENGTH_SHORT).show() }
    }
    val csvIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) actions.openRestore(uri, displayName(context, uri))
    }
    // A built backup is waiting for the user to pick where it goes.
    val ready = s.backup as? BackupUi.ReadyToSave
    LaunchedEffect(ready) {
        if (ready != null) saveLauncher.launch(ready.fileName)
    }

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        HlTopBar("백업·복원·보안", actions.back)
        ScreenScroll(padding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 24.dp), gap = 14.dp, bottomInset = true) {
            HlSectionLabel("백업")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card)) {
                Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HlText("전체 백업 만들기", size = HlSize.t14, weight = FontWeight.Bold)
                    HlText("투약·검사 기록, 반복 일정, 병원 메모를 모두 담아요. 파일은 암호 없이 저장돼요.", size = HlSize.t13, color = c.muted, lineHeight = 1.5f)
                }
                Column(Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp)) {
                when (val b = s.backup) {
                    BackupUi.Idle, is BackupUi.ReadyToSave -> HlButton(
                        "백업 파일 만들기", actions.createBackup,
                        Modifier.fillMaxWidth(), minHeight = 52.dp, size = HlSize.t14,
                    )
                    BackupUi.Running -> Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HlText("만드는 중… 투약 ${s.doses.size}건 · 검사 ${s.labs.size}건", size = HlSize.t13, color = c.muted)
                        IndeterminateBar()
                    }
                    is BackupUi.Done -> Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.chip)).background(c.tealSoft).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HlText("백업을 만들었어요", size = HlSize.t14, weight = FontWeight.Bold, color = c.teal)
                        HlText("${b.fileName} · ${b.sizeKb} KB\n투약 ${b.doses}건 · 검사 ${b.labs}건 · 일정 ${b.regimens}개 · 메모 ${b.memos}건", size = HlSize.t13, lineHeight = 1.5f)
                        HlTextAction("다른 위치에 저장", { actions.resetBackup(); actions.createBackup() })
                    }
                    is BackupUi.Failed -> Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.chip)).background(c.dangerSoft).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        HlText("백업을 만들지 못했어요", size = HlSize.t14, weight = FontWeight.Bold, color = c.danger)
                        HlText("${b.reason}. 기존 기록은 그대로 있어요.", size = HlSize.t13, lineHeight = 1.5f)
                        HlTextAction("다시 시도", { actions.resetBackup(); actions.createBackup() }, color = c.danger)
                    }
                }
                }
            }

            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card)) {
                HlSettingRow("백업에서 복원", onClick = { restoreLauncher.launch(arrayOf("*/*")) })
                HlDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HlText("CSV 내보내기 · 불러오기", size = HlSize.t14, weight = FontWeight.SemiBold)
                    HlText("CSV는 일부 정보만 담겨요. 일정·병원 메모·보정 상태는 빠져요.", size = HlSize.t12, color = c.muted, lineHeight = 1.5f)
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        HlTextAction("내보내기", {
                            csvOut.launch("hormonelog_${fmt.now.epochSecond}.csv")
                        }, color = if (s.doses.isEmpty() && s.labs.isEmpty()) c.dim else c.teal)
                        HlTextAction("불러오기", { csvIn.launch(arrayOf("text/*", "application/octet-stream")) })
                    }
                }
            }

            HlSectionLabel("보안")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card)) {
                Column(Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HlText("위장 모드", size = HlSize.t14, weight = FontWeight.Bold)
                    HlText("다른 사람에게 보일 수 있는 곳에서 앱 정체를 숨겨요.", size = HlSize.t12, color = c.muted, lineHeight = 1.5f)
                }
                HlDivider()
                HlSwitchRow("런처 이름·아이콘 바꾸기", settings.disguiseLauncher, actions.disguiseLauncher, subtitle = "“메모” 이름과 중립 아이콘으로 보여요")
                HlDivider()
                HlSwitchRow("최근 앱 화면 가리기", settings.hideInRecents, actions.hideInRecents, subtitle = "앱 전환 화면에서 내용을 가려요")
                HlDivider()
                HlSwitchRow("알림 문구 중립화", settings.neutralNotifications, actions.neutralNotifications, subtitle = "알림과 위젯에서 약 이름·수치·“호르몬”을 빼요")
            }
            HlText("앱 이름은 알림 머리글과 휴대폰 설정의 앱 목록에 그대로 보여요.", size = HlSize.t12, color = c.muted, lineHeight = 1.5f)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HlText("알림 미리보기", size = HlSize.t12, weight = FontWeight.SemiBold, color = c.muted)
                NotificationPreview(settings.neutralNotifications || settings.disguiseLauncher)
            }
        }
    }
}

/** How a dose reminder would look with the current wording choice; the header is the app's real name, as the system shows it. */
@Composable
fun NotificationPreview(neutral: Boolean, modifier: Modifier = Modifier) {
    val c = Hl.colors
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.input).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HlText("${stringResource(R.string.app_name)} · 지금", size = HlSize.t12, color = c.muted)
        HlText(if (neutral) "오늘 기록할 항목이 있어요" else "오늘 주사일이에요 · 에스트라디올 발레레이트 5 mg", size = HlSize.t14, weight = FontWeight.SemiBold, lineHeight = 1.4f)
        HlText(if (neutral) "완료" else "투약 완료", size = HlSize.t13, weight = FontWeight.Bold, color = c.teal)
    }
}

@Composable
private fun IndeterminateBar() {
    val c = Hl.colors
    val t = rememberInfiniteTransition(label = "backup")
    val x by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart), label = "x")
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(c.input).clipToBounds()) {
        Box(
            Modifier.height(6.dp).fillMaxWidth(0.35f).layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                layout(constraints.maxWidth, p.height) { p.place(((constraints.maxWidth + p.width) * x - p.width).toInt(), 0) }
            }.clip(RoundedCornerShape(3.dp)).background(c.teal),
        )
    }
}

/** Notice used by the restore screen and others when a file cannot be read. */
@Composable
fun UnreadableNotice(text: String) {
    HlBanner(HlNotice.Danger, icon = HlIcon.Alert) { HlText(text, size = HlSize.t13, lineHeight = 1.5f) }
}

