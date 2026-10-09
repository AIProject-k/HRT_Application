package com.hormonelog.app.feature.report

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hormonelog.app.platform.ReportFiles
import com.hormonelog.app.state.AppState
import com.hormonelog.app.state.ReportKind
import com.hormonelog.app.state.ReportPart
import com.hormonelog.app.state.ReportPeriod
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.kit.HlButton
import com.hormonelog.app.ui.kit.HlButtonKind
import com.hormonelog.app.ui.kit.HlChip
import com.hormonelog.app.ui.kit.HlDisclaimer
import com.hormonelog.app.ui.kit.HlDivider
import com.hormonelog.app.ui.kit.HlTopBar
import com.hormonelog.app.ui.kit.ScreenScroll
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText

class ReportActions(
    val back: () -> Unit,
    val period: (ReportPeriod) -> Unit,
    val toggle: (ReportPart) -> Unit,
    val create: (ReportKind) -> Unit,
    val shared: () -> Unit,
)

private val PERIODS = listOf(ReportPeriod.MONTH to "1개월", ReportPeriod.QUARTER to "3개월", ReportPeriod.HALF to "6개월", ReportPeriod.ALL to "전체")

private val PARTS = listOf(
    ReportPart.CHART to "예상 곡선 + 실측",
    ReportPart.LABS to "검사값 표",
    ReportPart.SCHEDULE to "현재 일정",
    ReportPart.ADHERENCE to "투약 순응도",
    ReportPart.MODEL to "모델·근거 버전",
    ReportPart.MEMOS to "병원 메모",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(s: AppState, actions: ReportActions) {
    val c = Hl.colors
    val context = LocalContext.current
    val ui = s.report

    // A finished file goes to the system share sheet, where the user picks the one app that gets it.
    val ready = ui.ready
    LaunchedEffect(ready) {
        if (ready != null) {
            val uri = ReportFiles.uriFor(context, ready)
            val send = Intent(Intent.ACTION_SEND).setType(ready.mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri(null, uri)
            runCatching { context.startActivity(Intent.createChooser(send, "리포트 공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            actions.shared()
        }
    }

    Column(Modifier.fillMaxWidth().fillMaxHeight()) {
        HlTopBar("리포트 만들기", actions.back)
        ScreenScroll(padding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 24.dp), gap = 14.dp, bottomInset = true) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HlText("기간", size = HlSize.t13, weight = FontWeight.SemiBold, color = c.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PERIODS.forEach { (p, label) -> HlChip(label, ui.period == p, { actions.period(p) }, padding = PaddingValues(horizontal = 14.dp)) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HlText("담을 내용", size = HlSize.t13, weight = FontWeight.SemiBold, color = c.muted)
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.card)).background(c.card)) {
                    PARTS.forEachIndexed { i, (part, label) ->
                        if (i > 0) HlDivider()
                        val on = part in ui.include
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(value = on, role = Role.Checkbox, onValueChange = { actions.toggle(part) }).padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (on) c.teal else androidx.compose.ui.graphics.Color.Transparent).border(2.dp, if (on) c.teal else c.line, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center,
                            ) { if (on) HlIcon(HlIcon.Check, size = 14.dp, tint = c.onTeal, strokeWidth = 3f) }
                            HlText(label, size = HlSize.t14, weight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(HlRadius.chip)).background(c.input).padding(horizontal = 14.dp, vertical = 12.dp)) {
                HlText("이름·앱 이름은 들어가지 않아요. 리포트는 기기 안에서 만들어지고, 공유할 곳은 직접 골라요.", size = HlSize.t13, color = c.muted, lineHeight = 1.5f)
            }
            HlButton(if (ui.busy) "만드는 중…" else "A4 PDF 만들기", { actions.create(ReportKind.PDF) }, Modifier.fillMaxWidth(), enabled = !ui.busy)
            HlButton("세로 이미지로 공유", { actions.create(ReportKind.IMAGE) }, Modifier.fillMaxWidth(), kind = HlButtonKind.Secondary, enabled = !ui.busy, size = HlSize.t14)
            if (!s.hasRecords) {
                HlText("기록이 아직 없어서 빈 리포트가 만들어져요. 투약이나 검사를 기록한 뒤에 만들면 더 쓸모 있어요.", size = HlSize.t12, color = c.muted, lineHeight = 1.5f)
            }
            HlDisclaimer()
        }
    }
}
