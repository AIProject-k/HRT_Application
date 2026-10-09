package com.hormonelog.app.feature.flow

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.ui.theme.Hl
import com.hormonelog.app.ui.theme.HlColors
import com.hormonelog.core.modelengine.EstimatePoint
import com.hormonelog.core.modelengine.EstimateSeries
import java.time.Instant
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** A measured value to mark on the chart. [used] false = drawn hollow: it did not shape the curve. */
data class ChartLab(val at: Instant, val value: Double, val used: Boolean)

enum class TickState { TAKEN, PLANNED, MISSED }

/** One dose tick under the plot. [antiandrogen] ticks go in their own (violet) row. */
data class ChartDose(val at: Instant, val state: TickState, val antiandrogen: Boolean)

/** Everything the chart draws; built by the caller so the chart itself knows nothing of records. */
class ChartModel(
    val curve: EstimateSeries?,
    val from: Instant,
    val to: Instant,
    val now: Instant,
    val labs: List<ChartLab>,
    val doses: List<ChartDose>,
    /** True for Total T (blue); false for E2 (teal). */
    val isTotalT: Boolean,
    /** Gray reference band in the units of the chart, or null. */
    val guide: ClosedFloatingPointRange<Double>? = null,
)

/**
 * The 예상 흐름 chart, shared by the home card (compact) and the full screen. A solid line is
 * what the records say happened, a dashed one what the plan says is next; the band is the
 * estimate's range; a diamond is a real blood test, hollow when it was not used to calibrate.
 * [scrub] (0..1 across the plot) shows a cursor and a dot on the curve.
 */
@Composable
fun EstimateChart(
    model: ChartModel,
    fmt: Fmt,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    scrub: Float? = null,
    onScrub: ((Float?) -> Unit)? = null,
    summary: String = "",
) {
    val c = Hl.colors
    val measurer = rememberTextMeasurer()
    val accent = if (model.isTotalT) c.blue else c.teal
    val soft = if (model.isTotalT) c.blueSoft else c.tealSoft
    val w = if (compact) 300f else 320f
    val h = if (compact) 96f else 210f
    val gestures = if (onScrub == null || compact) Modifier else Modifier
        .pointerInput(model.from, model.to) {
            detectTapGestures(onPress = { pos ->
                onScrub(fractionOf(pos.x, size.width.toFloat(), w))
                tryAwaitRelease()
                onScrub(null)
            })
        }
        .pointerInput(model.from, model.to) {
            detectHorizontalDragGestures(
                onDragStart = { pos -> onScrub(fractionOf(pos.x, size.width.toFloat(), w)) },
                onDragEnd = { onScrub(null) },
                onDragCancel = { onScrub(null) },
                onHorizontalDrag = { change, _ ->
                    change.consume()
                    onScrub(fractionOf(change.position.x, size.width.toFloat(), w))
                },
            )
        }
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(w / h)
            .semantics { contentDescription = summary }
            .then(gestures),
    ) {
        drawChart(model, c, accent, soft, measurer, fmt, compact, scrub, w, h)
    }
}

/**
 * Draws the chart off screen, for the report and the share image, in whichever palette [colors]
 * is. One unit of the chart's 320-wide drawing space becomes one dp at the density chosen here,
 * so the labels keep their place however wide the picture is. [heightUnits] flattens it.
 */
fun renderChart(context: Context, model: ChartModel, colors: HlColors, fmt: Fmt, widthPx: Int, compact: Boolean = false, heightUnits: Float? = null): ImageBitmap {
    val w = if (compact) 300f else 320f
    val h = heightUnits ?: if (compact) 96f else 210f
    val heightPx = (widthPx * h / w).roundToInt()
    val bitmap = ImageBitmap(widthPx, heightPx)
    val density = Density(density = widthPx / w, fontScale = 1f)
    val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
    val accent = if (model.isTotalT) colors.blue else colors.teal
    val soft = if (model.isTotalT) colors.blueSoft else colors.tealSoft
    CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(bitmap), Size(widthPx.toFloat(), heightPx.toFloat())) {
        drawChart(model, colors, accent, soft, measurer, fmt, compact, null, w, h)
    }
    return bitmap
}

private fun fractionOf(x: Float, widthPx: Float, vbW: Float): Float {
    val plotLeft = (PL_FULL / vbW) * widthPx
    val plotRight = widthPx - (PR / vbW) * widthPx
    return ((x - plotLeft) / (plotRight - plotLeft)).coerceIn(0f, 1f)
}

