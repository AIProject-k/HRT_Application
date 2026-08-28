package com.hormonelog.app.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hormonelog.app.feature.dashboard.DashboardTab
import com.hormonelog.app.ui.theme.HlColor
import com.hormonelog.app.ui.theme.HlType

private data class TabDef(val tab: DashboardTab, val icon: String, val label: String)

private val TAB_DEFS = listOf(
    TabDef(DashboardTab.HOME, "🏠", "홈"),
    TabDef(DashboardTab.TIMELINE, "📋", "타임라인"),
    TabDef(DashboardTab.FLOW, "📈", "예상 흐름"),
    TabDef(DashboardTab.ME, "👤", "내 정보"),
)

@Composable
fun BottomTabs(current: DashboardTab, onSelect: (DashboardTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HlColor.NavBackground)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
    ) {
        TAB_DEFS.forEach { def ->
            val selected = def.tab == current
            Column(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 52.dp)
                    .clickable { onSelect(def.tab) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = def.icon,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) HlColor.TealTint else HlColor.Background.copy(alpha = 0f))
                        .padding(horizontal = 16.dp, vertical = 3.dp),
                )
                Text(
                    text = def.label,
                    style = HlType.TabLabel,
                    color = if (selected) HlColor.Teal else HlColor.TextDim,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
