package com.rajdeep.unidoc.core.pdf

import android.content.Context
import com.rajdeep.unidoc.core.scan.ocr.OcrResult
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode

/**
 * Invisible (render mode 3) text placed over each recognized line so a scanned PDF is searchable
 * and selectable. Noto Sans covers Latin; Noto Sans Devanagari covers Hindi/Marathi/Nepali/Sanskrit.
 * Both are subset-embedded.
 */
class PdfTextLayer(private val context: Context, private val doc: PDDocument) {
    private val latin: PDFont by lazy { load("fonts/NotoSans-Regular.ttf") }
    private val devanagari: PDFont by lazy { load("fonts/NotoSansDevanagari-Regular.ttf") }

    private fun load(asset: String): PDFont = context.assets.open(asset).use { PDType0Font.load(doc, it, true) }

    fun write(cs: PDPageContentStream, result: OcrResult, rect: PdfRect) {
        cs.saveGraphicsState()
        cs.setRenderingMode(RenderingMode.NEITHER)
        for (line in result.lines) {
            if (line.box.size != 4 || line.text.isBlank()) continue
            val (l, t, r, b) = line.box
            val font = if (line.text.any { it in 'ऀ'..'ॿ' }) devanagari else latin
            val text = encodable(font, line.text)
            if (text.isBlank()) continue
            val boxW = (r - l) * rect.width
            val boxH = (b - t) * rect.height
            val size = (boxH * 0.85f).coerceAtLeast(1f)
            val natural = font.getStringWidth(text) / 1000f * size
            if (natural <= 0f) continue
            cs.beginText()
            cs.setFont(font, size)
            cs.setHorizontalScaling((boxW / natural * 100f).coerceIn(10f, 1000f))
            cs.newLineAtOffset(rect.x + l * rect.width, rect.y + (1 - b) * rect.height + boxH * 0.15f)
            cs.showText(text)
            cs.endText()
        }
        cs.restoreGraphicsState()
    }

    /** Drops characters the font can't encode (PDFBox throws on unknown glyphs). */
    private fun encodable(font: PDFont, text: String): String = buildString {
        text.forEach { ch -> if (runCatching { font.encode(ch.toString()) }.isSuccess) append(ch) }
    }
}
