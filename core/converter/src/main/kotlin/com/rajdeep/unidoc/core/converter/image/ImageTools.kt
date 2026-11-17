package com.rajdeep.unidoc.core.converter.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.exifinterface.media.ExifInterface
import com.rajdeep.unidoc.core.converter.JobContext
import com.rajdeep.unidoc.core.converter.Workspace
import com.rajdeep.unidoc.core.model.OcrLanguage
import com.rajdeep.unidoc.core.scan.ocr.OcrEngine
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import kotlin.math.roundToInt

/** Image and plain-text tools. Text rendering uses the system fonts, so every Indian script is shaped correctly. */
object ImageTools {

    /** Decodes with EXIF orientation applied, downsampled so the long side is ≤ [maxSide]. */
    fun decode(file: File, maxSide: Int = 3200): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0) {
            if (Build.VERSION.SDK_INT < 28 &&
                file.name.endsWith(".heic", true)
            ) {
                "HEIC needs Android 9 or newer"
            } else {
                "Unsupported or damaged image"
            }
        }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val raw = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalArgumentException("Couldn't read image")
        val scaled = if (maxOf(raw.width, raw.height) > maxSide) {
            val s = maxSide.toFloat() / maxOf(raw.width, raw.height)
            Bitmap.createScaledBitmap(raw, (raw.width * s).roundToInt(), (raw.height * s).roundToInt(), true).also {
                if (it !==
                    raw
                ) {
                    raw.recycle()
                }
            }
        } else {
            raw
        }
        val orientation = runCatching {
            ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> m.apply {
                postRotate(90f)
                postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> m.apply {
                postRotate(270f)
                postScale(-1f, 1f)
            }
            else -> return scaled
        }
        return Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, m, true).also { if (it !== scaled) scaled.recycle() }
    }

    /** The JPEG itself as a PDF image when it needs no rotation and is a sane size; null otherwise. */
    private fun jpegPassthrough(doc: PDDocument, file: File): com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject? {
        if (file.extension.lowercase() !in setOf("jpg", "jpeg")) return null
        return runCatching {
            val orientation = ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            if (orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || maxOf(bounds.outWidth, bounds.outHeight) > 8000) return null
            // CMYK/YCCK JPEGs render with wrong colours in many viewers when embedded raw.
            if (bounds.outColorSpace?.let { it.model == android.graphics.ColorSpace.Model.CMYK } == true) return null
            file.inputStream().buffered().use { JPEGFactory.createFromStream(doc, it) }
        }.getOrNull()
    }

    suspend fun imagesToPdf(ctx: JobContext): List<File> {
        val margin = when (ctx.opt("margin", "none")) {
            "small" -> 18f
            "large" -> 42f
            else -> 0f
        }
        val sizeOpt = ctx.opt("page_size", "fit")
        val single = ctx.flag("single", true)
        val outputs = ArrayList<File>()
        var doc: PDDocument? = null
        ctx.inputs.forEachIndexed { i, file ->
            ctx.progress(i.toFloat() / ctx.inputs.size, "Adding image ${i + 1} of ${ctx.inputs.size}")
            if (doc == null) doc = PDDocument()
            val d = doc!!
            // Upright JPEGs (most camera photos) are embedded byte-for-byte: no re-encoding generation
            // loss, full resolution, smaller file and much faster. Everything else is decoded once.
            val image = jpegPassthrough(d, file) ?: run {
                val bmp = decode(file, 4000)
                val lossless = file.extension.lowercase() in setOf("png", "webp", "bmp", "gif") &&
                    bmp.width.toLong() * bmp.height <= 12_000_000L
                // Screenshots / graphics stay lossless (sharp text, no JPEG halos); photos use high-quality JPEG.
                (if (lossless) LosslessFactory.createFromImage(d, bmp) else JPEGFactory.createFromImage(d, bmp, 0.92f))
                    .also { bmp.recycle() }
            }
            // 150 dpi equivalent for "fit" pages.
            val iw = image.width * 72f / 150f
            val ih = image.height * 72f / 150f
            val landscape = iw > ih
            val box = when (sizeOpt) {
                "a4" -> PDRectangle.A4
                "letter" -> PDRectangle.LETTER
                else -> PDRectangle(iw + 2 * margin, ih + 2 * margin)
            }.let { if (sizeOpt != "fit" && landscape) PDRectangle(it.height, it.width) else it }
            val page = PDPage(box)
            d.addPage(page)
            val availW = box.width - 2 * margin
            val availH = box.height - 2 * margin
            val s = minOf(availW / iw, availH / ih)
            val w = iw * s
            val h = ih * s
            PDPageContentStream(d, page).use { it.drawImage(image, (box.width - w) / 2, (box.height - h) / 2, w, h) }
            if (!single) {
                outputs += ctx.out("${Workspace.baseName(ctx.names[i])}.pdf").also { d.save(it) }
                d.close()
                doc = null
            }
        }
        doc?.let { d ->
            outputs +=
                ctx.out(
                    if (ctx.inputs.size ==
                        1
                    ) {
                        "${ctx.firstBase}.pdf"
                    } else {
                        "${ctx.firstBase} + ${ctx.inputs.size - 1} images.pdf"
                    }
                ).also { d.save(it) }
            d.close()
        }
        return outputs
    }

    private fun format(ext: String): Pair<Bitmap.CompressFormat, String> = when (ext) {
        "png" -> Bitmap.CompressFormat.PNG to "png"
        "webp" -> (
            if (Build.VERSION.SDK_INT >=
                30
            ) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            ) to
            "webp"
        else -> Bitmap.CompressFormat.JPEG to "jpg"
    }

    private fun flatten(bmp: Bitmap): Bitmap {
        if (!bmp.hasAlpha()) return bmp
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(bmp, 0f, 0f, null)
        }
        bmp.recycle()
        return out
    }

    suspend fun convert(ctx: JobContext): List<File> {
        val (fmt, ext) = format(ctx.opt("format", "jpg"))
        val quality = ctx.number("quality", 85f).roundToInt().coerceIn(1, 100)
        return ctx.inputs.mapIndexed { i, file ->
            ctx.progress(i.toFloat() / ctx.inputs.size, "Converting ${ctx.names[i]}")
            var bmp = decode(file, 8000)
            if (fmt == Bitmap.CompressFormat.JPEG) bmp = flatten(bmp)
            ctx.out("${Workspace.baseName(ctx.names[i])}.$ext").also { out ->
                out.outputStream().use { bmp.compress(fmt, quality, it) }
                bmp.recycle()
            }
        }
    }

    suspend fun compress(ctx: JobContext): List<File> {
        val quality = ctx.number("quality", 75f).roundToInt().coerceIn(10, 100)
        val maxSide = ctx.number("max_side", 2000f).roundToInt()
        return ctx.inputs.mapIndexed { i, file ->
            ctx.progress(i.toFloat() / ctx.inputs.size, "Compressing ${ctx.names[i]}")
            val src = ctx.names[i].substringAfterLast('.', "jpg").lowercase()
            var bmp = decode(file, maxSide)
            // PNG without transparency compresses far better as JPEG; transparent PNG stays PNG.
            val (fmt, ext) = when {
                src == "webp" -> format("webp")
                src == "png" && bmp.hasAlpha() -> format("png")
                else -> format("jpg")
            }
            if (fmt == Bitmap.CompressFormat.JPEG) bmp = flatten(bmp)
            val out = ctx.out("${Workspace.baseName(ctx.names[i])} (compressed).$ext")
            out.outputStream().use { bmp.compress(fmt, quality, it) }
            bmp.recycle()
            if (out.length() >= file.length() && ext == src.replace("jpeg", "jpg")) file.copyTo(out, overwrite = true)
            out
        }
    }

    suspend fun toText(ctx: JobContext, ocr: OcrEngine): List<File> {
        val lang = OcrLanguage.entries.firstOrNull { it.tag == ctx.opt("lang", "en") } ?: OcrLanguage.ENGLISH
        val sb = StringBuilder()
        ctx.inputs.forEachIndexed { i, file ->
            ctx.progress(i.toFloat() / ctx.inputs.size, "Reading ${ctx.names[i]}")
            val normalized = File(ctx.workspace.root, "ocr_$i.jpg")
            decode(file, 3000).let { b ->
                normalized.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                b.recycle()
            }
            val text = ocr.recognize(normalized, setOf(OcrLanguage.ENGLISH, lang)).text
            if (ctx.inputs.size > 1) sb.append("── ").append(ctx.names[i]).append(" ──\n")
            sb.append(text.trim()).append("\n\n")
            normalized.delete()
        }
        require(sb.isNotBlank()) { "No text found in the image" }
        return listOf(ctx.out("${ctx.firstBase}.txt").also { it.writeText(sb.toString().trimEnd() + "\n") })
    }

    suspend fun textToImage(ctx: JobContext): List<File> {
        val text = ctx.opt("text")
        require(text.isNotBlank()) { "Enter some text" }
        ctx.progress(0.3f, "Rendering")
        val width = 1080
        val pad = 84
        val (bg, fg) = when (ctx.opt("theme", "light")) {
            "dark" -> Color.rgb(17, 18, 28) to Color.rgb(236, 237, 245)
            "brand" -> Color.TRANSPARENT to Color.WHITE
            else -> Color.rgb(250, 250, 252) to Color.rgb(24, 25, 38)
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = when {
                text.length < 80 -> 64f
                text.length < 300 -> 46f
                else -> 36f
            }
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width - 2 * pad)
            .setAlignment(if (text.length < 80) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.25f)
            .build()
        val height = (layout.height + 2 * pad).coerceAtLeast(if (text.length < 80) 1080 else 0)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (bg == Color.TRANSPARENT) {
            canvas.drawPaint(
                Paint().apply {
                    shader =
                        LinearGradient(
                            0f,
                            0f,
                            width.toFloat(),
                            height.toFloat(),
                            intArrayOf(Color.rgb(75, 59, 255), Color.rgb(139, 61, 255), Color.rgb(34, 211, 238)),
                            null,
                            Shader.TileMode.CLAMP
                        )
                }
            )
        } else {
            canvas.drawColor(bg)
        }
        canvas.translate(pad.toFloat(), ((height - layout.height) / 2).toFloat())
        layout.draw(canvas)
        val (fmt, ext) = format(ctx.opt("format", "png"))
        val name = text.lineSequence().first().take(32).trim().ifBlank { "Text" }
        return listOf(
            ctx.out("$name.$ext").also { out ->
                out.outputStream().use { bmp.compress(fmt, 95, it) }
                bmp.recycle()
            }
        )
    }

    /** Reads text as UTF-8 (BOM-aware), falling back to Windows-1252 for legacy files. */
    fun readText(file: File): String {
        val bytes = file.readBytes()
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, Charsets.UTF_16LE).drop(1)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, Charsets.UTF_16BE).drop(1)
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(
                CodingErrorAction.REPORT
            ).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("")
        } catch (_: CharacterCodingException) {
            String(bytes, charset("windows-1252"))
        }
    }

    /** Plain text → paginated PDF with real (selectable) text, via the platform PDF canvas. */
    suspend fun textToPdf(ctx: JobContext): List<File> {
        val (pw, ph) = if (ctx.opt("page_size", "a4") == "letter") 612 to 792 else 595 to 842
        val size = ctx.number("font_size", 11f)
        return ctx.inputs.mapIndexed { i, file ->
            ctx.progress(i.toFloat() / ctx.inputs.size, "Typesetting ${ctx.names[i]}")
            ctx.out("${Workspace.baseName(ctx.names[i])}.pdf").also { writeTextPdf(readText(file), it, pw, ph, size) }
        }
    }

    fun writeTextPdf(text: String, out: File, pw: Int, ph: Int, size: Float, margin: Int = 54) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(20, 20, 28)
            textSize = size
        }
        val body = text.replace("\t", "    ").ifEmpty { " " }
        val layout = StaticLayout.Builder.obtain(body, 0, body.length, paint, pw - 2 * margin).setLineSpacing(0f, 1.2f).build()
        val usable = ph - 2 * margin
        val pdf = PdfDocument()
        try {
            var line = 0
            var pageNo = 1
            while (line < layout.lineCount) {
                val top = layout.getLineTop(line)
                var last = line
                while (last + 1 < layout.lineCount && layout.getLineBottom(last + 1) - top <= usable) last++
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(pw, ph, pageNo++).create())
                val c = page.canvas
                c.save()
                c.clipRect(margin, margin, pw - margin, margin + layout.getLineBottom(last) - top)
                c.translate(margin.toFloat(), (margin - top).toFloat())
                layout.draw(c)
                c.restore()
                pdf.finishPage(page)
                line = last + 1
            }
            out.outputStream().use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
    }
}
