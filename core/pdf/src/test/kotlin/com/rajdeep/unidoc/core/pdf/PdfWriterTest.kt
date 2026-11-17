package com.rajdeep.unidoc.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.rajdeep.unidoc.core.model.PageMargin
import com.rajdeep.unidoc.core.model.PageOrientation
import com.rajdeep.unidoc.core.model.PageSize
import com.rajdeep.unidoc.core.scan.ocr.OcrLine
import com.rajdeep.unidoc.core.scan.ocr.OcrResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceGray
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Inflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PdfWriterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun init() = PDFBoxResourceLoader.init(context)

    @Test
    fun bilevelPackingIsMsbFirstWhiteIsOne() {
        // 10×1 row: black, white, black … → bits 0101010101 → 0x55, 0x40
        val row = ByteArray(10) { if (it % 2 == 1) 255.toByte() else 0 }
        val inflater = Inflater().apply { setInput(BilevelImage.packAndDeflate(row, 10, 1)) }
        val out = ByteArray(2)
        inflater.inflate(out)
        assertEquals(0x55, out[0].toInt() and 0xFF)
        assertEquals(0x40, out[1].toInt() and 0xFF)
    }

    @Test
    fun geometryFitsAndCenters() {
        val auto = PageGeometry.layout(1654, 2339, 200, PageSize.AUTO, PageOrientation.AUTO, PageMargin.NONE)
        assertEquals(595.4f, auto.pageWidth, 1f) // 8.27 in
        val a4 = PageGeometry.layout(2000, 1000, 200, PageSize.A4, PageOrientation.AUTO, PageMargin.SMALL)
        assertTrue("landscape chosen", a4.pageWidth > a4.pageHeight)
        assertEquals(a4.pageWidth - 36f, a4.image.width, 0.5f)
        assertEquals((a4.pageHeight - a4.image.height) / 2, a4.image.y, 0.5f)
    }

    /** Builds a PDF exactly the way DocumentExporter does and reads it back. */
    @Test
    fun imagesTextLayerAndEncryptionRoundTrip() {
        val file = File(tmp.root, "out.pdf")
        PDDocument().use { doc ->
            // Page 1: 1-bit Flate image + Latin/Devanagari text layer
            val w = 200
            val h = 100
            val gray = ByteArray(w * h) { 255.toByte() }
            val bw =
                PDImageXObject(
                    doc,
                    ByteArrayInputStream(BilevelImage.packAndDeflate(gray, w, h)),
                    COSName.FLATE_DECODE,
                    w,
                    h,
                    1,
                    PDDeviceGray.INSTANCE
                )
            val page = PDPage(PDRectangle(300f, 150f))
            doc.addPage(page)
            val rect = PdfRect(0f, 0f, 300f, 150f)
            PDPageContentStream(doc, page).use { cs ->
                cs.drawImage(bw, rect.x, rect.y, rect.width, rect.height)
                PdfTextLayer(context, doc).write(
                    cs,
                    OcrResult(
                        text = "",
                        lines = listOf(
                            OcrLine("Invoice 2026", listOf(0.1f, 0.1f, 0.6f, 0.25f)),
                            OcrLine("बिल राशि", listOf(0.1f, 0.5f, 0.5f, 0.65f))
                        ),
                        languages = listOf("en", "hi"),
                        imageWidth = w,
                        imageHeight = h
                    ),
                    rect
                )
            }
            // Page 2: DCT (JPEG) image
            val bmp = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(60, 120, 200)) }
            val jpeg = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
            val color = PDImageXObject(doc, ByteArrayInputStream(jpeg), COSName.DCT_DECODE, 64, 48, 8, PDDeviceRGB.INSTANCE)
            val page2 = PDPage(PDRectangle(64f, 48f))
            doc.addPage(page2)
            PDPageContentStream(doc, page2).use { it.drawImage(color, 0f, 0f, 64f, 48f) }
            doc.protect(StandardProtectionPolicy("owner", "secret", AccessPermission()).apply { encryptionKeyLength = 256 })
            doc.save(file)
        }

        var rejected = false
        try {
            PDDocument.load(file).close()
        } catch (_: InvalidPasswordException) {
            rejected = true
        }
        assertTrue("password must be required", rejected)

        PDDocument.load(file, "secret").use { doc ->
            assertEquals(2, doc.numberOfPages)
            assertTrue(doc.isEncrypted)
            val text = PDFTextStripper().getText(doc)
            assertTrue("latin text layer: $text", text.contains("Invoice 2026"))
            assertTrue("devanagari text layer: $text", text.contains("राशि") || text.contains("बिल"))
        }
    }
}
