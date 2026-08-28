package com.hormonelog.app.feature.flow

import android.graphics.Paint as NativePaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import com.hormonelog.app.feature.dashboard.HormoneSeries
import com.hormonelog.app.feature.dashboard.canonical
import com.hormonelog.app.feature.dashboard.fmtShort
import com.hormonelog.app.ui.theme.HlColor
import com.hormonelog.core.domain.Analyte
import com.hormonelog.core.domain.DoseEvent
import com.hormonelog.core.domain.DoseStatus
import com.hormonelog.core.domain.LabResult
import com.hormonelog.core.modelengine.EstimateSeries
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil

// Design plot geometry (viewBox 0 0 340 200).
private const val VB_W = 340f
private const val VB_H = 200f
private const val PL = 36f
private const val PW = 294f
private const val PT = 12f
private const val PH = 152f
private const val AXIS_Y = PT + PH // 164
private const val DAY_MS = 86_400_000.0

private data class Frame(val t0: Long, val t1: Long, val vmax: Float) {
    fun xOf(millis: Long): Float = PL + ((millis - t0).toDouble() / (t1 - t0)).toFloat() * PW
    fun yOf(v: Float): Float = PT + (1f - v.coerceIn(0f, vmax) / vmax) * PH
}

private fun windowMillis(rangeDays: Int, now: Instant): Pair<Long, Long> {
    val span = rangeDays * DAY_MS
    val lead = if (rangeDays == 7) 0.45 else 0.7
    val t0 = (now.toEpochMilli() - span * lead).toLong()
    return t0 to (t0 + span).toLong()
}

/** Time window the chart shows for a given range — callers compute curves over this. */
fun chartWindow(rangeDays: Int, now: Instant): Pair<Instant, Instant> {
    val (a, b) = windowMillis(rangeDays, now)
    return Instant.ofEpochMilli(a) to Instant.ofEpochMilli(b)
}

private fun fractionAt(x: Float, widthPx: Float): Float {
    val sx = widthPx / VB_W
    return ((x / sx - PL) / PW).coerceIn(0f, 1f)
}

/** Convert a scrub fraction (0..1 across the plot) back to an instant. */
fun scrubInstant(rangeDays: Int, now: Instant, fraction: Float): Instant {
    val (t0, t1) = windowMillis(rangeDays, now)
    return Instant.ofEpochMilli((t0 + (t1 - t0) * fraction.coerceIn(0f, 1f)).toLong())
}

private fun trim(v: Float): String =
    if (v % 1f == 0f) v.toInt().toString() else ((v * 10).toInt() / 10.0).toString()

/**
 * The 예상 흐름 chart. Framing (axes, now divider, future shading, dose ticks,
 * measured diamonds, scrub cursor) is always drawn. The predicted [curve] — a
 * literature population estimate — is drawn as solid (past) + dashed (forecast)
 * with an uncertainty band when present; when null the plot shows a "준비 중"
 * message instead of a fabricated line.
 */
