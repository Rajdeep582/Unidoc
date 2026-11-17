package com.rajdeep.unidoc.core.converter.pdf

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont

/**
 * Minimal word-wrapping text flow onto new pages. Words may carry a background highlight and a
 * strike-through; "\n" tokens force a line break.
 */
class FlowWriter(
    private val doc: PDDocument,
    private val font: PDFont,
    private val size: Float,
    private val pageSize: PDRectangle = PDRectangle.A4,
    private val margin: Float = 54f
) {
    data class Style(
        val highlight: FloatArray? = null,
        val strike: Boolean = false,
        val color: FloatArray = floatArrayOf(0.1f, 0.1f, 0.14f)
    )

    private var cs: PDPageContentStream? = null
    private var x = margin
    private var y = 0f
    private val lineH get() = size * 1.45f
    private val right get() = pageSize.width - margin
    val pageCount get() = doc.numberOfPages

    private fun newPage() {
        cs?.close()
        val page = PDPage(pageSize)
        doc.addPage(page)
        cs = PDPageContentStream(doc, page)
        x = margin
        y = pageSize.height - margin - size
    }

    private fun stream(): PDPageContentStream {
        if (cs == null) newPage()
        return cs!!
    }

    fun newLine(extra: Float = 0f) {
        stream()
        x = margin
        y -= lineH + extra
        if (y < margin) newPage()
    }

    fun heading(text: String, headingSize: Float, color: FloatArray = floatArrayOf(0.1f, 0.1f, 0.2f)) {
        val s = stream()
        val t = PdfSupport.encodable(font, text)
        s.beginText()
        s.setFont(font, headingSize)
        s.setNonStrokingColor(color[0], color[1], color[2])
        s.newLineAtOffset(margin, y - (headingSize - size))
        s.showText(t)
        s.endText()
        y -= headingSize - size
        newLine(4f)
    }

    fun word(raw: String, style: Style = Style()) {
        if (raw == "\n") {
            newLine()
            return
        }
        val text = PdfSupport.encodable(font, raw)
        if (text.isEmpty()) return
        var s = stream()
        val w = font.getStringWidth(text) / 1000f * size
        val space = font.getStringWidth(" ") / 1000f * size
        if (x > margin && x + w > right) {
            newLine()
            s = stream()
        }
        style.highlight?.let { c ->
            s.setNonStrokingColor(c[0], c[1], c[2])
            s.addRect(x - 1f, y - size * 0.28f, w + 2f, size * 1.25f)
            s.fill()
        }
        s.beginText()
        s.setFont(font, size)
        s.setNonStrokingColor(style.color[0], style.color[1], style.color[2])
        s.newLineAtOffset(x, y)
        s.showText(text)
        s.endText()
        if (style.strike) {
            s.setStrokingColor(style.color[0], style.color[1], style.color[2])
            s.setLineWidth(0.6f)
            s.moveTo(x, y + size * 0.3f)
            s.lineTo(x + w, y + size * 0.3f)
            s.stroke()
        }
        x += w + space
    }

    /** Writes plain text preserving line breaks (wrapping long lines). */
    fun text(body: String) {
        body.lines().forEach { line ->
            if (line.isBlank()) {
                newLine()
            } else {
                line.split(' ').filter { it.isNotEmpty() }.forEach { word(it) }
                newLine()
            }
        }
    }

    fun close() {
        cs?.close()
        cs = null
    }
}
