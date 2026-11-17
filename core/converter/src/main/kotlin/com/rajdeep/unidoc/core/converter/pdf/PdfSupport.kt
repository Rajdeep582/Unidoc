package com.rajdeep.unidoc.core.converter.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.rajdeep.unidoc.core.pdf.PdfRect
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import kotlin.math.roundToInt

class PdfPasswordException : IllegalArgumentException("This PDF is password-protected — unlock it first or enter the password")

object PdfSupport {
    fun load(file: File, password: String? = null): PDDocument = try {
        PDDocument.load(file, password ?: "", MemoryUsageSetting.setupTempFileOnly())
    } catch (e: InvalidPasswordException) {
        throw PdfPasswordException()
    }

    /** Embedded Unicode font (Noto Sans) for stamps; Devanagari text uses Noto Sans Devanagari. */
    fun font(context: Context, doc: PDDocument, text: String = ""): PDFont {
        val asset = if (text.any { it in 'ऀ'..'ॿ' }) "fonts/NotoSansDevanagari-Regular.ttf" else "fonts/NotoSans-Regular.ttf"
        return context.assets.open(asset).use { PDType0Font.load(doc, it, true) }
    }

    /**
     * True when [text] can be written as real PDF text with the bundled Latin font. Other scripts
     * (Bengali, Tamil, Devanagari conjuncts, Urdu…) need shaping that PDF text drawing doesn't do,
     * so they're rendered by Android's text engine instead — see [textImage].
     */
    fun isSimpleLatin(text: String): Boolean = text.all { it.code < 0x0250 || it.isWhitespace() || it in "‘’‚“”„–—•…€₹·™©®°×÷" }

    /** Text rendered with the system fonts (every script, correctly shaped) as a transparent image. */
    class TextImage(val image: com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject, val width: Float, val height: Float)

    fun textImage(doc: PDDocument, text: String, sizePt: Float, argb: Int, scale: Float = 4f): TextImage {
        val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = argb
            textSize = sizePt * scale
        }
        val width = text.lines().maxOf {
            android.text.Layout.getDesiredWidth(it, paint)
        }.let { kotlin.math.ceil(it).toInt() + 4 }.coerceAtLeast(4)
        val layout = android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(true)
            .build()
        val bmp = android.graphics.Bitmap.createBitmap(width, layout.height.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
        layout.draw(android.graphics.Canvas(bmp))
        val image = com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(doc, bmp)
        bmp.recycle()
        return TextImage(image, width / scale, layout.height / scale)
    }

    /** Drops characters the font can't encode (e.g. emoji) instead of failing the whole job. */
    fun encodable(font: PDFont, text: String): String {
        if (runCatching { font.encode(text) }.isSuccess) return text
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val ch = String(Character.toChars(cp))
            if (runCatching { font.encode(ch) }.isSuccess) {
                sb.append(ch)
            } else if (!Character.isWhitespace(cp)) {
                sb.append('?')
            }
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    /** Visible page size after /Rotate (what the user sees). */
    fun displaySize(page: PDPage): Pair<Float, Float> {
        val box = page.cropBox
        return if (page.rotation % 180 != 0) box.height to box.width else box.width to box.height
    }

    /**
     * Content stream appended on top of the page with a transform so callers can draw in *display*
     * coordinates (origin bottom-left of the page as the user sees it), regardless of /Rotate.
     */
    fun overlay(doc: PDDocument, page: PDPage): PDPageContentStream {
        val cs = PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)
        val box = page.cropBox
        val w = box.width
        val h = box.height
        val x0 = box.lowerLeftX
        val y0 = box.lowerLeftY
        val m = when ((page.rotation % 360 + 360) % 360) {
            90 -> Matrix(0f, 1f, -1f, 0f, w + x0, y0)
            180 -> Matrix(-1f, 0f, 0f, -1f, w + x0, h + y0)
            270 -> Matrix(0f, -1f, 1f, 0f, x0, h + y0)
            else -> Matrix(1f, 0f, 0f, 1f, x0, y0)
        }
        cs.transform(m)
        return cs
    }

    fun displayRect(page: PDPage): PdfRect = displaySize(page).let { (w, h) -> PdfRect(0f, 0f, w, h) }

    /** Renders pages with the platform PDF renderer (fast, native). Requires an unencrypted file. */
    inline fun <T> render(file: File, block: (PdfRenderer) -> T): T {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return try {
            PdfRenderer(fd).use(block)
        } catch (e: SecurityException) {
            throw PdfPasswordException()
        } finally {
            runCatching { fd.close() }
        }
    }

    fun renderPage(renderer: PdfRenderer, index: Int, dpi: Int, maxSide: Int = 4200): Bitmap = renderer.openPage(index).use { page ->
        var scale = dpi / 72f
        val longest = maxOf(page.width, page.height) * scale
        if (longest > maxSide) scale *= maxSide / longest
        val bmp = Bitmap.createBitmap(
            (page.width * scale).roundToInt().coerceAtLeast(1),
            (page.height * scale).roundToInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        bmp.eraseColor(Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        bmp
    }
}
