package com.rajdeep.unidoc.core.imaging

import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Corners of a real rectangle seen by a pinhole camera, to check foreshortening is undone. */
class AspectEstimateTest {
    private fun project(
        widthM: Double,
        heightM: Double,
        tiltXDeg: Double,
        tiltYDeg: Double,
        dist: Double,
        f: Double,
        imgW: Int,
        imgH: Int
    ): Quad {
        val ax = Math.toRadians(tiltXDeg)
        val ay = Math.toRadians(tiltYDeg)
        fun p(x: Double, y: Double): Pt {
            // Rotate about X then Y, push away from the camera.
            val y1 = y * cos(ax)
            val z1 = y * sin(ax)
            val x2 = x * cos(ay) + z1 * sin(ay)
            val z2 = -x * sin(ay) + z1 * cos(ay) + dist
            return Pt(imgW / 2.0 + f * x2 / z2, imgH / 2.0 + f * y1 / z2)
        }
        val hw = widthM / 2
        val hh = heightM / 2
        return Quad(p(-hw, -hh), p(hw, -hh), p(hw, hh), p(-hw, hh))
    }

    @Test
    fun recoversA4AspectUnderStrongTilt() {
        val truth = 0.21 / 0.297
        listOf(25.0 to 0.0, 40.0 to 10.0, 15.0 to 30.0, 50.0 to -15.0).forEach { (tx, ty) ->
            val q = project(0.21, 0.297, tx, ty, 0.42, 3000.0, 3000, 4000)
            val est = PerspectiveWarper.estimateAspect(q, 3000, 4000)
            val naive = q.averageWidth() / q.averageHeight()
            assertEquals("tilt $tx/$ty", truth, est, truth * 0.02)
            assertTrue("estimate beats naive at $tx/$ty", kotlin.math.abs(est - truth) <= kotlin.math.abs(naive - truth))
        }
    }

    @Test
    fun frontalShotFallsBackToEdges() {
        val q = Quad(Pt(100.0, 100.0), Pt(900.0, 100.0), Pt(900.0, 1231.0), Pt(100.0, 1231.0))
        assertEquals(800.0 / 1131.0, PerspectiveWarper.estimateAspect(q, 1000, 1400), 1e-6)
    }
}

class OrderingTest {
    @Test
    fun tiltedQuadIsNeverTwisted() {
        // ~40° rotated rectangle: independent x+y / x−y picks used to cross the outline.
        val pts = listOf(Pt(500.0, 100.0), Pt(900.0, 450.0), Pt(560.0, 880.0), Pt(160.0, 530.0))
        val q = Quad.ordered(pts.shuffled(java.util.Random(3)))
        assertTrue(q.isConvex())
        assertEquals(pts.toSet(), q.points.toSet())
    }
}

class FocalHintTest {
    @Test
    fun singleAxisTiltUsesExifFocalLength() {
        // Tilted only forward: one vanishing point, so the focal length must come from EXIF.
        val f = 3600.0
        val pts = Scenes.cameraCorners(0.707, 32.0, 0.0, 0.0, 3000, 4000, fFactor = f / 4000)
        val q = Quad(Pt(pts[0].x, pts[0].y), Pt(pts[1].x, pts[1].y), Pt(pts[2].x, pts[2].y), Pt(pts[3].x, pts[3].y))
        assertEquals(0.707, PerspectiveWarper.estimateAspect(q, 3000, 4000, focalPx = f), 0.707 * 0.01)
        assertEquals(26.0 / 43.27 * 5000, PerspectiveWarper.focalPxFrom35mm(26.0, 3000, 4000), 1e-6)
    }
}
