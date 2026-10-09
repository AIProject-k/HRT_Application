package com.hormonelog.app.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The line icons of the design, drawn from their 24×24 path data so they follow the text
 * colour and scale with their box. They replace the emoji the app used before: an emoji
 * cannot be recoloured, differs from phone to phone, and says nothing to a screen reader.
 *
 * Arc flags are written with separators on purpose; not every path parser accepts the
 * compact "a4 4 0 100-8" form.
 */
enum class HlIcon(val paths: List<String>, val filled: Boolean = false) {
    Home(listOf("M4 11 l8 -7 8 7 v9 a1 1 0 0 1 -1 1 h-4 v-6 h-6 v6 H5 a1 1 0 0 1 -1 -1 z")),
    Timeline(listOf("M8 6h12M8 12h12M8 18h12", "M4 6h.01M4 12h.01M4 18h.01")),
    Flow(listOf("M3 17c3-8 5-8 7-2s4 6 7-4 4-3 4-3")),
    Me(listOf("M12 12 a4 4 0 1 0 0 -8 a4 4 0 0 0 0 8 z", "M4 21c1-4 4-6 8-6s7 2 8 6")),

    Back(listOf("M15 5l-7 7 7 7")),
    Chevron(listOf("M9 5l7 7-7 7")),
    Close(listOf("M6 6l12 12M18 6L6 18")),
    Plus(listOf("M12 5v14M5 12h14")),
    Check(listOf("M4 12l5 5L20 6")),
    Search(listOf("M11 4 a7 7 0 1 0 0 14 a7 7 0 0 0 0 -14 z", "M20 20l-4-4")),
    Dots(listOf("M12 3.2 a1.8 1.8 0 1 0 0 3.6 a1.8 1.8 0 0 0 0 -3.6 z", "M12 10.2 a1.8 1.8 0 1 0 0 3.6 a1.8 1.8 0 0 0 0 -3.6 z", "M12 17.2 a1.8 1.8 0 1 0 0 3.6 a1.8 1.8 0 0 0 0 -3.6 z"), filled = true),
    External(listOf("M14 4h6v6M20 4l-9 9M18 14v6H4V6h6")),

    Phone(listOf("M8.5 2.5 h7 a2.5 2.5 0 0 1 2.5 2.5 v14 a2.5 2.5 0 0 1 -2.5 2.5 h-7 A2.5 2.5 0 0 1 6 19 V5 a2.5 2.5 0 0 1 2.5 -2.5 z", "M10.5 18.5h3")),
    Lock(listOf("M7 10.5 h10 a2 2 0 0 1 2 2 v6 a2 2 0 0 1 -2 2 h-10 a2 2 0 0 1 -2 -2 v-6 a2 2 0 0 1 2 -2 z", "M8 10.5 V7.5 a4 4 0 0 1 8 0 v3")),
    Calendar(listOf("M5.5 5 h13 a2 2 0 0 1 2 2 v11 a2 2 0 0 1 -2 2 h-13 a2 2 0 0 1 -2 -2 v-11 a2 2 0 0 1 2 -2 z", "M3.5 10h17M8 3v4M16 3v4")),
    Clock(listOf("M12 3 a9 9 0 1 0 0 18 a9 9 0 0 0 0 -18 z", "M12 7v5l3 2")),
    Info(listOf("M12 3 a9 9 0 1 0 0 18 a9 9 0 0 0 0 -18 z", "M12 8v5M12 16v.5")),
    Alert(listOf("M12 3l10 18H2z", "M12 10v5M12 18v.5")),
    CircleMinus(listOf("M12 3 a9 9 0 1 0 0 18 a9 9 0 0 0 0 -18 z", "M8 12h8")),

    Injection(listOf("M18 2l4 4M16 4l4 4M14 6l4 4-9 9H5v-4z", "M3 21l2-2")),
    Pill(listOf("M10.5 3.5 a5 5 0 0 1 7 7 l-7 7 a5 5 0 0 1 -7 -7 z", "M7 7l7 7")),
    Lab(listOf("M12 3 s6 7 6 11 a6 6 0 0 1 -12 0 c0 -4 6 -11 6 -11 z")),
    Missed(listOf("M12 3 a9 9 0 1 0 0 18 a9 9 0 0 0 0 -18 z", "M9 9l6 6M15 9l-6 6")),
    Condition(listOf("M12 20 s-7 -4.5 -7 -10 a4 4 0 0 1 7 -2.6 A4 4 0 0 1 19 10 c0 5.5 -7 10 -7 10 z")),
    Memo(listOf("M6 3h9l4 4v14H6z", "M9 11h7M9 15h7")),
    Gel(listOf("M8 3h8v4l-1 2v12H9V9L8 7z")),
    Patch(listOf("M6.5 4 h11 a2.5 2.5 0 0 1 2.5 2.5 v11 a2.5 2.5 0 0 1 -2.5 2.5 h-11 A2.5 2.5 0 0 1 4 17.5 v-11 A2.5 2.5 0 0 1 6.5 4 z", "M9.5 9.5 h5 v5 h-5 z")),
    Edit(listOf("M4 20h4L20 8l-4-4L4 16z")),
    Copy(listOf("M8 8h12v12H8z", "M4 16V4h12")),
    Trash(listOf("M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13")),
    Body(listOf("M12 3v18M7 8h10M9 21l3-6 3 6")),
    Box(listOf("M3 7l9-4 9 4v10l-9 4-9-4z", "M3 7l9 4 9-4M12 11v10")),
}

/** Draws [icon] at [size], following [tint] (the current text colour by default). */
@Composable
fun HlIcon(
    icon: HlIcon,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    tint: Color = LocalContentColor.current,
    strokeWidth: Float = 1.8f,
) {
    val paths = remember(icon) { icon.paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        withTransform({ scale(s, s, pivot = Offset.Zero) }) {
            paths.forEach { path ->
                if (icon.filled) {
                    drawPath(path, tint, style = Fill)
                } else {
                    drawPath(path, tint, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
            }
        }
    }
}
