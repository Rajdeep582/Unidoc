package com.rajdeep.unidoc.core.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rajdeep.unidoc.core.converter.office.DocxWriter
import com.rajdeep.unidoc.core.converter.office.OfficeHtml
import com.rajdeep.unidoc.core.converter.office.XlsxWriter
import com.rajdeep.unidoc.core.converter.pdf.PdfContent
import com.rajdeep.unidoc.core.converter.pdf.PdfOrganize
import com.rajdeep.unidoc.core.converter.pdf.PdfPasswordException
import com.rajdeep.unidoc.core.converter.pdf.PdfSecure
import com.rajdeep.unidoc.core.converter.pdf.PdfStamp
import com.rajdeep.unidoc.core.converter.pdf.PdfSupport
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfToolsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var workspace: Workspace

    @Before
    fun init() {
        PDFBoxResourceLoader.init(context)
        workspace = Workspace(context, "test-${System.nanoTime()}")
    }

    /** A PDF whose page i contains "Page <i+1>" plus [extra] lines. */
    private fun pdf(name: String, pages: Int, extra: List<String> = emptyList(), rotate: Int = 0): File {
        val f = File(workspace.inDir, name)
        PDDocument().use { doc ->
            repeat(pages) { i ->
                val page = PDPage(PDRectangle.A4).apply { rotation = rotate }
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs ->
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 14f)
                    cs.newLineAtOffset(72f, 760f)
                    cs.showText("Page ${i + 1}")
                    extra.forEach { line ->
                        cs.newLineAtOffset(0f, -20f)
                        cs.showText(line)
                    }
                    cs.endText()
                }
            }
            doc.save(f)
        }
        return f
    }

    private fun ctx(inputs: List<File>, options: Map<String, String> = emptyMap()) =
        JobContext(context, workspace, inputs, inputs.map { it.name }, options) { _, _ -> }

    private fun text(f: File, password: String? = null) = PdfSupport.load(f, password).use { PDFTextStripper().getText(it) }

    private fun pages(f: File) = PdfSupport.load(f).use { it.numberOfPages }

    @Test
    fun mergeKeepsOrderAndAddsBookmarks() = runBlocking {
        val out = PdfOrganize.merge(ctx(listOf(pdf("a.pdf", 2), pdf("b.pdf", 3)))).single()
        assertEquals(5, pages(out))
        PdfSupport.load(out).use { doc ->
            val titles = generateSequence(doc.documentCatalog.documentOutline.firstChild) { it.nextSibling }.map { it.title }.toList()
            assertEquals(listOf("a", "b"), titles)
        }
    }

    @Test
    fun splitRemoveExtractOrganize() = runBlocking {
        val src = pdf("doc.pdf", 6)
        val split = PdfOrganize.split(ctx(listOf(src), mapOf("mode" to "every", "ranges" to "4")))
        assertEquals(listOf(4, 2), split.map(::pages))
        val removed = PdfOrganize.remove(ctx(listOf(src), mapOf("pages" to "2, 4-5"))).single()
        assertEquals(3, pages(removed))
        assertFalse(text(removed).contains("Page 2"))
        val extracted = PdfOrganize.extract(ctx(listOf(src), mapOf("pages" to "6, 1"))).single()
        assertTrue(text(extracted).trim().startsWith("Page 6"))
        val organized = PdfOrganize.organize(ctx(listOf(src), mapOf("order" to "3-1", "rotate" to "2:90"))).single()
        PdfSupport.load(organized).use { doc ->
            assertEquals(3, doc.numberOfPages)
            assertEquals(90, doc.getPage(1).rotation)
        }
    }

    @Test
    fun protectThenUnlock() = runBlocking {
        val protected = PdfOrganize.protect(ctx(listOf(pdf("p.pdf", 1)), mapOf("password" to "s3cret"))).single()
        assertThrows(PdfPasswordException::class.java) { PdfSupport.load(protected).close() }
        val unlocked = PdfOrganize.unlock(ctx(listOf(protected), mapOf("password" to "s3cret"))).single()
        PdfSupport.load(unlocked).use { assertFalse(it.isEncrypted) }
    }

    @Test
    fun stampsDrawTextOnRotatedPages() = runBlocking {
        val src = pdf("r.pdf", 3, rotate = 90)
        val numbered = PdfStamp.pageNumbers(ctx(listOf(src), mapOf("format" to "n_of_total"))).single()
        assertTrue(text(numbered), text(numbered).contains("3 of 3"))
        val marked = PdfStamp.watermark(ctx(listOf(src), mapOf("text" to "CONFIDENTIAL", "layout" to "tile"))).single()
        assertTrue(text(marked).filterNot { it.isWhitespace() }.contains("CONFIDENTIAL"))
        val edited = PdfStamp.addText(ctx(listOf(src), mapOf("text" to "Approved\nby Raj", "pages" to "2"))).single()
        val t = text(edited)
        assertTrue(t.contains("Approved") && t.contains("by Raj"))
    }

    @Test
    fun placedTextLandsOnChosenPage() = runBlocking {
        val src = pdf("p.pdf", 3)
        val out = PdfStamp.addText(ctx(listOf(src), mapOf("text" to "PLACED", "place" to "1,0.1000,0.5000,0.0000"))).single()
        PdfSupport.load(out).use { doc ->
            val stripper = PDFTextStripper()
            stripper.startPage = 2
            stripper.endPage = 2
            assertTrue(stripper.getText(doc).contains("PLACED"))
            stripper.startPage = 1
            stripper.endPage = 1
            assertFalse(stripper.getText(doc).contains("PLACED"))
        }
    }

    @Test
    fun customCropShrinksCropBox() = runBlocking {
        val out = PdfStamp.crop(ctx(listOf(pdf("c.pdf", 1)), mapOf("mode" to "custom", "margin" to "20"))).single()
        PdfSupport.load(out).use { assertEquals(PDRectangle.A4.width - 2 * 20 * 72 / 25.4f, it.getPage(0).cropBox.width, 0.5f) }
    }

    @Test
    fun textWordExcelExports() = runBlocking {
        val src = pdf("t.pdf", 2, listOf("Item    Qty    Price", "Pen    2    10.50"))
        assertTrue(PdfContent.toText(ctx(listOf(src))).single().readText().contains("Page 2"))
        val docx = PdfContent.toWord(ctx(listOf(src))).single()
        ZipFile(docx).use { z -> assertTrue(z.getInputStream(z.getEntry("word/document.xml")).reader().readText().contains("Page 1")) }
        val xlsx = PdfContent.toExcel(ctx(listOf(src))).single()
        ZipFile(xlsx).use { z ->
            assertEquals(2, z.entries().asSequence().count { it.name.startsWith("xl/worksheets/") })
            assertTrue(z.getInputStream(z.getEntry("xl/worksheets/sheet1.xml")).reader().readText().contains("<v>10.5</v>"))
        }
    }

    @Test
    fun pdfABasicMetadata() = runBlocking {
        val out = PdfSecure.pdfA(ctx(listOf(pdf("a.pdf", 1)), mapOf("level" to "2b"))).single()
        PdfSupport.load(out).use { doc ->
            val xmp = doc.documentCatalog.metadata.exportXMPMetadata().reader().readText()
            assertTrue(xmp.contains("<pdfaid:part>2</pdfaid:part>"))
            assertEquals(1, doc.documentCatalog.outputIntents.size)
        }
    }

    @Test
    fun compareTextReportsChanges() = runBlocking {
        val a = pdf("old.pdf", 1, listOf("The fee is 500 rupees"))
        val b = pdf("new.pdf", 1, listOf("The fee is 750 rupees"))
        val report = PdfSecure.compare(ctx(listOf(a, b), mapOf("mode" to "text"))).single()
        val t = text(report)
        assertTrue(t.contains("1 words added · 1 words removed"))
        assertTrue(t.contains("750"))
    }

    @Test
    fun redactionPatterns() {
        val hits = PdfSecure.findHits(
            "Mail raj@example.com or call +91 98765 43210. Card 4111 1111 1111 1111. Secret word.",
            listOf("secret"),
            patterns = true
        )
        val text = "Mail raj@example.com or call +91 98765 43210. Card 4111 1111 1111 1111. Secret word."
        val found = hits.map { text.substring(it.first, it.last + 1) }
        assertTrue(found.contains("raj@example.com"))
        assertTrue(found.any { it.contains("98765") })
        assertTrue(found.contains("4111 1111 1111 1111"))
        assertTrue(found.contains("Secret"))
    }

    @Test
    fun docxRoundTripsThroughOfficeHtml() {
        val f = File(workspace.inDir, "w.docx")
        DocxWriter.write(f, listOf(listOf("Hello <world> & friends", "Second"), listOf("Next page")))
        val html = OfficeHtml.toHtml(f, "docx")
        assertTrue(html.contains("Hello &lt;world&gt; &amp; friends"))
        assertTrue(html.contains("page-break-after:always"))
    }

    @Test
    fun xlsxRoundTripsThroughOfficeHtml() {
        val f = File(workspace.inDir, "s.xlsx")
        XlsxWriter.write(f, listOf("Sheet A" to listOf(listOf("Name", "Qty"), listOf("Pen", "2"))))
        val html = OfficeHtml.toHtml(f, "xlsx")
        assertTrue(html.contains("Sheet A"))
        assertTrue(html.contains("<td class=\"num\">2</td>"))
        assertEquals("AB", XlsxWriter.column(27))
        assertEquals(27, OfficeHtml.colIndex("AB12"))
    }

    @Test
    fun csvParsesQuotesAndDelimiters() {
        val rows = OfficeHtml.csvRows("name;note\n\"Raj\";\"says \"\"hi\"\"; ok\"\n\n")
        assertEquals(listOf(listOf("name", "note"), listOf("Raj", "says \"hi\"; ok")), rows)
    }

    @Test
    fun paragraphsRejoinHyphenation() {
        val p = PdfContent.paragraphs(
            "This is a long line of text that wraps over to the\nnext line and is hyphen-\nated nicely here.\n\nNew para."
        )
        assertEquals(2, p.size)
        assertTrue(p[0].contains("hyphenated"))
    }
}
