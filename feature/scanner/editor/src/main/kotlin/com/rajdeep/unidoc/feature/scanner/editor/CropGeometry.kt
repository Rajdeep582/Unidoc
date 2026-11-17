package com.rajdeep.unidoc.feature.scanner.editor

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

/**
 * Pure geometry for the crop editor, in pixel space of the displayed image ([w]×[h]).
 * Handles: 0..3 = corners TL, TR, BR, BL; 4..7 = midpoints of the top, right, bottom, left sides.
 * Corners move freely; a side handle slides its whole side along the side's normal (like
 * CamScanner/Adobe Scan), so the outline is always a straight-sided quadrilateral and the output
 * can never be bent or sheared by a stray handle.
 */
object CropGeometry {
    const val NONE = -1

    fun toPx(n: List<Offset>, w: Float, h: Float) = n.map { Offset(it.x * w, it.y * h) }

    fun toNorm(p: List<Offset>, w: Float, h: Float) = p.map { Offset(it.x / w, it.y / h) }

    fun midpoints(c: List<Offset>) = List(4) { (c[it] + c[(it + 1) % 4]) / 2f }

    fun handles(c: List<Offset>) = c + midpoints(c)

    /**
     * Handle under [touch] within [radius], preferring corners: a side handle wins only when it is
     * clearly closer, so small or thin outlines don't grab the wrong point.
     */
    fun pick(c: List<Offset>, touch: Offset, radius: Float): Int {
        val all = handles(c)
        val corner = (0..3).minBy { (all[it] - touch).getDistance() }
        val side = (4..7).minBy { (all[it] - touch).getDistance() }
        val dc = (all[corner] - touch).getDistance()
        val ds = (all[side] - touch).getDistance()
        return when {
            dc <= radius && dc <= ds * 1.35f -> corner
            ds <= radius -> side
            dc <= radius -> corner
            else -> NONE
        }
    }

    /**
     * New corners after dragging [handle] so that it sits at [target] (pixels), starting from
     * [start] corners. Returns null when the move would make an unusable outline (self-crossing,
     * concave, collapsed side); the caller then keeps the last valid outline.
     */
    fun move(start: List<Offset>, handle: Int, target: Offset, w: Float, h: Float): List<Offset>? {
        fun clamp(p: Offset) = Offset(p.x.coerceIn(0f, w), p.y.coerceIn(0f, h))
        val next = start.toMutableList()
        if (handle in 0..3) {
            next[handle] = clamp(target)
        } else {
            val side = handle - 4
            val a = start[side]
            val b = start[(side + 1) % 4]
            val dir = b - a
            val len = dir.getDistance()
            if (len < 1f) return null
            val normal = Offset(-dir.y / len, dir.x / len)
            val d = (target - (a + b) / 2f).let { it.x * normal.x + it.y * normal.y }
            // Limit the slide so neither end leaves the image.
            val limited = limitAlong(listOf(a, b), normal, d, w, h)
            next[side] = a + normal * limited
            next[(side + 1) % 4] = b + normal * limited
        }
        val minSide = minOf(w, h) * 0.04f
        // If the starting outline is already odd (e.g. an old saved crop), never lock the user out.
        if (!isUsable(start, minSide)) return next
        return next.takeIf { isUsable(it, minSide) }
    }

    private fun limitAlong(points: List<Offset>, n: Offset, d: Float, w: Float, h: Float): Float {
        var lo = Float.NEGATIVE_INFINITY
        var hi = Float.POSITIVE_INFINITY
        for (p in points) {
            if (abs(n.x) > 1e-6f) {
                val t1 = (0f - p.x) / n.x
                val t2 = (w - p.x) / n.x
                lo = maxOf(lo, minOf(t1, t2))
                hi = minOf(hi, maxOf(t1, t2))
            }
            if (abs(n.y) > 1e-6f) {
                val t1 = (0f - p.y) / n.y
                val t2 = (h - p.y) / n.y
                lo = maxOf(lo, minOf(t1, t2))
                hi = minOf(hi, maxOf(t1, t2))
            }
        }
        return d.coerceIn(lo, hi)
    }

    /** Convex, consistently wound, no side shorter than [minSide], every angle 15°..165°. */
    fun isUsable(c: List<Offset>, minSide: Float): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val a = c[i]
            val b = c[(i + 1) % 4]
            val d = c[(i + 2) % 4]
            if ((b - a).getDistance() < minSide) return false
            val cross = (b.x - a.x) * (d.y - b.y) - (b.y - a.y) * (d.x - b.x)
            val s = if (cross > 0) {
                1
            } else if (cross < 0) {
                -1
            } else {
                0
            }
            if (s == 0) return false
            if (sign == 0) {
                sign = s
            } else if (s != sign) {
                return false
            }
        }
        // TL, TR, BR, BL on screen (y down) wind clockwise: positive cross products.
        if (sign < 0) return false
        for (i in 0 until 4) {
            val prev = c[(i + 3) % 4] - c[i]
            val next = c[(i + 1) % 4] - c[i]
            val cos = (prev.x * next.x + prev.y * next.y) / (prev.getDistance() * next.getDistance())
            val deg = Math.toDegrees(acos(cos.coerceIn(-1f, 1f).toDouble()))
            if (deg < 15 || deg > 165) return false
        }
        return true
    }

    fun distance(a: Offset, b: Offset) = hypot(a.x - b.x, a.y - b.y)
}
