package com.rajdeep.unidoc.core.converter.pdf

import android.graphics.Bitmap
import com.rajdeep.unidoc.core.converter.JobContext
import com.rajdeep.unidoc.core.converter.office.DocxWriter
import com.rajdeep.unidoc.core.converter.office.PptxWriter
import com.rajdeep.unidoc.core.converter.office.XlsxWriter
import com.rajdeep.unidoc.core.model.OcrLanguage
import com.rajdeep.unidoc.core.pdf.PdfTextLayer
import com.rajdeep.unidoc.core.scan.ocr.OcrEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlin.math.roundToInt

/** Tools that read or rewrite page content: text, images, OCR, compression, Office exports. */
object PdfContent {

    suspend fun toText(ctx: JobContext): List<File> = PdfSupport.load(ctx.inputs.first()).use { doc ->
        ctx.progress(0.4f, "Extracting text")
        val stripper = PDFTextStripper().apply {
            sortByPosition = ctx.flag("layout")
            pageEnd = "\n\n"
        }
        val text = stripper.getText(doc)
        require(text.isNotBlank()) { "No text found — this looks like a scanned PDF. Run OCR PDF first." }
        ctx.out("${ctx.firstBase}.txt").also { it.writeText(text) }.let(::listOf)
    }

    suspend fun toImages(ctx: JobContext): List<File> {
        val png = ctx.opt("format", "jpg") == "png"
        val dpi = ctx.number("dpi", 150f).toInt()
        return PdfSupport.render(ctx.inputs.first()) { r ->
            (0 until r.pageCount).map { i ->
                ctx.progress(i.toFloat() / r.pageCount, "Rendering page ${i + 1} of ${r.pageCount}")
                val bmp = PdfSupport.renderPage(r, i, dpi)
                val out = ctx.out("${ctx.firstBase} - ${(i + 1).toString().padStart(2, '0')}.${if (png) "png" else "jpg"}")
                out.outputStream().use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, it) }
                bmp.recycle()
                out
            }
        }
    }

    /**
     * Adds an invisible text layer to pages without text (on-device ML Kit: Latin + Devanagari).
     * Pages keep their original content; only text is added.
     */
    suspend fun ocr(ctx: JobContext, ocr: OcrEngine): List<File> {
        val file = ctx.inputs.first()
        val lang = OcrLanguage.entries.firstOrNull { it.tag == ctx.opt("lang", "en") } ?: OcrLanguage.ENGLISH
        val skipText = ctx.flag("skip_text", true)
        val tmp = File(ctx.workspace.root, "ocr_page.png")
        return PdfSupport.load(file).use { doc ->
            val stripper = PDFTextStripper()
            PdfSupport.render(file) { renderer ->
                val layer = PdfTextLayer(ctx.android, doc)
                for (i in 0 until doc.numberOfPages) {
                    ctx.progress(i.toFloat() / doc.numberOfPages, "Reading page ${i + 1} of ${doc.numberOfPages}")
                    if (skipText) {
                        stripper.startPage = i + 1
                        stripper.endPage = i + 1
                        if (stripper.getText(doc).trim().length > 20) continue
                    }
                    val bmp = PdfSupport.renderPage(renderer, i, 220, maxSide = 3000)
                    tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                    val result = ocr.recognize(tmp, setOf(OcrLanguage.ENGLISH, lang))
                    val page = doc.getPage(i)
                    PdfSupport.overlay(doc, page).use { cs -> layer.write(cs, result, PdfSupport.displayRect(page)) }
                }
            }
            tmp.delete()
            ctx.progress(0.97f, "Saving")
            ctx.out("${ctx.firstBase} (searchable).pdf").also { doc.save(it) }.let(::listOf)
        }
    }

    /**
     * Re-encodes embedded photos/scans: downsample to the level's DPI (by placed size) and JPEG
     * re-compress, keeping the original when the new one isn't smaller. Vector text is untouched.
     */
    suspend fun compress(ctx: JobContext): List<File> {
        val (targetDpi, quality) = when (ctx.opt("level", "balanced")) {
            "high" -> 200 to 0.82f
            "small" -> 100 to 0.5f
            else -> 144 to 0.65f
        }
        return ctx.inputs.mapIndexed { idx, file ->
            PdfSupport.load(file).use { doc ->
                val seen = HashSet<COSName>()
                doc.pages.forEachIndexed { i, page ->
                    ctx.progress(
                        (idx + i.toFloat() / doc.numberOfPages) / ctx.inputs.size,
                        "Optimizing images · page ${i + 1} of ${doc.numberOfPages}"
                    )
                    val pageInches = maxOf(page.mediaBox.width, page.mediaBox.height) / 72f
                    page.resources?.let { recompress(doc, it, pageInches, targetDpi, quality, seen, 0) }
                }
                doc.documentCatalog.cosObject.removeItem(COSName.getPDFName("PieceInfo"))
                ctx.out("${ctx.names[idx].substringBeforeLast('.')} (compressed).pdf").also { doc.save(it) }
            }
        }
    }

    private fun recompress(
        doc: PDDocument,
        res: PDResources,
        pageInches: Float,
        dpi: Int,
        quality: Float,
        seen: HashSet<COSName>,
        depth: Int
    ) {
        if (depth > 3) return
        for (name in res.xObjectNames.toList()) {
            val xo = runCatching { res.getXObject(name) }.getOrNull() ?: continue
            when (xo) {
                is PDFormXObject -> xo.resources?.let { recompress(doc, it, pageInches, dpi, quality, seen, depth + 1) }
                is PDImageXObject -> {
                    if (xo.isStencil || xo.bitsPerComponent < 8 || xo.width * xo.height < 160_000) continue
                    val before = xo.cosObject.length
                    val bmp = runCatching { xo.image }.getOrNull() ?: continue
                    val maxSide = (pageInches * dpi).roundToInt()
                    val scaled = if (maxOf(bmp.width, bmp.height) > maxSide) {
                        val s = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
                        Bitmap.createScaledBitmap(
                            bmp,
                            (bmp.width * s).roundToInt().coerceAtLeast(1),
                            (bmp.height * s).roundToInt().coerceAtLeast(1),
                            true
                        )
                    } else {
                        bmp
                    }
                    val replacement = runCatching { JPEGFactory.createFromImage(doc, scaled, quality) }.getOrNull()
                    if (replacement != null && replacement.cosObject.length < before * 0.95) res.put(name, replacement)
                    if (scaled !== bmp) scaled.recycle()
                    bmp.recycle()
                }
            }
        }
    }

    /** Paragraph-preserving text export (offline fallback; Cloud pdf2docx keeps full layout). */
    suspend fun toWord(ctx: JobContext): List<File> = PdfSupport.load(ctx.inputs.first()).use { doc ->
        val stripper = PDFTextStripper().apply { sortByPosition = true }
        val pages = (1..doc.numberOfPages).map { p ->
            ctx.progress(p.toFloat() / doc.numberOfPages * 0.9f, "Reading page $p of ${doc.numberOfPages}")
            stripper.startPage = p
            stripper.endPage = p
            paragraphs(stripper.getText(doc))
        }
        require(pages.any { it.isNotEmpty() }) { "No text found — run OCR PDF first, or use Cloud for scanned files" }
        ctx.out("${ctx.firstBase}.docx").also { DocxWriter.write(it, pages) }.let(::listOf)
    }

    /** Rows from text lines; columns split on runs of 2+ spaces or tabs (offline fallback). */
    suspend fun toExcel(ctx: JobContext): List<File> = PdfSupport.load(ctx.inputs.first()).use { doc ->
        val stripper = PDFTextStripper().apply { sortByPosition = true }
        val sheets = (1..doc.numberOfPages).map { p ->
            ctx.progress(p.toFloat() / doc.numberOfPages * 0.9f, "Reading page $p")
            stripper.startPage = p
            stripper.endPage = p
            "Page $p" to stripper.getText(doc).lines().filter { it.isNotBlank() }.map { line ->
                line.trim().split(Regex("\t|\\s{2,}")).map { it.trim() }
            }
        }.filter { it.second.isNotEmpty() }
        require(sheets.isNotEmpty()) { "No text found — run OCR PDF first, or use Cloud for scanned files" }
        ctx.out("${ctx.firstBase}.xlsx").also { XlsxWriter.write(it, sheets) }.let(::listOf)
    }

    /** One slide per page (high-resolution image, exact look). */
    suspend fun toPowerPoint(ctx: JobContext): List<File> {
        val slides = PdfSupport.render(ctx.inputs.first()) { r ->
            (0 until r.pageCount).map { i ->
                ctx.progress(i.toFloat() / r.pageCount * 0.9f, "Rendering slide ${i + 1} of ${r.pageCount}")
                val bmp = PdfSupport.renderPage(r, i, 150, maxSide = 2400)
                val f = File(ctx.workspace.root, "slide_$i.jpg")
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                PptxWriter.Slide(f, bmp.width, bmp.height).also { bmp.recycle() }
            }
        }
        return ctx.out("${ctx.firstBase}.pptx").also { PptxWriter.write(it, slides) }.let(::listOf)
    }

    /** Groups lines into paragraphs (blank line or short line ends a paragraph). */
    internal fun paragraphs(text: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        val lines = text.lines()
        val typical = lines.map { it.trim().length }.filter { it > 0 }.sorted().let { if (it.isEmpty()) 0 else it[it.size * 3 / 4] }
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) {
                if (current.isNotEmpty()) result += current.toString().also { current.clear() }
                continue
            }
            when {
                current.isEmpty() -> Unit
                current.endsWith("-") -> current.setLength(current.length - 1) // re-join hyphenated words
                else -> current.append(' ')
            }
            current.append(line)
            val sentenceEnd = line.last() in ".!?:"
            val continues = line.endsWith("-") || line.endsWith(",")
            if (sentenceEnd && line.length < typical * 0.9 || !continues && line.length < typical * 0.6) {
                result += current.toString()
                current.clear()
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }
}
