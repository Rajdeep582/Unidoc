package com.rajdeep.unidoc.core.imaging

import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/** Detect → warp each scene and compare the output against the flat source page. */
class ScanOutputTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setup() = Fixtures.loadOpenCv()
    }

    @Test
    fun outputsMatchTheFlatPage() {
        val failures = ArrayList<String>()
        Scenes.physical().forEach { s ->
            val r = DocumentDetector().detect(s.image) ?: run {
                failures += "${s.name}: missed"
                return@forEach
            }
            // The simulated camera's focal length, as a phone would report it in EXIF.
            val out = PerspectiveWarper.warp(s.image, r.quad, focalPx = maxOf(s.image.cols(), s.image.rows()) * 0.9)
            val page = s.page!!
            val aspectErr = Math.abs(out.cols().toDouble() / out.rows() / (page.cols().toDouble() / page.rows()) - 1) * 100
            val a = Mat()
            val b = Mat()
            Imgproc.cvtColor(out, a, Imgproc.COLOR_BGR2GRAY)
            Imgproc.cvtColor(page, b, Imgproc.COLOR_BGR2GRAY)
            Imgproc.resize(a, a, Size(400.0, 560.0), 0.0, 0.0, Imgproc.INTER_AREA)
            Imgproc.resize(b, b, Size(400.0, 560.0), 0.0, 0.0, Imgproc.INTER_AREA)
            val diff = Mat()
            Core.absdiff(a, b, diff)
            val mad = Core.mean(diff).`val`[0]
            println("OUTPUT ${s.name}: aspect err %.1f%%, mean |diff| %.1f".format(aspectErr, mad))
            if (aspectErr > 2.5 || mad > 25) failures += "${s.name}: aspect %.1f%% diff %.1f".format(aspectErr, mad)
            listOf(out, a, b, diff).forEach { it.release() }
        }
        assertTrue("$failures", failures.isEmpty())
    }
}
