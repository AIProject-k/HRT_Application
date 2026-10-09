package com.hormonelog.app.ui.kit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hormonelog.app.ui.icons.HlIcon
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText
import kotlinx.coroutines.delay

/**
 * A number or short text field as the design draws it: a 56dp box, the value in bold, the
 * unit on the right. The border says what is going on — red for a value that cannot be
 * saved, teal for one the app filled in from the last record.
 */
@Composable
fun HlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    placeholder: String = "",
    unit: String? = null,
    keyboard: KeyboardType = KeyboardType.Decimal,
    error: Boolean = false,
    filled: Boolean = false,
    textSize: TextUnit = HlSize.t18,
    minHeight: Dp = 56.dp,
    textAlign: TextAlign = TextAlign.Start,
    imeAction: ImeAction = ImeAction.Done,
    onDone: (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    /** Shows dots instead of the characters and asks the keyboard not to learn them. */
    password: Boolean = false,
) {
    val c = Hl.colors
    val shape = RoundedCornerShape(HlRadius.chip)
    val border = when {
        error -> c.danger
        filled -> c.teal
        else -> c.line
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(c.input)
            .border(if (error || filled) 1.5.dp else 1.dp, border, shape)
            .padding(horizontal = 14.dp, vertical = if (singleLine) 0.dp else 12.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                HlText(placeholder, size = textSize, weight = FontWeight.Normal, color = c.dim, align = textAlign, modifier = Modifier.fillMaxWidth())
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                textStyle = TextStyle(color = c.text, fontSize = textSize, fontWeight = if (singleLine) FontWeight.Bold else FontWeight.Normal, textAlign = textAlign),
                cursorBrush = SolidColor(c.teal),
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard, imeAction = imeAction),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
        if (unit != null) HlText(unit, size = HlSize.t14, color = c.muted)
    }
}

/** The line under a field that explains why it cannot be saved. */
@Composable
fun HlFieldError(text: String, modifier: Modifier = Modifier) {
    HlText(text, modifier = modifier.semantics { liveRegion = LiveRegionMode.Assertive }, size = HlSize.t13, weight = FontWeight.SemiBold, color = Hl.colors.danger)
}

/** Back arrow, title, and one optional text action; 56dp tall, never smaller. */
@Composable
fun HlTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) HlIconButton(HlIcon.Back, "뒤로", onBack) else Box(Modifier.width(12.dp))
        HlText(title, modifier = Modifier.weight(1f), size = HlSize.t18, weight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (action != null && onAction != null) {
            Box(
                Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { HlText(action, size = HlSize.t14, weight = FontWeight.Bold, color = Hl.colors.teal) }
        }
    }
}

/** One destination of the bottom bar. */
data class NavTab<T>(val key: T, val label: String, val icon: HlIcon)

@Composable
fun <T> HlBottomNav(tabs: List<NavTab<T>>, current: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Hl.colors
    Column(modifier.fillMaxWidth().background(c.card).navigationBarsPadding()) {
        HlDivider()
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            tabs.forEach { tab ->
                val on = tab.key == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 60.dp)
                        .clickable(role = Role.Tab, interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(tab.key) }
                        .semantics { selected = on },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        Modifier.size(width = 52.dp, height = 28.dp).clip(RoundedCornerShape(14.dp)).background(if (on) c.tealSoft else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { HlIcon(tab.icon, size = 22.dp, tint = if (on) c.teal else c.muted) }
                    HlText(
                        tab.label, modifier = Modifier.padding(horizontal = 2.dp), size = HlSize.t12, weight = FontWeight.SemiBold,
                        color = if (on) c.teal else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The five-second message at the bottom, with an undo when the action can be reversed. */
@Composable
fun HlToastHost(message: String?, onUndo: (() -> Unit)?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hl.colors
    LaunchedEffect(message, onUndo != null) {
        if (message != null) {
            delay(if (onUndo != null) 5000 else 3000)
            onDismiss()
        }
    }
    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        AnimatedVisibility(
            visible = message != null,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(HlRadius.button))
                    .background(c.text)
                    .padding(start = 16.dp, top = 6.dp, end = 6.dp, bottom = 6.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HlText(message.orEmpty(), modifier = Modifier.weight(1f).padding(vertical = 8.dp), size = HlSize.t13, weight = FontWeight.SemiBold, color = c.bg, lineHeight = 1.4f)
                if (onUndo != null) {
                    Box(
                        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onUndo).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        HlText("실행 취소", size = HlSize.t13, weight = FontWeight.ExtraBold, color = c.bg, decoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                    }
                }
            }
        }
    }
}

/**
 * A confirmation that says exactly what will happen. The confirm button is teal, or red
 * for something that removes records; cancel is always the quiet one.
 */
@Composable
fun HlDialog(
    title: String,
    body: String,
    confirmLabel: String,
    cancelLabel: String = "취소",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
) {
    val c = Hl.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(HlRadius.sheet))
                    .background(c.card)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .padding(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HlText(title, size = HlSize.t18, weight = FontWeight.Bold, lineHeight = 1.4f)
                HlText(body, size = HlSize.t14, color = c.muted, lineHeight = 1.6f)
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HlButton(confirmLabel, onConfirm, Modifier.fillMaxWidth(), minHeight = 52.dp, size = HlSize.t14, container = if (danger) c.danger else c.teal, content = if (danger) c.bg else c.onTeal)
                    HlButton(cancelLabel, onDismiss, Modifier.fillMaxWidth(), kind = HlButtonKind.Text, minHeight = 48.dp, content = c.text)
                }
            }
        }
    }
}

/**
 * A bottom sheet with the design's handle, 24dp top corners and scrim. [fillHeight] makes it
 * a tall form sheet (92% of the screen) so a fixed header and footer can frame a scrolling
 * body; leave it off for a short menu that should wrap its content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HlSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Hl.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.card,
        contentColor = c.text,
        scrimColor = c.scrim,
        shape = RoundedCornerShape(topStart = HlRadius.sheet, topEnd = HlRadius.sheet),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(c.line))
        },
    ) {
        Column(modifier.fillMaxWidth().then(if (fillHeight) Modifier.fillMaxHeight(0.92f) else Modifier).imePadding(), content = content)
    }
}

/** One line of a menu sheet: an icon, a label, an optional note. */
@Composable
fun HlMenuItem(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: HlIcon? = null,
    note: String? = null,
    selected: Boolean = false,
    danger: Boolean = false,
) {
    val c = Hl.colors
    val fg = when {
        danger -> c.danger
        selected -> c.teal
        else -> c.text
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(HlRadius.chip))
            .background(if (danger) c.dangerSoft else if (selected) c.tealSoft else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            if (icon != null) HlIcon(icon, size = 22.dp, tint = fg) else if (selected) HlIcon(HlIcon.Check, size = 20.dp, tint = fg, strokeWidth = 2.2f)
        }
        HlText(label, modifier = Modifier.weight(1f), size = HlSize.t14, weight = FontWeight.SemiBold, color = fg)
        if (note != null) HlText(note, size = HlSize.t12, color = c.muted)
    }
}

/** Title (and optional subtitle) at the top of a menu sheet. */
@Composable
fun HlSheetTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 10.dp)), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        HlText(title, size = HlSize.t16, weight = FontWeight.Bold)
        if (subtitle != null) HlText(subtitle, size = HlSize.t13, color = Hl.colors.muted)
    }
}
