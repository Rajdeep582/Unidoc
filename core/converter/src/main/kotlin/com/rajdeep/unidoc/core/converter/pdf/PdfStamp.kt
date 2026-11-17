package com.rajdeep.unidoc.core.converter.pdf

import android.graphics.Bitmap
import android.graphics.Color
import com.rajdeep.unidoc.core.converter.JobContext
import com.rajdeep.unidoc.core.converter.PageRanges
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import kotlin.math.atan2
import kotlin.math.hypot

/** Tools that draw on top of existing pages: page numbers, watermark, text stamp, crop. */
object PdfStamp {

    suspend fun pageNumbers(ctx: JobContext): List<File> = PdfSupport.load(ctx.inputs.first()).use { doc ->
        val font = PdfSupport.font(ctx.android, doc)
        val start = ctx.number("start", 1f).toInt()
        val total = doc.numberOfPages
        val position = ctx.opt("position", "bottom_center")
        doc.pages.forEachIndexed { i, page ->
            ctx.progress(i.toFloat() / total, "Numbering page ${i + 1} of $total")
            val n = start + i
            val label = when (ctx.opt("format", "n")) {
                "page_n" -> "Page $n"
                "n_of_total" -> "$n of ${start + total - 1}"
                else -> "$n"
            }
            val (w, h) = PdfSupport.displaySize(page)
            val size = (minOf(w, h) * 0.018f).coerceIn(8f, 14f)
            val textW = font.getStringWidth(label) / 1000f * size
            val margin = 28f
            val x = when {
                position.endsWith("left") -> margin
                position.endsWith("right") -> w - margin - textW
                else -> (w - textW) / 2
            }
            val y = if (position.startsWith("top")) h - margin - size else margin
            PdfSupport.overlay(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(font, size)
                cs.setNonStrokingColor(0.25f, 0.25f, 0.3f)
                cs.newLineAtOffset(x, y)
                cs.showText(label)
                cs.endText()
            }
        }
        ctx.out("${ctx.firstBase} (numbered).pdf").also { doc.save(it) }.let(::listOf)
    }

