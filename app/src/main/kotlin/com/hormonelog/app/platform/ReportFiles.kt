package com.hormonelog.app.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.FileProvider
import com.hormonelog.app.analysis.ReportData
import com.hormonelog.app.feature.common.Fmt
import com.hormonelog.app.feature.flow.renderChart
import com.hormonelog.app.state.ReportFile
import com.hormonelog.app.state.ReportKind
import com.hormonelog.app.ui.theme.DarkHlColors
import com.hormonelog.app.ui.theme.HlColors
import com.hormonelog.app.ui.theme.LightHlColors
import java.io.File
import java.time.LocalDate

/**
 * Makes the hospital-visit report: an A4 PDF, or a tall picture for a chat. Both are built on the
 * phone from the records and written to the app's private cache; nothing is sent anywhere until
 * the user picks an app in the share sheet. Neither carries a name or the app's name.
 */
object ReportFiles {
    private const val DIR = RecordCopies.REPORTS_DIR

    /**
     * A report holds the records in the clear, outside the encrypted file, so it does not stay: the share sheet
     * has long read it by now. Whatever is older goes whenever the app comes back to the front.
     */
    const val KEEP_MILLIS = 3_600_000L
    private const val DISCLAIMER = "참고용 추정 · 임상 검증 아님 · 용량 판단은 의료진과. 예상값은 문헌 기반 모델의 추정 범위이며 실측값과 구분해 표시했어요. 이 문서는 사용자 기기에서 만들어졌어요."

    fun create(context: Context, kind: ReportKind, data: ReportData, fmt: Fmt): ReportFile {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        RecordCopies.purgeReports(context.cacheDir, KEEP_MILLIS, System.currentTimeMillis())
        val day = LocalDate.now(fmt.zone)
        return when (kind) {
            ReportKind.PDF -> {
                val file = File(dir, "report-$day.pdf")
                file.outputStream().use { writePdf(context, data, fmt, it) }
                ReportFile(file.absolutePath, "application/pdf", file.name)
            }
            ReportKind.IMAGE -> {
                val file = File(dir, "report-$day.png")
                val bitmap = drawImage(context, data, fmt)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                ReportFile(file.absolutePath, "image/png", file.name)
            }
        }
    }

