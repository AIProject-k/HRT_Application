package com.hormonelog.app.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlRadius
import com.hormonelog.app.ui.theme.HlSize
import com.hormonelog.app.ui.theme.HlText

/**
 * A scrolling screen with the design's 20dp gutters. [bottomInset] keeps the last item clear of
 * the system navigation bar on screens that have no bottom tab bar of their own.
 */
@Composable
fun ScreenScroll(
    modifier: Modifier = Modifier,
    gap: Dp = 12.dp,
    padding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
    bottomInset: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
        verticalArrangement = Arrangement.spacedBy(gap),
    ) {
        content()
        if (bottomInset) Spacer(Modifier.navigationBarsPadding())
    }
}

/** The big title at the top of a tab screen, 48dp tall so the screens line up. */
@Composable
fun ScreenTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.padding(bottom = 4.dp)) {
            if (subtitle != null) HlText(subtitle, size = HlSize.t12, color = Hl.colors.muted)
            HlText(title, size = HlSize.t22, weight = FontWeight.Bold, modifier = Modifier.padding(top = if (subtitle != null) 2.dp else 0.dp))
        }
        trailing?.invoke()
    }
}

/** A small card with a label, a value and a line of detail — the tiles on the home screen. */
@Composable
fun HlStatTile(
    label: String,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
    detail: String? = null,
) {
    val c = Hl.colors
    Column(
        modifier.clip(RoundedCornerShape(HlRadius.button)).background(c.card).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HlText(label, size = HlSize.t12, color = c.muted)
        value()
        if (detail != null) HlText(detail, size = HlSize.t12, color = c.muted, lineHeight = 1.35f)
    }
}

/** The yellow diamond that means "a real blood test", at text size. */
@Composable
fun MeasuredMark(size: Dp = 8.dp, hollow: Boolean = false, modifier: Modifier = Modifier) {
    val c = Hl.colors
    androidx.compose.foundation.Canvas(modifier.size(size + 2.dp)) {
        val s = this.size.minDimension * 0.72f
        val o = androidx.compose.ui.geometry.Offset(this.size.width / 2, this.size.height / 2)
        rotate(45f, o) {
            if (hollow) {
                drawRect(c.yMark, topLeft = androidx.compose.ui.geometry.Offset(o.x - s / 2, o.y - s / 2), size = androidx.compose.ui.geometry.Size(s, s), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
            } else {
                drawRect(c.yMark, topLeft = androidx.compose.ui.geometry.Offset(o.x - s / 2, o.y - s / 2), size = androidx.compose.ui.geometry.Size(s, s))
            }
        }
    }
}

/** Full-bleed background for screens that are not scrolled (onboarding, lock). */
@Composable
fun ScreenBox(modifier: Modifier = Modifier, color: Color = Hl.colors.bg, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(color)) { content() }
}

@Composable
fun VSpace(height: Dp) = Spacer(Modifier.height(height))
