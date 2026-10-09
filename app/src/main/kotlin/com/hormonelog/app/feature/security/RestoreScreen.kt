package com.hormonelog.app.feature.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.common.text
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.RestoreIssue
import com.hormonelog.app.state.RestoreKind
import com.hormonelog.app.state.RestorePreview
import com.hormonelog.app.ui.kit.HlButton
import com.hormonelog.app.ui.kit.HlButtonKind
import com.hormonelog.app.ui.kit.HlDivider
import com.hormonelog.app.ui.kit.HlTopBar
import com.hormonelog.app.ui.kit.ScreenScroll
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import java.time.Instant

class RestoreActions(
    val back: () -> Unit,
    val restoreNow: () -> Unit,
)

/** What a backup or CSV file holds, shown before anything is changed. */
@Composable
fun RestoreScreen(s: AppState, fmt: Fmt, actions: RestoreActions) {
    val c = Hl.colors
    Column(Modifier.fillMaxWidth()) {
        HlTopBar("복원 미리보기", actions.back)
        ScreenScroll(padding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 24.dp), gap = 14.dp, bottomInset = true) {
            when (s.restoreIssue) {
                RestoreIssue.Unreadable -> {
                    HlText("이 파일을 읽을 수 없어요", size = HlSize.t16, weight = FontWeight.Bold, color = c.danger)
                    HlText("호르몬로그 백업 파일(.hlb)이나 CSV 파일인지 확인해 주세요. 기존 기록은 그대로 있어요.", size = HlSize.t13, color = c.muted, lineHeight = 1.5f)
                    HlButton("돌아가기", actions.back, Modifier.fillMaxWidth(), kind = HlButtonKind.Secondary)
                }
                null -> s.restore?.let { Preview(it, s, fmt, actions) }
            }
        }
    }
}

@Composable
private fun Preview(p: RestorePreview, s: AppState, fmt: Fmt, actions: RestoreActions) {
    val c = Hl.colors
    var skipOpen by remember { mutableStateOf(false) }
    val snap = p.snapshot
    val backup = p.kind == RestoreKind.BACKUP

    HlText(
        buildString {
            append(p.fileName)
            p.createdAtMillis?.takeIf { it > 0 }?.let { append(" · ${fmt.dateDay(Instant.ofEpochMilli(it))} 만듦") }
        },
        size = HlSize.t13, color = c.muted,
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card)) {
        CountRow("투약 기록", "${snap.doses.size}건")
        HlDivider()
        CountRow("검사 기록", "${snap.labs.size}건")
        if (backup) {
            HlDivider()
            CountRow("반복 일정", "${snap.regimens.size}개")
            HlDivider()
            CountRow("병원 메모", "${snap.memos.size + snap.clinics.size}건")
            if (snap.journal.isNotEmpty()) {
                HlDivider()
                CountRow("다른 기록", "${snap.journal.size}건")
            }
            if (snap.carriedCount > 0) {
                // A record this build cannot read (or one with a date or number no real log holds) is not loaded, only kept.
                HlDivider()
                CountRow("읽을 수 없어 불러오지 않는 기록", "${snap.carriedCount}건")
            }
        } else if (p.duplicates > 0) {
            HlDivider()
            CountRow("이미 있는 기록 (건너뜀)", "${p.duplicates}건")
        }
        if (p.skipped.isNotEmpty()) {
            HlDivider()
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { skipOpen = !skipOpen }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HlText("건너뛸 행 ${p.skipped.size}건", size = HlSize.t14, weight = FontWeight.SemiBold, color = c.orange)
                HlText(if (skipOpen) "접기" else "이유 보기", size = HlSize.t14, weight = FontWeight.Bold, color = c.orange)
            }
            if (skipOpen) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.skipped.groupBy { it.reason }.forEach { (reason, rows) ->
                        val lines = rows.map { it.line }
                        val shown = lines.take(6).joinToString(", ") + if (lines.size > 6) " 외 ${lines.size - 6}행" else ""
                        HlText("· ${reason.text} ${rows.size}건 (${shown}번째 행)", size = HlSize.t13, lineHeight = 1.5f)
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.button)).background(c.orangeSoft).padding(horizontal = 14.dp, vertical = 12.dp)) {
        HlText(
            if (backup) "지금 기기에 있는 기록 ${s.recordCount}건은 백업 내용으로 바뀌어요. 복원하기 전에 현재 상태를 자동으로 따로 보관하고, 복원한 뒤 5초 안에는 실행 취소할 수 있어요."
            else "CSV는 지금 있는 기록에 더해져요. 이미 있는 기록은 건너뛰어요. 일정·병원 메모는 CSV에 없어서 바뀌지 않아요.",
            size = HlSize.t13, lineHeight = 1.55f,
        )
    }
    HlButton(if (backup) "복원하기" else "불러오기", actions.restoreNow, Modifier.fillMaxWidth())
    HlButton("취소", actions.back, Modifier.fillMaxWidth(), kind = HlButtonKind.Secondary)
}

@Composable
private fun CountRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        HlText(label, size = HlSize.t14)
        HlText(value, size = HlSize.t14, weight = FontWeight.Bold, tabular = true)
    }
}
