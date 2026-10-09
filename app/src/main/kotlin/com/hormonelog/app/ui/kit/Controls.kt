package com.hormonelog.app.ui.kit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText

/** What a button is for: the one main action, a quieter alternative, or a plain text action. */
enum class HlButtonKind { Primary, Secondary, Danger, Text }

/**
 * A button as the design draws it: 56dp for the main action of a screen, 52 or 48 below it.
 * A [Primary] button that cannot be used yet is drawn flat (input fill, dim text) rather
 * than faded, so the reason it is idle stays readable.
 */
@Composable
fun HlButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: HlButtonKind = HlButtonKind.Primary,
    enabled: Boolean = true,
    minHeight: Dp = if (kind == HlButtonKind.Primary) 56.dp else 52.dp,
    size: TextUnit = if (kind == HlButtonKind.Primary) HlSize.t16 else HlSize.t14,
    container: Color? = null,
    content: Color? = null,
    leading: HlIcon? = null,
) {
    val c = Hl.colors
    val (bg, fg, border) = when (kind) {
        HlButtonKind.Primary -> if (enabled) Triple(container ?: c.teal, content ?: c.onTeal, null) else Triple(c.input, c.dim, null)
        HlButtonKind.Secondary -> Triple(Color.Transparent, content ?: c.text, c.line)
        HlButtonKind.Danger -> Triple(Color.Transparent, c.danger, c.danger)
        HlButtonKind.Text -> Triple(Color.Transparent, content ?: c.teal, null)
    }
    val weight = if (kind == HlButtonKind.Secondary) FontWeight.SemiBold else FontWeight.Bold
    val shape = RoundedCornerShape(HlRadius.button)
    Row(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(bg)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) HlIcon(leading, size = 18.dp, tint = fg, strokeWidth = 2.2f)
        HlText(label, size = size, weight = weight, color = fg, align = TextAlign.Center)
    }
}

/** Underlined text action ("백업하기", "다시 시도"): 44dp tall so it can actually be hit. */
@Composable
fun HlTextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Hl.colors.teal, size: TextUnit = HlSize.t13) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        HlText(label, size = size, weight = FontWeight.Bold, color = color, decoration = TextDecoration.Underline)
    }
}

/**
 * A choice among several: a selected chip is teal in fill, border *and* text. [multi]
 * chips toggle independently and are announced as checkboxes; the rest behave as radios.
 */
@Composable
fun HlChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 48.dp,
    size: TextUnit = HlSize.t14,
    shape: Shape = RoundedCornerShape(HlRadius.chip),
    padding: PaddingValues = PaddingValues(horizontal = 16.dp),
    multi: Boolean = false,
    accent: Color = Hl.colors.teal,
    accentSoft: Color = Hl.colors.tealSoft,
    sub: String? = null,
) {
    val c = Hl.colors
    val role = if (multi) Role.Checkbox else Role.RadioButton
    Column(
        modifier = modifier
            .heightIn(min = minHeight)
            .clip(shape)
            .background(if (selected) accentSoft else Color.Transparent)
            .border(1.dp, if (selected) accent else c.line, shape)
            .then(
                if (multi) Modifier.toggleable(value = selected, role = role, onValueChange = { onClick() })
                else Modifier.selectable(selected = selected, role = role, onClick = onClick),
            )
            .padding(padding)
            .padding(vertical = if (sub != null) 4.dp else 0.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (sub != null) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        HlText(label, size = size, weight = FontWeight.SemiBold, color = if (selected) accent else c.text)
        if (sub != null) HlText(sub, size = HlSize.t12, color = c.muted)
    }
}

/** One option of an [HlSegmented]. [container]/[content] override the selected colours (blue for 반복 일정). */
data class SegItem<T>(val value: T, val label: String, val container: Color? = null, val content: Color? = null)

/** 2–4 mutually exclusive options in one track; the selected one is raised. */
@Composable
fun <T> HlSegmented(
    items: List<SegItem<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 44.dp,
    size: TextUnit = HlSize.t13,
    /** Raised segments use the card colour by default; pass true to fill with the accent instead. */
    solid: Boolean = false,
) {
    val c = Hl.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HlRadius.chip))
            .background(c.input)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        items.forEach { item ->
            val on = item.value == selected
            val bg = when {
                !on -> Color.Transparent
                solid -> item.container ?: c.teal
                else -> c.card
            }
            val fg = when {
                !on -> c.muted
                solid -> item.content ?: c.onTeal
                else -> item.content ?: c.teal
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = minHeight)
                    .clip(RoundedCornerShape(9.dp))
                    .background(bg)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(item.value) })
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                HlText(item.label, size = size, weight = FontWeight.Bold, color = fg, align = TextAlign.Center)
            }
        }
    }
}

