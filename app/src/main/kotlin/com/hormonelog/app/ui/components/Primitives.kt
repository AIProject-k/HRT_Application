package com.hormonelog.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.hormonelog.app.ui.theme.HlColor
import com.hormonelog.app.ui.theme.HlType

/** Mirrors the design's `chip()` helper: teal fill/border/text when selected. */
@Composable
fun HlChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(11.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 13.dp, vertical = 10.dp),
) {
    Text(
        text = label,
        style = HlType.Chip,
        color = if (selected) HlColor.Teal else HlColor.TextSecondary,
        modifier = modifier
            .clip(shape)
            .background(if (selected) HlColor.TealTint else HlColor.ChipIdle)
            .border(
                BorderStroke(1.dp, if (selected) HlColor.Teal else HlColor.Border08),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(contentPadding),
    )
}

/** Pill-shaped filter chip (전체 / 투약 / 검사). */
@Composable
fun HlPillChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    HlChip(
        label = label,
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp),
    )

/** Bordered surface card used throughout the design (radius 14–20, 1px hairline). */
@Composable
fun HlCard(
    modifier: Modifier = Modifier,
    background: Color = HlColor.Card,
    borderColor: Color = HlColor.Border06,
    corner: Int = 18,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(10.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(BorderStroke(1.dp, borderColor), shape)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
fun SectionHeader(
    title: String,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = HlType.SectionHeader, color = HlColor.TextPrimary)
        if (trailing != null) {
            Text(
                trailing,
                style = HlType.Label,
                color = HlColor.TextMuted,
                modifier = if (onTrailingClick != null) Modifier.clickable(onClick = onTrailingClick) else Modifier,
            )
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(HlColor.InputSurface)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = HlType.StatLabel, color = HlColor.TextDim)
        Text(value, style = HlType.StatValue, color = HlColor.TextBright)
    }
}

@Composable
fun Disclaimer(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = HlType.Disclaimer,
        color = HlColor.TextDisclaimer,
        textAlign = TextAlign.Start,
        modifier = modifier.padding(horizontal = 2.dp),
    )
}