private const val PL_FULL = 34f
private const val PR = 8f

private fun DrawScope.drawChart(
    m: ChartModel,
    c: HlColors,
    accent: Color,
    soft: Color,
    measurer: TextMeasurer,
    fmt: Fmt,
    compact: Boolean,
    scrub: Float?,
    vbW: Float,
    vbH: Float,
) {
    val sx = size.width / vbW
    val sy = size.height / vbH
    val pl = if (compact) 0f else PL_FULL
    val pr = if (compact) 0f else PR
    val pt = 10f
    val rows = if (compact) 14f else 30f
    val labelRow = if (compact) 0f else 18f
    val pb = rows + labelRow
    val iw = vbW - pl - pr
    val ih = vbH - pt - pb
    fun px(x: Float, y: Float) = Offset(x * sx, y * sy)

    val t0 = m.from.toEpochMilli().toDouble()
    val t1 = m.to.toEpochMilli().toDouble()
    fun xOf(t: Instant): Float = (pl + ((t.toEpochMilli() - t0) / (t1 - t0)) * iw).toFloat()

    val points = m.curve?.points.orEmpty().filter { it.at.toEpochMilli() in m.from.toEpochMilli()..m.to.toEpochMilli() }
    val unit = if (m.isTotalT) 20.0 else 50.0
    var top = points.maxOfOrNull { it.upper } ?: (if (m.isTotalT) 100.0 else 250.0)
    m.labs.filter { it.at in m.from..m.to }.forEach { top = max(top, it.value) }
    m.guide?.let { if (!compact) top = max(top, it.endInclusive) }
    val ymax = max(unit, ceil(top * 1.08 / unit) * unit)
    fun yOf(v: Double): Float = (pt + ih - (v.coerceIn(0.0, ymax) / ymax) * ih).toFloat()

    val labelStyle = TextStyle(fontSize = (if (compact) 8f else 9.5f).sp, color = c.muted)

    if (!compact) {
        // grid + y labels
        for (v in listOf(0.0, ymax / 2, ymax)) {
            drawLine(c.line, px(pl, yOf(v)), px(vbW - pr, yOf(v)), strokeWidth = 1f)
            val label = measurer.measure(v.toInt().toString(), labelStyle)
            drawText(label, topLeft = Offset(px(pl - 5f, 0f).x - label.size.width, px(0f, yOf(v)).y - label.size.height / 2f))
        }
        m.guide?.let { g ->
            drawRect(c.guide, topLeft = px(pl, yOf(g.endInclusive)), size = Size(iw * sx, (yOf(g.start) - yOf(g.endInclusive)) * sy))
            val l = measurer.measure("가이드라인 참고", TextStyle(fontSize = 9.sp, color = c.muted))
            drawText(l, topLeft = px(pl + 4f, yOf(g.endInclusive) + 3f))
        }
    }

    // range band, then the curve: solid up to now, dashed after
    if (points.size >= 2) {
        val band = Path().apply {
            points.forEachIndexed { i, p -> val o = px(xOf(p.at), yOf(p.upper)); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
            for (i in points.indices.reversed()) { val o = px(xOf(points[i].at), yOf(points[i].lower)); lineTo(o.x, o.y) }
            close()
        }
        drawPath(band, soft, style = Fill)
        fun line(seg: List<EstimatePoint>, dashed: Boolean) {
            if (seg.size < 2) return
            val path = Path().apply {
                seg.forEachIndexed { i, p -> val o = px(xOf(p.at), yOf(p.median)); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) }
            }
            drawPath(
                path, accent,
                style = Stroke(
                    width = (if (compact) 2.5f else 2.2f) * sx,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4f * sx, 4f * sx)) else null,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round,
                ),
            )
        }
        val nowMs = m.now.toEpochMilli()
        line(points.filter { it.at.toEpochMilli() <= nowMs }, dashed = false)
        line(points.filter { it.at.toEpochMilli() >= nowMs - (t1 - t0) / 160 }, dashed = true)
    }

    // now marker
    if (m.now in m.from..m.to) {
        val x = xOf(m.now)
        drawLine(c.muted, px(x, pt), px(x, pt + ih), strokeWidth = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * sx, 3f * sx)))
        val l = measurer.measure("지금", TextStyle(fontSize = (if (compact) 8f else 9f).sp, color = c.muted))
        // At the right edge (a report that ends today) the word goes to the marker's left instead of off the picture.
        val labelWidth = l.size.width / sx
        drawText(l, topLeft = px(if (x + 3f + labelWidth > vbW - pr) x - 3f - labelWidth else x + 3f, pt - 1f))
    }

    // measured values
    for (lab in m.labs) {
        if (lab.at !in m.from..m.to) continue
        val o = px(xOf(lab.at), yOf(lab.value))
        val r = (if (compact) 4f else 5f) * sx
        rotate(45f, o) {
            drawRect(if (lab.used) c.yMark else c.card, topLeft = Offset(o.x - r, o.y - r), size = Size(r * 2, r * 2))
            drawRect(
                if (lab.used) c.bg else c.yMark, topLeft = Offset(o.x - r, o.y - r), size = Size(r * 2, r * 2),
                style = Stroke(width = (if (lab.used) 1.2f else 1.6f) * sx),
            )
        }
    }

    // dose ticks: estrogen row, then anti-androgen row
    val row1 = pt + ih + 6f
    val tickH = if (compact) 6f else 8f
    for (d in m.doses.filter { !it.antiandrogen && it.at in m.from..m.to }) {
        val x = xOf(d.at)
        val color = if (d.state == TickState.MISSED) c.danger else c.blue
        if (d.state == TickState.TAKEN) {
            drawRect(color, topLeft = px(x - 1.5f, row1), size = Size(3f * sx, tickH * sy))
        } else {
            drawRect(color, topLeft = px(x - 1.5f, row1), size = Size(3f * sx, tickH * sy), style = Stroke(width = 1.1f * sx))
        }
    }
    if (!compact) {
        val row2 = row1 + tickH + 4f
        val aa = m.doses.filter { it.antiandrogen && it.at in m.from..m.to }
        val spanDays = (t1 - t0) / 86_400_000.0
        if (spanDays <= 40) {
            for (d in aa) {
                val x = xOf(d.at)
                val filled = d.state == TickState.TAKEN
                val color = if (d.state == TickState.MISSED) c.danger else c.violet
                if (filled) drawRect(color, topLeft = px(x - 1f, row2), size = Size(2.4f * sx, 7f * sy))
                else drawRect(color, topLeft = px(x - 1f, row2), size = Size(2.4f * sx, 7f * sy), style = Stroke(width = 1f * sx))
            }
        } else if (aa.isNotEmpty()) {
            val first = aa.minOf { it.at }
            val lastTaken = aa.filter { it.state == TickState.TAKEN }.maxOfOrNull { it.at } ?: first
            val a = xOf(maxOf(first, m.from))
            val b = xOf(minOf(lastTaken, m.now))
            if (b > a) drawRoundRect(c.violet, topLeft = px(a, row2 + 2f), size = Size((b - a) * sx, 3f * sy), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * sx))
            val plannedEnd = aa.filter { it.state == TickState.PLANNED }.maxOfOrNull { it.at }
            if (plannedEnd != null) {
                val pa = xOf(maxOf(m.now, m.from))
                val pb = xOf(minOf(plannedEnd, m.to))
                if (pb > pa) drawRoundRect(c.violet, topLeft = px(pa, row2 + 2f), size = Size((pb - pa) * sx, 3f * sy), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5f * sx), style = Stroke(width = 0.8f * sx))
            }
        }
        // x labels
        for (i in 0 until 4) {
            val t = Instant.ofEpochMilli((t0 + (t1 - t0) * (i + 0.5) / 4).toLong())
            val label = measurer.measure(fmt.short(t), labelStyle)
            drawText(label, topLeft = Offset(px(xOf(t), 0f).x - label.size.width / 2f, size.height - label.size.height - 1f))
        }
    }

    // scrub cursor
    if (scrub != null && !compact) {
        val x = pl + scrub.coerceIn(0f, 1f) * iw
        drawLine(c.text, px(x, pt), px(x, pt + ih), strokeWidth = 1f)
        val at = Instant.ofEpochMilli((t0 + (t1 - t0) * scrub).toLong())
        m.curve?.pointAt(at)?.let { p ->
            val o = px(x, yOf(p.median))
            drawCircle(c.card, radius = 6.5f * sx, center = o)
            drawCircle(accent, radius = 4.5f * sx, center = o)
        }
    }
}

/** The instant a scrub fraction points at, for the readout above the chart. */
fun scrubInstant(model: ChartModel, fraction: Float): Instant =
    Instant.ofEpochMilli(model.from.toEpochMilli() + ((model.to.toEpochMilli() - model.from.toEpochMilli()) * fraction.coerceIn(0f, 1f)).toLong())