    fun uriFor(context: Context, file: ReportFile): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(file.path))

    // ── paints ────────────────────────────────────────────────

    private fun paint(size: Float, color: Int, bold: Boolean = false): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun stroke(color: Int, width: Float): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = width }

    private fun layout(text: CharSequence, p: TextPaint, width: Int, spacing: Float = 1.3f, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width.coerceAtLeast(1)).setLineSpacing(0f, spacing).setAlignment(align).build()

    private fun Canvas.diamond(cx: Float, cy: Float, r: Float, paint: Paint) {
        val path = Path().apply { moveTo(cx, cy - r); lineTo(cx + r, cy); lineTo(cx, cy + r); lineTo(cx - r, cy); close() }
        drawPath(path, paint)
    }

    // ── A4 PDF ────────────────────────────────────────────────

    private const val W = 595
    private const val H = 842
    private const val LEFT = 44f
    private const val TOP = 40f
    private const val CONTENT_W = W - 2 * LEFT
    private const val FOOTER = 46f
    private const val LIMIT = H - 40f - FOOTER

    /** Pages one after another; a section that does not fit moves to the next page. */
    private class Pages(private val doc: PdfDocument, private val colors: HlColors) {
        private var page: PdfDocument.Page? = null
        private var number = 0
        lateinit var canvas: Canvas
        var y = TOP

        val ink = colors.text.toArgb()
        val muted = colors.muted.toArgb()
        val line = colors.line.toArgb()
        val line2 = colors.input.toArgb()
        val teal = colors.teal.toArgb()
        val yellow = colors.yellow.toArgb()

        /** The diamond's own fill, brighter than the text yellow, as the chart draws it. */
        val yMark = colors.yMark.toArgb()

        fun begin() {
            number++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, number).create())
            canvas = page!!.canvas
            canvas.drawColor(android.graphics.Color.WHITE)
            y = TOP
        }

        fun finish() {
            val p = page ?: return
            canvas.drawLine(LEFT, H - 40f - FOOTER + 10f, W - LEFT, H - 40f - FOOTER + 10f, stroke(line, 0.8f))
            val note = layout(DISCLAIMER, paint(8.5f, muted), (CONTENT_W - 30).toInt(), 1.45f)
            canvas.save(); canvas.translate(LEFT, H - 40f - FOOTER + 16f); note.draw(canvas); canvas.restore()
            val num = paint(8.5f, muted)
            canvas.drawText("$number", W - LEFT - num.measureText("$number"), H - 40f - FOOTER + 26f, num)
            doc.finishPage(p)
            page = null
        }

        fun need(height: Float) {
            if (y + height > LIMIT) { finish(); begin() }
        }

        fun gap(h: Float) { y += h }

        fun text(s: CharSequence, p: TextPaint, x: Float = LEFT, width: Float = CONTENT_W, spacing: Float = 1.3f, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): Float {
            val l = layout(s, p, width.toInt(), spacing, align)
            need(l.height.toFloat())
            canvas.save(); canvas.translate(x, y); l.draw(canvas); canvas.restore()
            y += l.height
            return l.height.toFloat()
        }

        /** A header row and body rows; each row stays whole on one page. */
        fun table(headers: List<String>, weights: List<Float>, headColors: List<Int>, rows: List<List<String>>, boldCols: Set<Int> = emptySet()) {
            val total = weights.sum()
            val widths = weights.map { CONTENT_W * it / total }
            val pad = 5f
            fun row(cells: List<String>, paints: List<TextPaint>, top: Int) {
                val layouts = cells.mapIndexed { i, c -> layout(c, paints[i], (widths[i] - 4).toInt(), 1.2f) }
                val h = (layouts.maxOf { it.height }) + 2 * pad
                need(h)
                canvas.drawLine(LEFT, y, W - LEFT, y, stroke(top, 0.7f))
                var x = LEFT
                layouts.forEachIndexed { i, l ->
                    canvas.save(); canvas.translate(x, y + pad); l.draw(canvas); canvas.restore()
                    x += widths[i]
                }
                y += h
            }
            row(headers, headers.indices.map { paint(10.5f, headColors[it], bold = true) }, line)
            rows.forEach { r -> row(r, r.indices.map { i -> paint(10.5f, ink, bold = i in boldCols) }, line2) }
        }

        fun heading(text: String) {
            gap(18f)
            need(40f)
            text(text, paint(12f, ink, bold = true))
            gap(5f)
        }
    }

    private fun writePdf(context: Context, data: ReportData, fmt: Fmt, out: java.io.OutputStream) {
        val doc = PdfDocument()
        val colors = LightHlColors
        val pg = Pages(doc, colors)
        pg.begin()

        // header
        val top = pg.y
        val title = layout("투약·검사 기록 요약", paint(18f, pg.ink, bold = true), 300, 1.2f)
        val period = layout(data.periodText, paint(11f, pg.muted), 320, 1.3f)
        val right = layout(listOfNotNull(data.createdText, data.modelText).joinToString("\n"), paint(10f, pg.muted), 200, 1.4f, Layout.Alignment.ALIGN_OPPOSITE)
        pg.canvas.save(); pg.canvas.translate(LEFT, top); title.draw(pg.canvas); pg.canvas.restore()
        pg.canvas.save(); pg.canvas.translate(LEFT, top + title.height + 4f); period.draw(pg.canvas); pg.canvas.restore()
        pg.canvas.save(); pg.canvas.translate(W - LEFT - 200f, top + 4f); right.draw(pg.canvas); pg.canvas.restore()
        pg.y = top + maxOf(title.height + 4f + period.height, right.height + 4f) + 10f
        pg.canvas.drawLine(LEFT, pg.y, W - LEFT, pg.y, stroke(pg.ink, 2f))
        pg.gap(2f)

        data.schedule?.let { rows ->
            pg.heading("현재 일정")
            if (rows.isEmpty()) pg.text("진행 중인 반복 일정이 없어요", paint(10.5f, pg.muted))
            else pg.table(listOf("약물", "경로·용량", "간격", "시작"), listOf(1.6f, 1f, 1f, 1.2f), List(4) { pg.muted }, rows.map { listOf(it.drug, it.routeDose, it.cadence, it.start) })
        }

        if (data.chartWanted) {
            pg.heading("예상 E2 곡선과 실측값")
            val model = data.chart
            if (model == null) {
                pg.text("투약 기록이 없어서 그릴 수 있는 곡선이 없어요", paint(10.5f, pg.muted))
            } else {
                val image = renderChart(context, model, colors, fmt, widthPx = 1520, heightUnits = 150f).asAndroidBitmap()
                val h = CONTENT_W * image.height / image.width
                pg.need(h + 24f)
                pg.canvas.drawBitmap(image, null, RectF(LEFT, pg.y, LEFT + CONTENT_W, pg.y + h), Paint(Paint.FILTER_BITMAP_FLAG))
                pg.y += h + 6f
                legend(pg)
            }
        }

        data.labs?.let { rows ->
            pg.heading("검사값")
            if (rows.isEmpty()) {
                pg.text("이 기간에 E2 검사가 없어요", paint(10.5f, pg.muted))
            } else {
                pg.table(
                    listOf("채혈", "투약 후", "실측 E2", "예상 E2 (범위)", "차이", "보정"), listOf(1.1f, 0.9f, 0.9f, 1.3f, 0.7f, 1f),
                    listOf(pg.muted, pg.muted, pg.yellow, pg.teal, pg.muted, pg.muted),
                    rows.map { listOf(it.date, it.since, it.measured, it.expected, it.diff, it.status) }, boldCols = setOf(2),
                )
            }
            data.labsNote?.let { pg.gap(4f); pg.text(it, paint(10f, pg.muted)) }
        }

        // the two boxes side by side, or one alone at full width
        val boxes = listOfNotNull(data.adherence?.let { "투약 순응도" to it }, data.model?.let { "모델 상태" to it })
        if (boxes.isNotEmpty()) {
            pg.gap(16f)
            val gapW = 12f
            val boxW = (CONTENT_W - gapW * (boxes.size - 1)) / boxes.size
            val built = boxes.map { (head, lines) ->
                layout(head, paint(12f, pg.ink, bold = true), (boxW - 20).toInt(), 1.2f) to layout(lines.joinToString("\n"), paint(10.5f, pg.ink), (boxW - 20).toInt(), 1.4f)
            }
            val h = built.maxOf { it.first.height + 4 + it.second.height } + 20f
            pg.need(h)
            built.forEachIndexed { i, (head, body) ->
                val x = LEFT + i * (boxW + gapW)
                pg.canvas.drawRoundRect(RectF(x, pg.y, x + boxW, pg.y + h), 8f, 8f, stroke(pg.line, 0.8f))
                pg.canvas.save(); pg.canvas.translate(x + 10f, pg.y + 10f); head.draw(pg.canvas); pg.canvas.restore()
                pg.canvas.save(); pg.canvas.translate(x + 10f, pg.y + 14f + head.height); body.draw(pg.canvas); pg.canvas.restore()
            }
            pg.y += h
        }

        data.memos?.let { memos ->
            pg.heading("병원 메모")
            if (memos.isEmpty()) pg.text("이 기간에 쓴 메모가 없어요", paint(10.5f, pg.muted))
            memos.forEach { m ->
                pg.need(44f)
                pg.text("${m.date}  ${m.title}", paint(10.5f, pg.ink, bold = true))
                if (m.body.isNotBlank()) pg.text(m.body, paint(10f, pg.muted), spacing = 1.4f)
                m.prescription?.let { pg.text("처방 · $it", paint(10f, pg.ink)) }
                pg.gap(6f)
            }
        }

        pg.finish()
        doc.writeTo(out)
        doc.close()
    }

    /** Line, diamond, hollow diamond and tick, each with its meaning, drawn rather than typed so no font can drop them. */
    private fun legend(pg: Pages) {
        pg.need(18f)
        val c = pg.canvas
        val text = paint(9.5f, pg.muted)
        var x = LEFT
        val base = pg.y + 10f
        fun label(s: String) { c.drawText(s, x, base, text); x += text.measureText(s) + 14f }
        c.drawLine(x, base - 3f, x + 16f, base - 3f, stroke(pg.teal, 1.8f)); x += 20f
        label("예상 중앙값 · 띠 = 예상 범위")
        c.diamond(x + 4f, base - 3f, 4f, fill(pg.yMark)); x += 12f
        label("실측")
        c.diamond(x + 4f, base - 3f, 4f, stroke(pg.yMark, 1.3f)); x += 12f
        label("보정 제외 실측")
        c.drawLine(x + 2f, base - 8f, x + 2f, base, stroke(0xFF3450C9.toInt(), 1.6f)); x += 8f
        label("투약")
        pg.y += 16f
    }

    // ── share picture ─────────────────────────────────────────

    private const val PW = 1080
    private const val PH = 1920
    private const val M = 72f

    private fun drawImage(context: Context, data: ReportData, fmt: Fmt): Bitmap {
        val colors = DarkHlColors
        val bg = colors.bg.toArgb()
        val card = colors.card.toArgb()
        val ink = colors.text.toArgb()
        val muted = colors.muted.toArgb()
        val teal = colors.teal.toArgb()
        val yellow = colors.yellow.toArgb()
        val bitmap = Bitmap.createBitmap(PW, PH, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        c.drawColor(bg)
        var y = 110f
        val width = PW - 2 * M

        fun block(s: String, p: TextPaint, x: Float = M, w: Float = width, spacing: Float = 1.3f): Float {
            val l = layout(s, p, w.toInt(), spacing)
            c.save(); c.translate(x, y); l.draw(c); c.restore()
            y += l.height
            return l.height.toFloat()
        }

        block(data.periodText.substringBefore(" ("), paint(38f, muted))
        y += 6f
        block("투약·검사 기록 요약", paint(66f, ink, bold = true), spacing = 1.2f)
        y += 36f

        // chart card
        val cardPad = 30f
        val model = data.chart
        if (model != null) {
            val chart = renderChart(context, model, colors, fmt, widthPx = (width - 2 * cardPad).toInt(), heightUnits = 200f).asAndroidBitmap()
            val h = chart.height + 2 * cardPad
            c.drawRoundRect(RectF(M, y, M + width, y + h), 50f, 50f, fill(card))
            c.drawBitmap(chart, M + cardPad, y + cardPad, Paint(Paint.FILTER_BITMAP_FLAG))
            y += h + 28f
        } else {
            val l = layout("그릴 수 있는 곡선이 없어요", paint(36f, muted), (width - 2 * cardPad).toInt())
            val h = l.height + 2 * cardPad
            c.drawRoundRect(RectF(M, y, M + width, y + h), 50f, 50f, fill(card))
            c.save(); c.translate(M + cardPad, y + cardPad); l.draw(c); c.restore()
            y += h + 28f
        }

        // the newest measured value beside the expected one
        data.highlight?.let { hl ->
            val tileW = (width - 24f) / 2
            val tileH = 280f
            fun tile(x: Float, labelColor: Int, label: String, value: String, sub: String, diamond: Boolean) {
                c.drawRoundRect(RectF(x, y, x + tileW, y + tileH), 42f, 42f, fill(card))
                var tx = x + 36f
                if (diamond) { c.diamond(tx + 12f, y + 56f, 12f, fill(yellow)); tx += 34f }
                c.drawText(label, tx, y + 68f, paint(32f, labelColor, bold = true))
                c.drawText(value, x + 36f, y + 170f, paint(88f, labelColor, bold = true))
                c.drawText(sub, x + 36f, y + 230f, paint(32f, muted))
            }
            tile(M, yellow, "실측 E2 · ${hl.dateText}", hl.measured, hl.unit, diamond = true)
            tile(M + tileW + 24f, teal, "예상 · 같은 시점", hl.expected ?: "—", hl.range?.let { "범위 $it" } ?: "", diamond = false)
            y += tileH + 24f
        }

        data.adherence?.let { lines ->
            val l = layout(lines.joinToString("\n"), paint(38f, ink), (width - 72f).toInt(), 1.5f)
            val h = l.height + 72f
            c.drawRoundRect(RectF(M, y, M + width, y + h), 42f, 42f, fill(card))
            c.save(); c.translate(M + 36f, y + 36f); l.draw(c); c.restore()
            y += h + 24f
        }

        val note = layout("참고용 추정 · 임상 검증 아님 · 용량 판단은 의료진과", paint(30f, colors.disc.toArgb()), width.toInt(), 1.5f, Layout.Alignment.ALIGN_CENTER)
        c.save(); c.translate(M, PH - 100f - note.height); note.draw(c); c.restore()
        return bitmap
    }
}