/** The 44×26 switch of the design. */
@Composable
fun HlSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val c = Hl.colors
    val knob by animateDpAsState(if (checked) 21.dp else 3.dp, tween(150), label = "knob")
    Box(
        modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (checked) c.teal else c.line),
    ) {
        Box(
            Modifier
                .offset(x = knob, y = 3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** A whole row that is the switch: the touch target is the row, not just the 44dp knob. */
@Composable
fun HlSwitchRow(
    title: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    minHeight: Dp = 56.dp,
) {
    val c = Hl.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            HlText(title, size = HlSize.t14, weight = FontWeight.SemiBold)
            if (subtitle != null) HlText(subtitle, size = HlSize.t12, color = c.muted, lineHeight = 1.4f)
        }
        HlSwitch(checked)
    }
}

/** Title, optional value, and a chevron when it leads somewhere. */
@Composable
fun HlSettingRow(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    valueColor: Color = Hl.colors.muted,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    minHeight: Dp = 56.dp,
) {
    val c = Hl.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            HlText(title, size = HlSize.t14, weight = FontWeight.SemiBold)
            if (subtitle != null) HlText(subtitle, size = HlSize.t12, color = c.muted, lineHeight = 1.4f)
        }
        if (value != null) HlText(value, size = if (subtitle == null) HlSize.t13 else HlSize.t12, color = valueColor, align = TextAlign.End)
        if (onClick != null) HlIcon(HlIcon.Chevron, size = 16.dp, tint = c.muted, strokeWidth = 2f)
    }
}

/** A rounded surface; rows inside it are separated with [HlDivider]. */
@Composable
fun HlCard(
    modifier: Modifier = Modifier,
    background: Color = Hl.colors.card,
    radius: Dp = HlRadius.card,
    border: BorderStroke? = null,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .padding(padding),
    ) { content() }
}

@Composable
fun HlDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Hl.colors.line))
}

/** The group headings ("백업", "보안", "경로별 보정"). */
@Composable
fun HlSectionLabel(text: String, modifier: Modifier = Modifier) {
    HlText(text, modifier = modifier, size = HlSize.t13, weight = FontWeight.Bold, color = Hl.colors.muted)
}

/** The sentence that closes every screen showing an estimate. */
@Composable
fun HlDisclaimer(modifier: Modifier = Modifier, text: String = "참고용 추정 · 임상 검증 아님 · 용량 판단은 의료진과") {
    HlText(text, modifier = modifier.fillMaxWidth(), size = HlSize.t12, color = Hl.colors.disc, lineHeight = 1.5f, align = TextAlign.Center)
}

/** A small rounded label with a coloured outline ("예상 · 실측 아님", "진행 중"). */
@Composable
fun HlPill(text: String, color: Color, modifier: Modifier = Modifier, size: TextUnit = HlSize.t12) {
    HlText(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, color, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        size = size, weight = FontWeight.Bold, color = color,
    )
}

/** The two kinds of notice the design allows: orange for model status, blue for information. */
enum class HlNotice { Warn, Info, Danger }

@Composable
fun HlBanner(
    kind: HlNotice,
    modifier: Modifier = Modifier,
    icon: HlIcon = if (kind == HlNotice.Info) HlIcon.Clock else HlIcon.Info,
    content: @Composable () -> Unit,
) {
    val c = Hl.colors
    val (soft, strong) = when (kind) {
        HlNotice.Warn -> c.orangeSoft to c.orange
        HlNotice.Info -> c.blueSoft to c.blue
        HlNotice.Danger -> c.dangerSoft to c.danger
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(HlRadius.chip))
            .background(soft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HlIcon(icon, size = 16.dp, tint = strong, strokeWidth = 2f, modifier = Modifier.padding(top = 2.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}

/** A tappable surface with a built-in accessibility label, for icon-only buttons. */
@Composable
fun HlIconButton(
    icon: HlIcon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Hl.colors.text,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { HlIcon(icon, size = iconSize, tint = tint, strokeWidth = 2f) }
}
