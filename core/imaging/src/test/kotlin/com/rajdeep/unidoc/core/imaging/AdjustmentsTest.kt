package com.rajdeep.unidoc.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar

class AdjustmentsTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() = Fixtures.loadOpenCv()
    }

    private fun mean(m: Mat) = Core.mean(m).`val`[0]

    @Test
    fun brightnessAndContrastMoveLevels() {
        val mid = Mat(50, 50, CvType.CV_8UC3, Scalar(128.0, 128.0, 128.0))
        assertTrue(mean(ImageFilters.adjust(mid, 0.5f, 0f, 0f, 0f)) > 150)
        assertTrue(mean(ImageFilters.adjust(mid, -0.5f, 0f, 0f, 0f)) < 106)
        val split = Mat(50, 100, CvType.CV_8UC3, Scalar(100.0, 100.0, 100.0))
        Mat(split, Rect(50, 0, 50, 50)).setTo(Scalar(160.0, 160.0, 160.0))
        val high = ImageFilters.adjust(split, 0f, 0.8f, 0f, 0f)
        val gap = mean(high.submat(Rect(50, 0, 50, 50))) - mean(high.submat(Rect(0, 0, 50, 50)))
        assertTrue("contrast gap $gap", gap > 80)
        assertEquals(128.0, mean(ImageFilters.adjust(mid, 0f, 0f, 0f, 0f)), 1.0)
    }

    @Test
    fun flipAndStraightenKeepSize() {
        val img = Fixtures.page(400, 600)
        val flipped = PerspectiveWarper.flip(img)
        assertEquals(img.size(), flipped.size())
        val tilted = PerspectiveWarper.straighten(img, 7.0)
        assertEquals(img.size(), tilted.size())
        // Header band moves when tilted; content stays mostly white-ish paper.
        assertTrue(mean(tilted) > 180)
    }
}
