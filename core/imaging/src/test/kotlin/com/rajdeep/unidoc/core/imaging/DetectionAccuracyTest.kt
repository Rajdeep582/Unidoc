package com.rajdeep.unidoc.core.imaging

import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class DetectionAccuracyTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setup() = Fixtures.loadOpenCv()
    }

    @Test
    fun detectsAllScenesPrecisely() {
        val detector = DocumentDetector()
        val failures = ArrayList<String>()
        (Scenes.all() + Scenes.physical()).forEach { s ->
            val t0 = System.nanoTime()
            val r = detector.detect(s.image)
            val ms = (System.nanoTime() - t0) / 1e6
            val line = if (s.corners == null) {
                "${s.name}: ${if (r == null) "none (ok)" else "FALSE POSITIVE ${r.quad}"}"
            } else if (r == null) {
                "${s.name}: MISSED"
            } else {
                "${s.name}: err %.2f%% conf %.2f".format(cornerError(r.quad, s.corners, s.image.cols(), s.image.rows()), r.confidence)
            }
            println("DETECT ${"%.0f".format(ms)}ms  $line")
            val ok = if (s.corners == null) r == null else r != null && cornerError(r.quad, s.corners, s.image.cols(), s.image.rows()) < 0.6
            if (!ok) failures += line
            s.image.release()
        }
        assertTrue("Failed: $failures", failures.isEmpty())
    }
}
