package com.rajdeep.unidoc.core.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    @Test
    fun orderedHandlesShuffledInput() {
        val q = Quad.ordered(listOf(Pt(90.0, 110.0), Pt(10.0, 5.0), Pt(5.0, 100.0), Pt(100.0, 0.0)))
        assertEquals(Pt(10.0, 5.0), q.tl)
        assertEquals(Pt(100.0, 0.0), q.tr)
        assertEquals(Pt(90.0, 110.0), q.br)
        assertEquals(Pt(5.0, 100.0), q.bl)
    }

    @Test
    fun areaAnglesConvexity() {
        val sq = Quad.full(101, 101)
        assertEquals(10000.0, sq.area(), 1e-6)
        sq.angles().forEach { assertEquals(90.0, it, 1e-6) }
        assertTrue(sq.isConvex())
        assertFalse(Quad(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(2.0, 2.0), Pt(0.0, 10.0)).isConvex())
    }

    @Test
    fun stabilityRequiresSteadySharpFrames() {
        val t = StabilityTracker(requiredFrames = 3, tolerance = 0.02, minSharpness = 10.0)
        val q = Quad(Pt(0.2, 0.2), Pt(0.8, 0.2), Pt(0.8, 0.8), Pt(0.2, 0.8))
        assertEquals(0f, t.update(q, 100.0))
        t.update(q, 100.0)
        t.update(q, 100.0)
        assertFalse(t.isReady)
        t.update(q, 100.0)
        assertTrue(t.isReady)
        val moved = q.scaled(1.1)
        assertEquals(0f, t.update(moved, 100.0))
        t.update(moved, 1.0)
        assertFalse("blurry frames must not complete", t.isReady)
        assertEquals(0f, t.update(null, 100.0))
    }
}
