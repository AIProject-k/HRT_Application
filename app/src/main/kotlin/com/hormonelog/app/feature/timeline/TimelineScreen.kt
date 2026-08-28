package com.hormonelog.app.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hormonelog.app.feature.common.TimelineItemRow
import com.hormonelog.app.feature.dashboard.DashboardState
import com.hormonelog.app.feature.dashboard.TimelineEntry
import com.hormonelog.app.feature.dashboard.TimelineFilter
import com.hormonelog.app.feature.dashboard.TimelineKind
import com.hormonelog.app.feature.dashboard.timelineEntries
import com.hormonelog.app.feature.dashboard.timelineGroups
import com.hormonelog.app.ui.components.ConfirmDialog
import com.hormonelog.app.ui.components.HlCard
import com.hormonelog.app.ui.components.HlPillChip
import com.hormonelog.app.ui.theme.HlColor
import com.hormonelog.app.ui.theme.HlType
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

private val FILTERS = listOf(
    TimelineFilter.ALL to "전체",
    TimelineFilter.DOSE to "투약",
    TimelineFilter.LAB to "검사",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimelineScreen(
    state: DashboardState,
    now: Instant,
    zone: ZoneId,
    onFilter: (TimelineFilter) -> Unit,
    onDeleteDose: (UUID) -> Unit = {},
    onDeleteLab: (UUID) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val entries = timelineEntries(state, now, zone, withMethod = true)
    val groups = timelineGroups(entries, state.timelineFilter)
    var pendingDelete by remember { mutableStateOf<TimelineEntry?>(null) }

    pendingDelete?.let { target ->
        val isLab = target.kind == TimelineKind.LAB
        ConfirmDialog(
            title = if (isLab) "검사 결과 삭제" else "투약 기록 삭제",
            body = "${target.title} · ${target.subtitle}\n\n이 기록을 삭제할까요? 예상 흐름 곡선도 다시 계산돼요.",
            onConfirm = { if (isLab) onDeleteLab(UUID.fromString(target.id)) else onDeleteDose(UUID.fromString(target.id)) },
            onDismiss = { pendingDelete = null },
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("타임라인", style = HlType.ScreenTitle, color = HlColor.TextPrimary, modifier = Modifier.padding(top = 6.dp))

        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            FILTERS.forEach { (value, label) ->
                HlPillChip(label = label, selected = state.timelineFilter == value, onClick = { onFilter(value) })
            }
        }

        if (groups.isEmpty()) {
            HlCard(borderColor = HlColor.Border06) {
                Text("아직 기록이 없어요", style = HlType.CardTitle, color = HlColor.TextMuted)
                Text("홈에서 투약이나 검사 결과를 추가해 보세요", style = HlType.BodySm, color = HlColor.TextDim)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                groups.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            group.dateLabel,
                            style = HlType.Label,
                            color = HlColor.TextDim,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                        group.items.forEach { item ->
                            TimelineItemRow(item, onDelete = { pendingDelete = item })
                        }
                    }
                }
            }
        }
    }
}