@Composable
fun EstimateChart(
    doses: List<DoseEvent>,
    labs: List<LabResult>,
    series: HormoneSeries,
    rangeDays: Int,
    now: Instant,
    zone: ZoneId,
    curve: EstimateSeries?,
    scrubFraction: Float?,
    onScrub: (Float?) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val density = LocalDensity.current
    val (t0, t1) = windowMillis(rangeDays, now)
    val analyte = if (series == HormoneSeries.E2) Analyte.ESTRADIOL else Analyte.TOTAL_TESTOSTERONE

    val defaultVmax = if (series == HormoneSeries.E2) 480f else 240f
    val vmax = if (curve != null && curve.points.isNotEmpty()) {
        val hi = curve.points.maxOf { it.upper }.toFloat()
        (ceil(maxOf(defaultVmax * 0.5f, hi * 1.15f) / 50f) * 50f).coerceAtLeast(50f)
    } else {
        defaultVmax
    }
    val frame = Frame(t0, t1, vmax)

    val labelPx = with(density) { (if (compact) 8f else 9.5f).sp.toPx() }
    val noticePx = with(density) { (if (compact) 9f else 11f).sp.toPx() }

    val gesture = if (compact) Modifier else Modifier.pointerInput(rangeDays, now) {
        awaitEachGesture {
            val down = awaitFirstDown()
            onScrub(fractionAt(down.position.x, size.width.toFloat()))
            down.consume()
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    onScrub(null)
                    break
                }
                onScrub(fractionAt(change.position.x, size.width.toFloat()))
                change.consume()
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(if (compact) 320f / 96f else VB_W / VB_H)
            .then(gesture),
    ) {
        val sx = size.width / VB_W
        val sy = size.height / VB_H
        fun px(x: Float, y: Float) = Offset(x * sx, y * sy)
        fun faintText(text: String, x: Float, y: Float, colorArgb: Int, align: NativePaint.Align, bold: Boolean = false) {
            drawContext.canvas.nativeCanvas.drawText(
                text, px(x, y).x, px(x, y).y,
                NativePaint().apply {
                    color = colorArgb
                    textSize = labelPx
                    textAlign = align
                    isAntiAlias = true
                    isFakeBoldText = bold
                },
            )
        }

        val nowX = frame.xOf(now.toEpochMilli())
        val accent = if (series == HormoneSeries.E2) HlColor.Teal else HlColor.Blue

        // future region shading
        drawRect(
            color = Color.White.copy(alpha = 0.025f),
            topLeft = px(nowX, PT),
            size = Size((PL + PW - nowX).coerceAtLeast(0f) * sx, PH * sy),
        )

        // y grid + labels
        val ticks = 3
        for (i in 0..ticks) {
            val v = frame.vmax / ticks * i
            val y = frame.yOf(v)
            drawLine(HlColor.Border06, px(PL, y), px(PL + PW, y), strokeWidth = 1f)
            if (!compact) faintText(v.toInt().toString(), PL - 4f, y + 3f, HlColor.TextFaint.toArgb(), NativePaint.Align.RIGHT)
        }

        // x labels
        if (!compact) {
            for (i in 0..4) {
                val t = frame.t0 + (frame.t1 - frame.t0) * i / 4
                faintText(fmtShort(Instant.ofEpochMilli(t), zone), frame.xOf(t), VB_H - 4f, HlColor.TextFaint.toArgb(), NativePaint.Align.CENTER)
            }
        }

        // ── uncertainty band + curve ──
        if (curve != null && curve.points.size >= 2) {
            val pts = curve.points.filter { it.at.toEpochMilli() in frame.t0..frame.t1 }
            if (pts.size >= 2) {
                val band = Path().apply {
                    pts.forEachIndexed { i, p ->
                        val o = px(frame.xOf(p.at.toEpochMilli()), frame.yOf(p.upper.toFloat()))
                        if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                    }
                    for (i in pts.indices.reversed()) {
                        val p = pts[i]
                        val o = px(frame.xOf(p.at.toEpochMilli()), frame.yOf(p.lower.toFloat()))
                        lineTo(o.x, o.y)
                    }
                    close()
                }
                drawPath(band, color = accent.copy(alpha = 0.14f))

                fun median(seg: List<com.hormonelog.core.modelengine.EstimatePoint>, dashed: Boolean) {
                    if (seg.size < 2) return
                    val path = Path().apply {
                        seg.forEachIndexed { i, p ->
                            val o = px(frame.xOf(p.at.toEpochMilli()), frame.yOf(p.median.toFloat()))
                            if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                        }
                    }
                    drawPath(
                        path, color = accent,
                        style = Stroke(
                            width = 2.2f,
                            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f * sx, 5f * sx)) else null,
                        ),
                    )
                }
                val nowMs = now.toEpochMilli()
                median(pts.filter { it.at.toEpochMilli() <= nowMs }, dashed = false)
                median(pts.filter { it.at.toEpochMilli() >= nowMs }, dashed = true)
            }
        }

        // now divider
        drawLine(
            color = Color.White.copy(alpha = 0.28f),
            start = px(nowX, PT),
            end = px(nowX, AXIS_Y),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * sx, 3f * sx)),
        )

        // dose ticks
        doses.forEach { d ->
            val m = d.occurredAt.toEpochMilli()
            if (m in frame.t0..frame.t1) {
                val x = frame.xOf(m)
                drawLine(
                    color = if (d.status == DoseStatus.SKIPPED) HlColor.TextDim else HlColor.Blue,
                    start = px(x, AXIS_Y),
                    end = px(x, AXIS_Y + 8f),
                    strokeWidth = 2f,
                )
            }
        }

        // measured lab diamonds (+ value label)
        labs.forEach { l ->
            val t = l.collectedAt?.toEpochMilli() ?: return@forEach
            if (t !in frame.t0..frame.t1) return@forEach
            val v = l.canonical(analyte)?.toFloat() ?: return@forEach
            val c = px(frame.xOf(t), frame.yOf(v))
            val r = (if (compact) 4f else 5.5f) * sx
            val path = Path().apply {
                moveTo(c.x, c.y - r); lineTo(c.x + r, c.y); lineTo(c.x, c.y + r); lineTo(c.x - r, c.y); close()
            }
            drawPath(path, color = HlColor.Yellow)
            drawPath(path, color = HlColor.Card, style = Stroke(width = 2f))
            if (!compact) {
                drawContext.canvas.nativeCanvas.drawText(
                    "${trim(v)} 실측", c.x, c.y - r - labelPx / 2,
                    NativePaint().apply {
                        color = HlColor.Yellow.toArgb()
                        textSize = labelPx
                        textAlign = NativePaint.Align.CENTER
                        isAntiAlias = true
                        isFakeBoldText = true
                    },
                )
            }
        }

        // "준비 중" — no evidence-backed curve for the recorded routes.
        if (curve == null) {
            drawContext.canvas.nativeCanvas.drawText(
                if (compact) "예상 곡선 준비 중" else "예상 곡선은 근거 자료가 준비되면 표시돼요",
                px(PL + PW / 2f, PT + PH / 2f).x,
                px(PL + PW / 2f, PT + PH / 2f).y,
                NativePaint().apply {
                    color = HlColor.TextDim.toArgb()
                    textSize = noticePx
                    textAlign = NativePaint.Align.CENTER
                    isAntiAlias = true
                },
            )
        }

        // scrub cursor
        val f = scrubFraction
        if (f != null && !compact) {
            val x = PL + f.coerceIn(0f, 1f) * PW
            drawLine(
                color = HlColor.TextPrimary.copy(alpha = 0.5f),
                start = px(x, PT),
                end = px(x, AXIS_Y),
                strokeWidth = 1f,
            )
            if (curve != null && curve.points.isNotEmpty()) {
                val tScrub = frame.t0 + ((frame.t1 - frame.t0) * f).toLong()
                val nearest = curve.points.minByOrNull { kotlin.math.abs(it.at.toEpochMilli() - tScrub) }
                if (nearest != null) {
                    val c = px(x, frame.yOf(nearest.median.toFloat()))
                    drawCircle(HlColor.Background, radius = 4f * sx, center = c)
                    drawCircle(accent, radius = 4f * sx, center = c, style = Stroke(width = 2.2f))
                }
            }
        }
    }
}
