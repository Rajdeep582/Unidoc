package com.rajdeep.unidoc.core.imaging

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

data class Pt(val x: Double, val y: Double) {
    fun distanceTo(o: Pt): Double = hypot(x - o.x, y - o.y)
}

/** Document corners, always ordered top-left, top-right, bottom-right, bottom-left. */
data class Quad(val tl: Pt, val tr: Pt, val br: Pt, val bl: Pt) {
    val points: List<Pt> get() = listOf(tl, tr, br, bl)

    fun scaled(sx: Double, sy: Double = sx) = Quad(
        Pt(tl.x * sx, tl.y * sy),
        Pt(tr.x * sx, tr.y * sy),
        Pt(br.x * sx, br.y * sy),
        Pt(bl.x * sx, bl.y * sy)
    )

    /** Coordinates in 0..1 relative to a [width]×[height] frame. */
    fun normalized(width: Int, height: Int) = scaled(1.0 / width, 1.0 / height)

    /** Inverse of [normalized]. */
    fun denormalized(width: Int, height: Int) = scaled(width.toDouble(), height.toDouble())

    /** Polygon area (shoelace). */
    fun area(): Double {
        val p = points
        var s = 0.0
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            s += a.x * b.y - b.x * a.y
        }
        return abs(s) / 2
    }

    fun maxCornerDistance(other: Quad): Double = points.zip(other.points).maxOf { (a, b) -> a.distanceTo(b) }

    fun isConvex(): Boolean {
        val p = points
        var sign = 0
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % 4]
            val c = p[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            val s = if (cross > 0) {
                1
            } else if (cross < 0) {
                -1
            } else {
                0
            }
            if (s != 0) {
                if (sign == 0) {
                    sign = s
                } else if (s != sign) {
                    return false
                }
            }
        }
        return sign != 0
    }

    /** True when [p] lies inside this (convex) quad or on its edge. */
    fun contains(p: Pt): Boolean {
        val pts = points
        var sign = 0
        for (i in 0 until 4) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            val cross = (b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)
            val sgn = if (cross > 1e-9) {
                1
            } else if (cross < -1e-9) {
                -1
            } else {
                0
            }
            if (sgn == 0) continue
            if (sign == 0) {
                sign = sgn
            } else if (sgn != sign) {
                return false
            }
        }
        return true
    }

    /** Interior angles in degrees, in corner order. */
    fun angles(): List<Double> {
        val p = points
        return p.indices.map { i ->
            val prev = p[(i + 3) % 4]
            val cur = p[i]
            val next = p[(i + 1) % 4]
            val v1x = prev.x - cur.x
            val v1y = prev.y - cur.y
            val v2x = next.x - cur.x
            val v2y = next.y - cur.y
            val denom = sqrt(v1x * v1x + v1y * v1y) * sqrt(v2x * v2x + v2y * v2y)
            if (denom == 0.0) 0.0 else Math.toDegrees(acos(((v1x * v2x + v1y * v2y) / denom).coerceIn(-1.0, 1.0)))
        }
    }

    /** Output size for a perspective warp that preserves the longest edges. */
    fun naturalSize(): Pair<Double, Double> {
        val w = max(tl.distanceTo(tr), bl.distanceTo(br))
        val h = max(tl.distanceTo(bl), tr.distanceTo(br))
        return w to h
    }

    /**
     * Projective map from the unit square to this quad: (0,0)→TL, (1,0)→TR, (1,1)→BR, (0,1)→BL.
     * Returns [a,b,c,d,e,f,g,h]: x = (a·u + b·v + c)/w, y = (d·u + e·v + f)/w, w = g·u + h·v + 1.
     */
    fun unitSquareMap(): DoubleArray {
        val (x0, y0) = tl.x to tl.y
        val (x1, y1) = tr.x to tr.y
        val (x2, y2) = br.x to br.y
        val (x3, y3) = bl.x to bl.y
        val dx3 = x0 - x1 + x2 - x3
        val dy3 = y0 - y1 + y2 - y3
        if (abs(dx3) < 1e-12 && abs(dy3) < 1e-12) {
            return doubleArrayOf(x1 - x0, x2 - x1, x0, y1 - y0, y2 - y1, y0, 0.0, 0.0)
        }
        val dx1 = x1 - x2
        val dx2 = x3 - x2
        val dy1 = y1 - y2
        val dy2 = y3 - y2
        val den = dx1 * dy2 - dx2 * dy1
        if (abs(den) < 1e-12) return doubleArrayOf(x1 - x0, x3 - x0, x0, y1 - y0, y3 - y0, y0, 0.0, 0.0)
        val g = (dx3 * dy2 - dx2 * dy3) / den
        val h = (dx1 * dy3 - dx3 * dy1) / den
        return doubleArrayOf(x1 - x0 + g * x1, x3 - x0 + h * x3, x0, y1 - y0 + g * y1, y3 - y0 + h * y3, y0, g, h)
    }

    /** Point at unit-square coordinates (u, v), perspective-correct. */
    fun at(u: Double, v: Double): Pt {
        val m = unitSquareMap()
        val w = m[6] * u + m[7] * v + 1
        return Pt((m[0] * u + m[1] * v + m[2]) / w, (m[3] * u + m[4] * v + m[5]) / w)
    }

    /** Perspective-correct midpoints of the sides: top, right, bottom, left. */
    fun edgeMidpoints(): List<Pt> = listOf(at(0.5, 0.0), at(1.0, 0.5), at(0.5, 1.0), at(0.0, 0.5))

    fun averageWidth(): Double = (tl.distanceTo(tr) + bl.distanceTo(br)) / 2

    fun averageHeight(): Double = (tl.distanceTo(bl) + tr.distanceTo(br)) / 2

    companion object {
        fun full(width: Int, height: Int) = Quad(
            Pt(0.0, 0.0),
            Pt(width - 1.0, 0.0),
            Pt(width - 1.0, height - 1.0),
            Pt(0.0, height - 1.0)
        )

        /** Orders four arbitrary points into TL, TR, BR, BL. */
        fun ordered(points: List<Pt>): Quad {
            require(points.size == 4) { "Need 4 points, got ${points.size}" }
            // Walk the points clockwise (y points down) around their centroid, so the result is
            // never self-crossing, then start at the top-left-most one. (Picking each corner by
            // x+y / x−y independently can produce a twisted order for tilted pages.)
            val cx = points.sumOf { it.x } / 4
            val cy = points.sumOf { it.y } / 4
            val sorted = points.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) }
            val start = sorted.indices.minBy { sorted[it].x + sorted[it].y }
            val r = (0 until 4).map { sorted[(start + it) % 4] }
            return Quad(r[0], r[1], r[2], r[3])
        }
    }
}