    suspend fun watermark(ctx: JobContext): List<File> = PdfSupport.load(ctx.inputs.first()).use { doc ->
        val text = ctx.opt("text")
        require(text.isNotBlank()) { "Enter watermark text" }
        val latin = PdfSupport.isSimpleLatin(text)
        val font = PdfSupport.font(ctx.android, doc, text)
        // Non-Latin scripts: one shaped image at 48 pt, scaled per stamp.
        val art = if (latin) null else PdfSupport.textImage(doc, text, 48f, android.graphics.Color.rgb(140, 143, 158))
        val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = ctx.number("opacity", 0.2f).coerceIn(0.02f, 1f) }
        val layout = ctx.opt("layout", "diagonal")
        doc.pages.forEachIndexed { i, page ->
            ctx.progress(i.toFloat() / doc.numberOfPages, "Stamping page ${i + 1}")
            val (w, h) = PdfSupport.displaySize(page)
            PdfSupport.overlay(doc, page).use { cs ->
                cs.setGraphicsStateParameters(gs)
                cs.setNonStrokingColor(0.55f, 0.56f, 0.62f)
                val unit = art?.let { it.width / 48f } ?: (font.getStringWidth(PdfSupport.encodable(font, text)) / 1000f)
                fun stamp(cx: Float, cy: Float, size: Float, angle: Double) {
                    if (art != null) {
                        val k = size / 48f
                        cs.saveGraphicsState()
                        cs.transform(Matrix.getRotateInstance(angle, cx, cy))
                        cs.drawImage(art.image, -art.width * k / 2, -art.height * k / 2, art.width * k, art.height * k)
                        cs.restoreGraphicsState()
                        return
                    }
                    cs.beginText()
                    cs.setFont(font, size)
                    val m = Matrix.getRotateInstance(angle, cx, cy)
                    m.translate(-unit * size / 2, -size * 0.35f)
                    cs.setTextMatrix(m)
                    cs.showText(PdfSupport.encodable(font, text))
                    cs.endText()
                }
                when (layout) {
                    "center" -> stamp(w / 2, h / 2, (w * 0.6f / unit).coerceAtMost(96f), 0.0)
                    "tile" -> {
                        val size = (w * 0.22f / unit).coerceIn(10f, 40f)
                        var y = 60f
                        var row = 0
                        while (y < h) {
                            var x = if (row % 2 == 0) 70f else 70f + w * 0.2f
                            while (x < w) {
                                stamp(x, y, size, Math.PI / 6)
                                x += w * 0.4f
                            }
                            y += h * 0.14f
                            row++
                        }
                    }
                    else -> stamp(w / 2, h / 2, (hypot(w, h) * 0.65f / unit).coerceAtMost(140f), atan2(h, w).toDouble())
                }
            }
        }
        ctx.out("${ctx.firstBase} (watermarked).pdf").also { doc.save(it) }.let(::listOf)
    }

    /** Text stamp (Edit PDF until the visual editor lands): places [text] on selected pages. */
    suspend fun addText(ctx: JobContext): List<File> = PdfSupport.load(ctx.inputs.first()).use { doc ->
        val text = ctx.opt("text")
        require(text.isNotBlank()) { "Enter the text to add" }
        val font = PdfSupport.font(ctx.android, doc, text)
        val size = ctx.number("size", 14f)
        val art = if (PdfSupport.isSimpleLatin(
                text
            )
        ) {
            null
        } else {
            PdfSupport.textImage(doc, text, size, android.graphics.Color.rgb(18, 18, 28))
        }
        val place = ctx.placement
        val pages =
            place?.let { listOf(it[0].toInt().coerceIn(0, doc.numberOfPages - 1)) } ?: PageRanges.parse(ctx.opt("pages"), doc.numberOfPages)
        val position = ctx.opt("position", "top_left")
        pages.forEach { p ->
            val page = doc.getPage(p)
            val (w, h) = PdfSupport.displaySize(page)
            val lines = text.lines()
            val textW = art?.width ?: (lines.maxOf { font.getStringWidth(PdfSupport.encodable(font, it)) } / 1000f * size)
            val x = when {
                place != null -> place[1] * w
                position.endsWith("left") -> 36f
                position.endsWith("right") -> w - 36f - textW
                else -> (w - textW) / 2
            }
            // First baseline; with a placement, (x, y) is the top-left of the text block.
            var y = when {
                place != null -> h - place[2] * h - size
                position.startsWith("top") -> h - 36f - size
                else -> 36f + (lines.size - 1) * size * 1.25f
            }
            PdfSupport.overlay(doc, page).use { cs ->
                if (art != null) {
                    // Image top aligns with where the first line's cap height would be.
                    val top = y + size
                    cs.drawImage(art.image, x, top - art.height, art.width, art.height)
                    return@use
                }
                cs.setNonStrokingColor(0.07f, 0.07f, 0.11f)
                lines.forEach { line ->
                    cs.beginText()
                    cs.setFont(font, size)
                    cs.newLineAtOffset(x, y)
                    cs.showText(PdfSupport.encodable(font, line))
                    cs.endText()
                    y -= size * 1.25f
                }
            }
        }
        ctx.out("${ctx.firstBase} (edited).pdf").also { doc.save(it) }.let(::listOf)
    }

    /** Custom margins, or auto-trim to the visible content (rendered at low resolution). */
    suspend fun crop(ctx: JobContext): List<File> {
        val file = ctx.inputs.first()
        val auto = ctx.opt("mode", "auto") == "auto"
        val marginPt = ctx.number("margin", 10f) * 72f / 25.4f
        val contentBoxes: List<FloatArray?> = if (auto) {
            PdfSupport.render(file) { r ->
                (0 until r.pageCount).map { i ->
                    ctx.progress(i.toFloat() / r.pageCount * 0.8f, "Finding content on page ${i + 1}")
                    val bmp = PdfSupport.renderPage(r, i, 50)
                    contentBounds(bmp).also { bmp.recycle() }
                }
            }
        } else {
            emptyList()
        }
        return PdfSupport.load(file).use { doc ->
            doc.pages.forEachIndexed { i, page ->
                val box = page.cropBox
                val rect = if (auto) {
                    val b = contentBoxes.getOrNull(i) ?: return@forEachIndexed
                    // Bounds are fractions of the *displayed* page; map back for /Rotate.
                    val (l, t, r, bt) = unrotate(b, page.rotation)
                    PDRectangle(
                        (box.lowerLeftX + l * box.width - marginPt).coerceAtLeast(box.lowerLeftX),
                        (box.lowerLeftY + (1 - bt) * box.height - marginPt).coerceAtLeast(box.lowerLeftY),
                        ((r - l) * box.width + 2 * marginPt).coerceAtMost(box.width),
                        ((bt - t) * box.height + 2 * marginPt).coerceAtMost(box.height)
                    )
                } else {
                    PDRectangle(
                        box.lowerLeftX + marginPt,
                        box.lowerLeftY + marginPt,
                        (box.width - 2 * marginPt).coerceAtLeast(36f),
                        (
                            box.height -
                                2 * marginPt
                            ).coerceAtLeast(36f)
                    )
                }
                page.cropBox = rect
            }
            ctx.progress(0.95f, "Saving")
            ctx.out("${ctx.firstBase} (cropped).pdf").also { doc.save(it) }.let(::listOf)
        }
    }

    /** Non-white bounds as fractions [left, top, right, bottom] of the bitmap, or null for blank pages. */
    internal fun contentBounds(bmp: Bitmap, threshold: Int = 235): FloatArray? {
        val w = bmp.width
        val h = bmp.height
        val row = IntArray(w)
        var minX = w
        var maxX = -1
        var minY = h
        var maxY = -1
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                val c = row[x]
                if (Color.red(c) < threshold || Color.green(c) < threshold || Color.blue(c) < threshold) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0) return null
        return floatArrayOf(minX.toFloat() / w, minY.toFloat() / h, (maxX + 1f) / w, (maxY + 1f) / h)
    }

    /** Converts display-space fractional bounds to unrotated page-space fractions. */
    private fun unrotate(b: FloatArray, rotation: Int): FloatArray {
        val (l, t, r, bt) = b
        return when ((rotation % 360 + 360) % 360) {
            90 -> floatArrayOf(t, 1 - r, bt, 1 - l)
            180 -> floatArrayOf(1 - r, 1 - bt, 1 - l, 1 - t)
            270 -> floatArrayOf(1 - bt, l, 1 - t, r)
            else -> b
        }
    }
}
