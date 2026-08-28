package com.hormonelog.app.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hormonelog.app.feature.dashboard.TimelineEntry
import com.hormonelog.app.feature.dashboard.TimelineKind
import com.hormonelog.app.ui.theme.HlColor
import com.hormonelog.app.ui.theme.HlType

private fun icon(kind: TimelineKind) = when (kind) {
    TimelineKind.LAB -> "🧪"
    TimelineKind.MISSED -> "✕"
    TimelineKind.DOSE -> "💉"
}

private fun tint(kind: TimelineKind) = when (kind) {
    TimelineKind.LAB -> HlColor.YellowTintSoft
    TimelineKind.MISSED -> HlColor.NeutralTint
    TimelineKind.DOSE -> HlColor.TealTintSoft
}

/** Compact record row used in 홈 · 최근 기록. */
@Composable
fun RecentRow(entry: TimelineEntry, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HlColor.Card)
            .border(1.dp, HlColor.Border06, RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(tint(entry.kind)),
            contentAlignment = Alignment.Center,
        ) { Text(icon(entry.kind), fontSize = 13.sp) }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.title, style = HlType.CardTitle, color = HlColor.TextPrimary)
            Text(entry.subtitle, style = HlType.Caption, color = HlColor.TextDim)
        }
        Text(entry.agoText, style = HlType.Caption, color = HlColor.TextDim)
    }
}

/** Timeline row with the left dot/line rail. Pass [onDelete] to show a 삭제 affordance. */
@Composable
fun TimelineItemRow(entry: TimelineEntry, modifier: Modifier = Modifier, onDelete: (() -> Unit)? = null) {
    Row(
        modifier = modifier.height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Column(
            modifier = Modifier.width(26.dp).padding(top = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val lab = entry.kind == TimelineKind.LAB
            val missed = entry.kind == TimelineKind.MISSED
            val dotShape = if (lab) RoundedCornerShape(2.dp) else CircleShape
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(dotShape)
                    .background(
                        when {
                            lab -> HlColor.Yellow
                            missed -> Color.Transparent
                            else -> HlColor.Teal
                        },
                    )
                    .border(
                        2.dp,
                        when {
                            lab -> HlColor.Yellow
                            missed -> HlColor.TextFaint
                            else -> HlColor.Teal
                        },
                        dotShape,
                    ),
            )
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .background(HlColor.Border06),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(HlColor.Card)
                .border(
                    1.dp,
                    if (entry.kind == TimelineKind.LAB) HlColor.YellowTint else HlColor.Border06,
                    RoundedCornerShape(14.dp),
                )
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.title,
                    style = HlType.CardTitle.copy(fontSize = 13.sp),
                    color = if (entry.kind == TimelineKind.MISSED) HlColor.TextMuted else HlColor.TextPrimary,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(entry.timeText, style = HlType.Caption, color = HlColor.TextDim)
                    if (onDelete != null) {
                        Text(
                            "삭제",
                            style = HlType.Caption,
                            color = HlColor.TextDim,
                            modifier = Modifier.clickable(onClick = onDelete),
                        )
                    }
                }
            }
            Text(entry.subtitle, style = HlType.BodySm, color = HlColor.TextMuted)
            if (entry.kind == TimelineKind.LAB) {
                Text(
                    "실제 검사값",
                    style = HlType.Badge,
                    color = HlColor.Yellow,
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(HlColor.YellowTintSoft)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
