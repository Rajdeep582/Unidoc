package com.rajdeep.unidoc.core.imaging

import com.rajdeep.unidoc.core.model.QualityPreset
import com.rajdeep.unidoc.core.model.ScanFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar

class ImagingPipelineTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() = Fixtures.loadOpenCv()

        val corners = listOf(Point(260.0, 300.0), Point(1250.0, 220.0), Point(1340.0, 1720.0), Point(170.0, 1800.0))
    }

    private fun scene(): Mat = Fixtures.composite(Fixtures.page(), Fixtures.desk(), corners)

    @Test
    fun detectsPerspectiveDocument() {
        val result = DocumentDetector().detect(scene())
        assertNotNull(result)
        val truth = Quad.ordered(corners.map { Pt(it.x, it.y) })
        val err = result!!.quad.maxCornerDistance(truth)
        assertTrue("corner error $err px", err < 2000 * 0.025)
        assertTrue(result.confidence > 0.3)
    }

    @Test
    fun detectsUnderShadowAndBlur() {
        val img = Fixtures.blur(Fixtures.addShadow(scene()), 7.0)
        val result = DocumentDetector().detect(img)
        assertNotNull(result)
        assertTrue(result!!.quad.maxCornerDistance(Quad.ordered(corners.map { Pt(it.x, it.y) })) < 60)
    }

    @Test
    fun noDocumentOnEmptyDesk() {
        assertNull(DocumentDetector().detect(Fixtures.desk()))
    }

    @Test
    fun warpRestoresA4Aspect() {
        val q = Quad.ordered(corners.map { Pt(it.x, it.y) })
        val out = PerspectiveWarper.warp(scene(), q)
        val aspect = out.cols().toDouble() / out.rows()
        assertEquals(1240.0 / 1754.0, aspect, 0.06)
        assertEquals(PerspectiveWarper.MAX_SIDE.toDouble(), maxOf(out.cols(), out.rows()).toDouble(), PerspectiveWarper.MAX_SIDE.toDouble())
        val forced = PerspectiveWarper.warp(scene(), q, aspect = IdCardComposer.CARD_ASPECT)
        assertEquals(IdCardComposer.CARD_ASPECT, forced.cols().toDouble() / forced.rows(), 0.02)
    }

    @Test
    fun blackWhiteRemovesShadowAndKeepsText() {
        val shadowed = Fixtures.addShadow(Fixtures.page(), strength = 0.55)
        val bw = ImageFilters.blackWhite(shadowed)
        assertEquals(1, bw.channels())
        // Blank paper in the darkest (bottom-right) corner must come out white.
        val corner = bw.submat(Rect(bw.cols() - 120, bw.rows() - 100, 100, 80))
        assertTrue("shadow not removed: ${Core.mean(corner).`val`[0]}", Core.mean(corner).`val`[0] > 245)
        // Text bars must stay black.
        val textBar = bw.submat(Rect(110, 242, 300, 10))
        assertTrue("text lost: ${Core.mean(textBar).`val`[0]}", Core.mean(textBar).`val`[0] < 40)
        assertEquals(PageKind.TEXT_BW, PageClassifier.classify(bw, ScanFilter.BLACK_WHITE))
    }

    @Test
    fun magicWhitensPaperUnderShadow() {
        val shadowed = Fixtures.addShadow(Fixtures.page(), strength = 0.5)
        val before = Core.mean(shadowed.submat(Rect(shadowed.cols() - 120, shadowed.rows() - 100, 100, 80))).`val`[0]
        val out = ImageFilters.magic(shadowed)
        val after = Core.mean(out.submat(Rect(out.cols() - 120, out.rows() - 100, 100, 80))).`val`[0]
        assertTrue("before=$before after=$after", after > 235 && after > before + 40)
    }

    @Test
    fun allFiltersProduceValidOutput() {
        val src = Fixtures.page()
        ScanFilter.entries.forEach { f ->
            val out = ImageFilters.apply(src, f)
            assertEquals("$f size", src.size(), out.size())
            assertEquals("$f depth", CvType.CV_8U, out.depth())
        }
    }

    @Test
    fun encoderPresetsShrinkMonotonicallyAndRespectDpi() {
        val page = ImageFilters.magic(Fixtures.page(2480, 3508))
        val kind = PageClassifier.classify(page, ScanFilter.AUTO)
        val sizes = QualityPreset.entries.map { ImageEncoder.encode(page, it, kind) }
        assertTrue(sizes[0].bytes.size > sizes[1].bytes.size && sizes[1].bytes.size > sizes[2].bytes.size)
        assertEquals(ImageEncoder.targetLongSide(QualityPreset.BALANCED), sizes[1].height)
        assertEquals(200, sizes[1].dpi)
    }

    @Test
    fun bilevelPngBeatsColorJpegForTextPages() {
        val page = Fixtures.page(2480, 3508, colorHeader = false)
        val bw = ImageFilters.blackWhite(page)
        val png = ImageEncoder.encode(bw, QualityPreset.BALANCED, PageKind.TEXT_BW)
        val jpg = ImageEncoder.encode(page, QualityPreset.BALANCED, PageKind.MIXED)
        assertEquals("image/png", png.mimeType)
        assertTrue("png=${png.bytes.size} jpg=${jpg.bytes.size}", png.bytes.size < jpg.bytes.size / 2)
        assertTrue("bw page too big: ${png.bytes.size}", png.bytes.size < 120_000)
    }

    @Test
    fun classifierSeparatesGrayAndPhoto() {
        val gray = Fixtures.page(colorHeader = false)
        assertEquals(PageKind.GRAY, PageClassifier.classify(gray, ScanFilter.AUTO))
        val photo = Mat(800, 600, CvType.CV_8UC3, Scalar(30.0, 140.0, 220.0))
        assertEquals(PageKind.PHOTO, PageClassifier.classify(photo, ScanFilter.ENHANCED))
    }

    @Test
    fun bookSplitFindsGutter() {
        val left = Fixtures.page(800, 1100, seed = 1)
        val right = Fixtures.page(800, 1100, seed = 2)
        val spread = Mat()
        Core.hconcat(listOf(left, right), spread)
        Mat(spread, Rect(790, 0, 20, 1100)).setTo(Scalar(90.0, 90.0, 90.0))
        val pages = BookSplitter.split(spread)
        assertEquals(2, pages.size)
        assertEquals(800.0, pages[0].cols().toDouble(), 20.0)
        assertEquals(1, BookSplitter.split(left).size)
    }

    @Test
    fun idCardComposedAtTrueSize() {
        val card = Mat(540, 856, CvType.CV_8UC3, Scalar(200.0, 120.0, 40.0))
        val page = IdCardComposer.compose(card, card.clone(), dpi = 300)
        assertEquals(2481.0, page.cols().toDouble(), 2.0)
        assertEquals(3507.0, page.rows().toDouble(), 2.0)
    }

    @Test
    fun sharpnessDropsWithBlur() {
        val s = scene()
        val sharp = DocumentDetector.sharpness(s)
        val soft = DocumentDetector.sharpness(Fixtures.blur(s, 31.0))
        assertTrue("sharp=$sharp soft=$soft", sharp > soft * 2)
    }
}
