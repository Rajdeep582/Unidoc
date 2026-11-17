package com.rajdeep.unidoc.core.imaging

import com.rajdeep.unidoc.core.model.ScanFilter
import java.io.File
import org.junit.BeforeClass
import org.junit.Test
import org.opencv.core.Point
import org.opencv.imgcodecs.Imgcodecs

/** Writes the full pipeline's intermediate images to build/pipeline-preview for visual review. */
class PipelinePreviewTest {
    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() = Fixtures.loadOpenCv()
    }

    @Test
    fun writePreview() {
        val dir = File("build/pipeline-preview").apply { mkdirs() }
        val corners = listOf(Point(260.0, 300.0), Point(1250.0, 220.0), Point(1340.0, 1720.0), Point(170.0, 1800.0))
        val scene = Fixtures.blur(Fixtures.addShadow(Fixtures.composite(Fixtures.page(), Fixtures.desk(), corners)), 3.0)
        Imgcodecs.imwrite(File(dir, "0_scene.jpg").path, scene)
        val quad = DocumentDetector().detect(scene)?.quad ?: return
        val warped = PerspectiveWarper.warp(scene, quad, maxSide = 1600)
        Imgcodecs.imwrite(File(dir, "1_warped.jpg").path, warped)
        ScanFilter.entries.forEach { f -> Imgcodecs.imwrite(File(dir, "2_${f.name.lowercase()}.jpg").path, ImageFilters.apply(warped, f)) }
    }
}
