package com.rajdeep.unidoc.core.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.rajdeep.unidoc.core.converter.image.ImageTools
import com.rajdeep.unidoc.core.converter.office.OfficeHtml
import com.rajdeep.unidoc.core.converter.office.PptxWriter
import com.rajdeep.unidoc.core.converter.pdf.PdfContent
import com.rajdeep.unidoc.core.converter.pdf.PdfOrganize
import com.rajdeep.unidoc.core.converter.pdf.PdfSecure
import com.rajdeep.unidoc.core.converter.pdf.PdfStamp
import com.rajdeep.unidoc.core.converter.pdf.PdfSupport
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every converter tool that runs without the platform PDF renderer / WebView / ML Kit. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ConverterToolsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var ws: Workspace

    @Before
    fun init() {
        PDFBoxResourceLoader.init(context)
        ws = Workspace(context, "t-${System.nanoTime()}")
    }

    private fun ctx(inputs: List<File>, options: Map<String, String> = emptyMap(), names: List<String> = inputs.map { it.name }) =
        JobContext(context, ws, inputs, names, options) { _, _ -> }

    private fun photo(w: Int = 1600, h: Int = 1200, alpha: Boolean = false): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        if (alpha) c.drawColor(Color.TRANSPARENT) else c.drawColor(Color.rgb(90, 120, 60))
        val p = Paint().apply { isAntiAlias = true }
        for (i in 0 until 40) {
            p.color = Color.rgb((i * 37) % 255, (i * 91) % 255, (i * 53) % 255)
            c.drawCircle((i * 97 % w).toFloat(), (i * 61 % h).toFloat(), 60f + i, p)
        }
        return b
    }

    private fun imageFile(name: String, fmt: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, alpha: Boolean = false): File {
        val f = File(ws.inDir, name)
        f.outputStream().use { photo(alpha = alpha).compress(fmt, 92, it) }
        return f
    }

    private fun textPdf(name: String, pages: Int = 2): File {
        val f = File(ws.inDir, name)
        PDDocument().use { doc ->
            repeat(pages) { i ->
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 14f)
                    cs.newLineAtOffset(72f, 760f)
                    cs.showText("Page ${i + 1}")
                    cs.endText()
                }
            }
            doc.save(f)
        }
        return f
    }

    /** PDF holding one big photo (what scanners and phones produce). */
    private fun photoPdf(name: String): File {
        val f = File(ws.inDir, name)
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val img: PDImageXObject = LosslessFactory.createFromImage(doc, photo(2480, 3508))
            PDPageContentStream(doc, page).use { it.drawImage(img, 0f, 0f, page.mediaBox.width, page.mediaBox.height) }
            doc.save(f)
        }
        return f
    }

    private fun text(f: File) = PdfSupport.load(f).use { PDFTextStripper().getText(it) }

    @Test
    fun rotateSelectedPagesOnly() = runBlocking {
        val out = PdfOrganize.rotate(ctx(listOf(textPdf("r.pdf", 3)), mapOf("angle" to "270", "pages" to "2"))).single()
        PdfSupport.load(out).use { d -> assertEquals(listOf(0, 270, 0), d.pages.map { it.rotation }) }
        val all = PdfOrganize.rotate(ctx(listOf(textPdf("r2.pdf", 2)), mapOf("angle" to "180"))).single()
        PdfSupport.load(all).use { d -> assertEquals(listOf(180, 180), d.pages.map { it.rotation }) }
    }

    @Test
    fun repairRewritesFile() = runBlocking {
        val src = textPdf("broken.pdf")
        // Damage the cross-reference table offsets: lenient parsing must still recover the pages.
        val bytes = src.readBytes()
        val text = String(bytes, Charsets.ISO_8859_1).replace("startxref", "startxref\n999999\n%")
        src.writeBytes(text.toByteArray(Charsets.ISO_8859_1))
        val out = PdfOrganize.repair(ctx(listOf(src))).single()
        assertEquals(2, PdfSupport.load(out).use { it.numberOfPages })
    }

    @Test
    fun compressShrinksPhotoPdfs() = runBlocking {
        val src = photoPdf("scan.pdf")
        val out = PdfContent.compress(ctx(listOf(src), mapOf("level" to "small"))).single()
        assertTrue("compressed ${out.length()} < ${src.length()}", out.length() < src.length() / 2)
        assertEquals(1, PdfSupport.load(out).use { it.numberOfPages })
    }

    @Test
    fun signWorksWithAndWithoutKeystore(): Unit = runBlocking {
        val sigFile = File(context.filesDir, "signature.json")
        sigFile.writeText("3.0\n[[0.1,0.5,0.3,0.2,0.5,0.8,0.9,0.4]]")
        val visual = PdfSecure.sign(ctx(listOf(textPdf("s.pdf")), mapOf("name" to "Rajdeep Biswas", "certify" to "false"))).single()
        assertTrue(text(visual).contains("Rajdeep Biswas"))
        // Robolectric has no AndroidKeyStore: certification fails and the visual signature is kept.
        val certified = PdfSecure.sign(ctx(listOf(textPdf("s2.pdf")), mapOf("name" to "রাজদীপ", "certify" to "true"))).single()
        assertEquals(2, PdfSupport.load(certified).use { it.numberOfPages })
        sigFile.delete()
    }

    @Test
    fun nonLatinStampsRenderAsShapedImages() = runBlocking {
        val src = textPdf("w.pdf", 1)
        val out = PdfStamp.watermark(ctx(listOf(src), mapOf("text" to "গোপনীয়", "layout" to "diagonal"))).single()
        PdfSupport.load(out).use { d ->
            val names = d.getPage(0).resources.xObjectNames.toList()
            assertTrue("watermark image present", names.isNotEmpty())
        }
        val edited = PdfStamp.addText(ctx(listOf(src), mapOf("text" to "অনুমোদিত ✓ 😀"))).single()
        assertEquals(1, PdfSupport.load(edited).use { it.numberOfPages })
        // Plain Latin stays real, searchable text.
        val latin = PdfStamp.addText(ctx(listOf(src), mapOf("text" to "Approved — café"))).single()
        assertTrue(text(latin).contains("Approved — café"))
    }

    @Test
    fun imagesToPdfSingleAndSeparate() = runBlocking {
        val imgs = listOf(imageFile("a.jpg"), imageFile("b.png", Bitmap.CompressFormat.PNG, alpha = true))
        val one = ImageTools.imagesToPdf(ctx(imgs, mapOf("page_size" to "a4", "margin" to "small", "single" to "true"))).single()
        PdfSupport.load(one).use { d ->
            assertEquals(2, d.numberOfPages)
            // Landscape photos get landscape A4.
            assertTrue(d.getPage(0).mediaBox.width > d.getPage(0).mediaBox.height)
        }
        val many = ImageTools.imagesToPdf(ctx(imgs, mapOf("single" to "false")))
        assertEquals(2, many.size)
    }

    @Test
    fun imageConvertAndCompress() = runBlocking {
        val src = imageFile("p.png", Bitmap.CompressFormat.PNG)
        listOf("jpg", "png", "webp").forEach { fmt ->
            val out = ImageTools.convert(ctx(listOf(src), mapOf("format" to fmt, "quality" to "80"))).single()
            assertTrue(out.name.endsWith(".$fmt"))
            assertTrue(BitmapFactory.decodeFile(out.path) != null)
        }
        val big = imageFile("big.jpg")
        val small = ImageTools.compress(ctx(listOf(big), mapOf("quality" to "50", "max_side" to "800"))).single()
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(small.path, o)
        assertEquals(800, maxOf(o.outWidth, o.outHeight))
        assertTrue(small.length() < big.length())
    }

    @Test
    fun textToImageAllStyles() = runBlocking {
        listOf("light", "dark", "brand").forEach { theme ->
            val out = ImageTools.textToImage(
                ctx(emptyList(), mapOf("text" to "নমস্কার Hello\nLine two", "theme" to theme, "format" to "png"))
            ).single()
            assertTrue(BitmapFactory.decodeFile(out.path).width == 1080)
        }
    }

    @Test
    fun textEncodingsAreDetected() {
        val utf8 = File(ws.inDir, "u.txt").apply { writeText("নমস্কার — café") }
        assertEquals("নমস্কার — café", ImageTools.readText(utf8))
        val latin1 = File(ws.inDir, "l.txt").apply { writeBytes("café".toByteArray(Charsets.ISO_8859_1)) }
        assertEquals("café", ImageTools.readText(latin1))
        val utf16 = File(ws.inDir, "w.txt").apply {
            writeBytes(
                byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "hi".toByteArray(Charsets.UTF_16LE)
            )
        }
        assertEquals("hi", ImageTools.readText(utf16))
    }

    @Test
    fun officeFilesWithoutExtensionAreSniffed() {
        val slides = (0 until 2).map { i ->
            val img = File(ws.inDir, "s$i.jpg")
            img.outputStream().use { photo(320, 240).compress(Bitmap.CompressFormat.JPEG, 80, it) }
            PptxWriter.Slide(img, 320, 240)
        }
        val pptx = File(ws.inDir, "deck_no_ext")
        PptxWriter.write(pptx, slides)
        assertEquals("pptx", OfficeHtml.sniff(pptx, ""))
        val html = OfficeHtml.toHtml(pptx, "")
        assertEquals(2, Regex("class=\"slide\"").findAll(html).count())
    }
}
