package com.rajdeep.unidoc.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar

class BentWarpTest {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setup() = Fixtures.loadOpenCv()
    }

    @Test
    fun unitSquareMapHitsCornersAndPerspectiveMidpoints() {
        val q = Quad(Pt(10.0, 20.0), Pt(310.0, 5.0), Pt(330.0, 400.0), Pt(0.0, 380.0))
        assertEquals(q.tl.x, q.at(0.0, 0.0).x, 1e-6)
        assertEquals(q.br.y, q.at(1.0, 1.0).y, 1e-6)
        assertEquals(q.bl.x, q.at(0.0, 1.0).x, 1e-6)
        // Midpoints lie on their sides.
        val top = q.edgeMidpoints()[0]
        val cross = (q.tr.x - q.tl.x) * (top.y - q.tl.y) - (q.tr.y - q.tl.y) * (top.x - q.tl.x)
        assertEquals(0.0, cross, 1e-6)
        // Rectangle: perspective midpoint = plain midpoint.
        val r = Quad.full(201, 101)
        assertEquals(100.0, r.edgeMidpoints()[0].x, 1e-9)
        assertEquals(50.0, r.edgeMidpoints()[1].y, 1e-9)
    }

    /** White page whose top edge sags 40 px in the middle, on a dark background. */
    private fun saggingPage(): Mat {
        val img = Mat(400, 300, CvType.CV_8UC3, Scalar(30.0, 30.0, 30.0))
        val row = ByteArray(300 * 3)
        for (y in 0 until 400) {
            for (x in 0 until 300) {
                val u = x / 299.0
                val topEdge = 40 * 4 * u * (1 - u)
                val white = y >= topEdge
                val v = if (white) 255.toByte() else 30.toByte()
                row[x * 3] = v
                row[x * 3 + 1] = v
                row[x * 3 + 2] = v
            }
            img.put(y, 0, row)
        }
        return img
    }

    private fun topRowMinimum(m: Mat): Double {
        val row = m.row(2)
        val mm = Core.minMaxLoc(row.reshape(1))
        row.release()
        return mm.minVal
    }

    @Test
    fun bendingTopMidpointRemovesBackgroundFromCurvedEdge() {
        val src = saggingPage()
        val quad = Quad.full(300, 400)
        val straight = PerspectiveWarper.warp(src, quad, inset = 0.0)
        assertTrue("straight warp still shows the background notch", topRowMinimum(straight) < 100)
        val bends = doubleArrayOf(0.0, 40.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        assertTrue(PerspectiveWarper.hasBend(bends))
        val bent = PerspectiveWarper.warp(src, quad, inset = 0.0, bends = bends)
        assertEquals(straight.size(), bent.size())
        assertTrue("bent warp follows the curved edge", topRowMinimum(bent) > 200)
        assertFalse(PerspectiveWarper.hasBend(DoubleArray(8)))
        listOf(src, straight, bent).forEach { it.release() }
    }
}
