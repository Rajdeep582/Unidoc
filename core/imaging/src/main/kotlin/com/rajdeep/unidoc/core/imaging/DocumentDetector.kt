package com.rajdeep.unidoc.core.imaging

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Finds the document quadrilateral in a photo.
 *
 * 1. **Candidates** (on a small copy, text erased by a morphological close): contours of colour
 *    edges (Lab, so a white page on a light-but-tinted desk still separates) and of the
 *    brightness/low-saturation blob, each simplified to a convex quad.
 * 2. **Scoring** — every candidate is checked against the image instead of trusting its area:
 *    all four sides must sit on real edges whose gradient points across the side, the band just
 *    inside must differ from the band just outside (paper vs. desk), and the inside band should be
 *    uniform (paper margin, not a busy pattern). Largest-area-wins picked tablecloth squares,
 *    desk outlines and shadows; this does not.
 * 3. **Refinement** (full-resolution grey): each side is re-located to sub-pixel accuracy by
 *    finding the strongest step along its normal at ~60 positions and robustly fitting a line;
 *    corners are the line intersections. Coarse corners were only accurate to ~8 px at 4000 px.
 */
class DocumentDetector(
    private val workingSize: Int = 640,
    private val minAreaFraction: Double = 0.08,
    private val refine: Boolean = true,
    private val refineSize: Int = 1800
) {
    data class Result(val quad: Quad, val confidence: Double)

    private class Field(val w: Int, val h: Int, val mag: FloatArray, val nx: FloatArray, val ny: FloatArray, val lab: Array<FloatArray>) {
        var alt: Field? = null
        var strong = 0f
        fun inside(x: Double, y: Double) = x >= 0 && y >= 0 && x <= w - 1 && y <= h - 1
        fun idx(x: Double, y: Double) = y.roundToInt().coerceIn(0, h - 1) * w + x.roundToInt().coerceIn(0, w - 1)
    }

    fun detect(input: Mat): Result? = withMats {
        val bgr = track(input.toBgr())
        val (small, scale) = bgr.fitWithin(workingSize).let { track(it.first) to it.second }
        val w = small.cols()
        val h = small.rows()
        val frameArea = w.toDouble() * h

        val lab = mat()
        Imgproc.cvtColor(small, lab, Imgproc.COLOR_BGR2Lab)
        val channels = ArrayList<Mat>()
        Core.split(lab, channels)
        channels.forEach { track(it) }
        // Dim or hazy photos: stretch lightness so thresholds behave the same as in good light.
        stretch(channels[0])
        // Erase text/lines (darker, thin) so the page interior becomes flat paper colour.
        val k = max(5, (max(w, h) * 0.014).roundToInt()).odd()
        val kernel = track(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(k.toDouble(), k.toDouble())))
        val clean = channels.map { c ->
            val out = mat()
            Imgproc.morphologyEx(c, out, Imgproc.MORPH_CLOSE, kernel)
            Imgproc.medianBlur(out, out, 5)
            out
        }

        // Closing erases text but also thin background bands (striped cloth, tiles): a page edge next
        // to such a band vanishes from the cleaned image. Edges are therefore also taken from a
        // lightly smoothed copy and either source may back a side.
        val raw = channels.map { c -> mat().also { Imgproc.medianBlur(c, it, 3) } }
        val field = gradientField(clean, w, h)
        val rawField = gradientField(raw, w, h)
        field.alt = rawField
        val candidates = ArrayList<Quad>()
        candidates += edgeCandidates(clean, field)
        candidates += blobCandidates(clean, small)
        val tol = hypot(w.toDouble(), h.toDouble()) * 0.02
        val regular = dedupe(candidates.filter { isPlausible(it, w, h, frameArea) }, tol)
        // Line-built quads are scored strictly: every side, end to end, on a real edge.
        if (field.strong == 0f) field.strong = edgeThreshold(field)
        if (rawField.strong == 0f) rawField.strong = edgeThreshold(rawField, 0.95, 0.6f)
        val strict = lineCandidates(rawEdges(raw), w, h).flatMap { snapVariants(it, field) }.filter { isPlausible(it, w, h, frameArea) }
        val regularScored = regular.mapNotNull { q -> score(q, field, frameArea)?.let { q to it } }
        val strictScored = strict
            .filter { q -> regularScored.none { it.first.maxCornerDistance(q) < tol } }
            .mapNotNull { q -> score(q, field, frameArea, strict = true)?.let { q to it } }
        // Only contour quads may "promote" to an enclosing outline; line quads compete on score.
        val (best, s) = pickOutermost(regularScored, strictScored) ?: return@withMats null

        var quad = best.scaled(1.0 / scale)
        if (refine) quad = refineQuad(bgr, quad) ?: quad
        Result(quad, s.coerceIn(0.0, 1.0))
    }

    // ------------------------------------------------------------------ gradients

    /** Per pixel: strongest gradient over L, a, b (chroma weighted ×2) and its unit direction. */
    private fun MatScope.gradientField(clean: List<Mat>, w: Int, h: Int): Field {
        val n = w * h
        val mag = FloatArray(n)
        val nx = FloatArray(n)
        val ny = FloatArray(n)
        val weights = doubleArrayOf(1.0, 2.0, 2.0)
        val dx = mat()
        val dy = mat()
        val bx = FloatArray(n)
        val by = FloatArray(n)
        clean.forEachIndexed { ci, c ->
            Imgproc.Scharr(c, dx, CvType.CV_32F, 1, 0)
            Imgproc.Scharr(c, dy, CvType.CV_32F, 0, 1)
            dx.get(0, 0, bx)
            dy.get(0, 0, by)
            val wgt = (weights[ci] / 16.0).toFloat() // Scharr gain is 16
            for (i in 0 until n) {
                val gx = bx[i] * wgt
                val gy = by[i] * wgt
                val m = sqrt(gx * gx + gy * gy)
                if (m > mag[i]) {
                    mag[i] = m
                    nx[i] = if (m > 0) gx / m else 0f
                    ny[i] = if (m > 0) gy / m else 0f
                }
            }
        }
        val labData = clean.map { c ->
            val f = FloatArray(n)
            val b = ByteArray(n)
            c.get(0, 0, b)
            for (i in 0 until n) f[i] = (b[i].toInt() and 0xFF).toFloat()
            f
        }.toTypedArray()
        return Field(w, h, mag, nx, ny, labData)
    }

    // ------------------------------------------------------------------ candidates

    private fun MatScope.edgeCandidates(clean: List<Mat>, field: Field): List<Quad> {
        val edges = mat()
        val tmp = mat()
        clean.forEachIndexed { i, c ->
            val median = median(c)
            val lo = if (i == 0) max(12.0, 0.4 * median * 0.25) else 6.0
            val hi = if (i == 0) max(30.0, lo * 2.5) else 18.0
            Imgproc.Canny(c, tmp, lo, hi)
            if (i == 0) tmp.copyTo(edges) else Core.bitwise_or(edges, tmp, edges)
        }
        Imgproc.dilate(edges, edges, track(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))))
        return quadsFromContours(edges, Imgproc.RETR_LIST, field.w * field.h)
    }

    private fun MatScope.rawEdges(raw: List<Mat>): Mat {
        val edges = mat()
        val tmp = mat()
        raw.forEachIndexed { i, c ->
            if (i == 0) {
                Imgproc.Canny(c, edges, 30.0, 80.0)
            } else {
                Imgproc.Canny(c, tmp, 12.0, 30.0)
                Core.bitwise_or(edges, tmp, edges)
            }
        }
        return edges
    }

    private class Line(val angle: Double, val rho: Double, var length: Double) {
        val nx get() = kotlin.math.cos(angle)
        val ny get() = kotlin.math.sin(angle)
    }

    /**
     * Quads built from long straight edges (Hough). On busy backgrounds — striped bedsheets, tiles,
     * a white page touching a white band — the paper's edge pixels connect to background edges, so
     * contours leak and never form the page; its four sides are still the longest straight lines
     * there. Opposite-side pairs × crossing pairs are intersected and handed to the normal scorer,
     * which rejects any side that isn't backed by a real edge over its whole length.
     */
    private fun MatScope.lineCandidates(edges: Mat, w: Int, h: Int): List<Quad> {
        val segs = mat()
        val minDim = min(w, h).toDouble()
        Imgproc.HoughLinesP(
            edges,
            segs,
            1.0,
            Math.PI / 360,
            (minDim * 0.08).roundToInt().coerceAtLeast(12),
            minDim * 0.12,
            max(w, h) * 0.015
        )
        if (segs.rows() == 0) return emptyList()
        val diag = hypot(w.toDouble(), h.toDouble())
        val lines = ArrayList<Line>()
        val v = IntArray(4)
        for (r in 0 until segs.rows()) {
            segs.get(r, 0, v)
            val dx = (v[2] - v[0]).toDouble()
            val dy = (v[3] - v[1]).toDouble()
            val len = hypot(dx, dy)
            if (len < 1) continue
            // Normal form: x·cos θ + y·sin θ = ρ, θ in [0, π).
            var theta = Math.atan2(dx, -dy)
            if (theta < 0) theta += Math.PI
            if (theta >= Math.PI) theta -= Math.PI
            val rho = v[0] * kotlin.math.cos(theta) + v[1] * kotlin.math.sin(theta)
            val same = lines.firstOrNull { l ->
                angleDiff(l.angle, theta) < Math.toRadians(2.5) && abs(signedRho(l, theta, rho) - l.rho) < diag * 0.012
            }
            if (same != null) same.length += len else lines += Line(theta, rho, len)
        }
        // Longest lines per 15° direction bin, so many parallel background lines (stripes, planks)
        // can't crowd out the page's other two sides.
        val top = lines.groupBy { ((it.angle / Math.toRadians(15.0)).toInt()) % 12 }
            .values.flatMap { bin -> bin.sortedByDescending { it.length }.take(7) }
            .sortedByDescending { it.length }
            .take(28)
        // Opposite sides: roughly parallel and well apart.
        val pairs = ArrayList<Pair<Line, Line>>()
        for (i in top.indices) {
            for (j in i + 1 until top.size) {
                val a = top[i]
                val b = top[j]
                if (angleDiff(a.angle, b.angle) > Math.toRadians(28.0)) continue
                if (abs(signedRho(a, b.angle, b.rho) - a.rho) < minDim * 0.12) continue
                pairs += a to b
            }
        }
        val result = ArrayList<Pair<Quad, Double>>()
        for (p in pairs.indices) {
            for (q in p + 1 until pairs.size) {
                val (a, b) = pairs[p]
                val (c, d) = pairs[q]
                val cross = angleDiff(a.angle, c.angle)
                if (cross < Math.toRadians(50.0)) continue
                val corners = listOf(meet(a, c), meet(c, b), meet(b, d), meet(d, a))
                if (corners.any { it == null }) continue
                val pts = corners.filterNotNull()
                // Inside the frame with a margin: pages cut by the frame are the contour path's job.
                if (pts.any { it.x < 2 || it.y < 2 || it.x > w - 3 || it.y > h - 3 }) continue
                result += Quad.ordered(pts) to (a.length + b.length + c.length + d.length)
            }
        }
        // Best-supported combinations first; bounded so busy scenes stay fast.
        return result.sortedByDescending { it.second }.take(48).map { it.first }
    }

    private fun angleDiff(a: Double, b: Double): Double {
        val d = abs(a - b) % Math.PI
        return min(d, Math.PI - d)
    }

    /** ρ of line (θ2, ρ2) expressed in the orientation of [ref] (flips sign across the 0/π seam). */
    private fun signedRho(ref: Line, theta: Double, rho: Double): Double = if (abs(ref.angle - theta) > Math.PI / 2) -rho else rho

    private fun meet(a: Line, b: Line): Pt? {
        val det = a.nx * b.ny - a.ny * b.nx
        if (abs(det) < 1e-6) return null
        return Pt((a.rho * b.ny - a.ny * b.rho) / det, (a.nx * b.rho - a.rho * b.nx) / det)
    }

    /** Paper is usually the brightest, least saturated large region. */
    private fun MatScope.blobCandidates(clean: List<Mat>, small: Mat): List<Quad> {
        val result = ArrayList<Quad>()
        val k = track(Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0)))
        val bin = mat()
        Imgproc.threshold(clean[0], bin, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
        Imgproc.morphologyEx(bin, bin, Imgproc.MORPH_OPEN, k)
        result += quadsFromContours(bin, Imgproc.RETR_EXTERNAL, small.cols() * small.rows())
        // Low saturation (whites/greys) vs. coloured desks.
        val hsv = mat()
        Imgproc.cvtColor(small, hsv, Imgproc.COLOR_BGR2HSV)
        val parts = ArrayList<Mat>()
        Core.split(hsv, parts)
        parts.forEach { track(it) }
        val sat = parts[1]
        Imgproc.medianBlur(sat, sat, 5)
        val satBin = mat()
        Imgproc.threshold(sat, satBin, 0.0, 255.0, Imgproc.THRESH_BINARY_INV or Imgproc.THRESH_OTSU)
        Imgproc.morphologyEx(satBin, satBin, Imgproc.MORPH_OPEN, k)
        result += quadsFromContours(satBin, Imgproc.RETR_EXTERNAL, small.cols() * small.rows())
        return result
    }

    private fun MatScope.quadsFromContours(binary: Mat, mode: Int, frameArea: Int): List<Quad> {
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(binary, contours, mat(), mode, Imgproc.CHAIN_APPROX_SIMPLE)
        val minArea = frameArea * minAreaFraction
        val result = ArrayList<Quad>()
        for (c in contours) {
            track(c)
            if (Imgproc.contourArea(c) < minArea * 0.5) continue
            val hullIdx = track(MatOfInt())
            Imgproc.convexHull(c, hullIdx)
            val pts = c.toArray()
            val hull = hullIdx.toArray().map { pts[it] }
            if (hull.size < 4) continue
            val hull2f = track(MatOfPoint2f(*hull.toTypedArray()))
            if (Imgproc.contourArea(hull2f) < minArea) continue
            val peri = Imgproc.arcLength(hull2f, true)
            var eps = 0.01
            while (eps <= 0.08) {
                val approx = track(MatOfPoint2f())
                Imgproc.approxPolyDP(hull2f, approx, eps * peri, true)
                if (approx.total() == 4L) {
                    result += Quad.ordered(approx.toArray().map { Pt(it.x, it.y) })
                    break
                }
                if (approx.total() < 4L) break
                eps += 0.005
            }
        }
        return result
    }

    /** Linear stretch of the 1st..99th percentile to 0..255 (in place). */
    private fun stretch(m: Mat) {
        val hist = IntArray(256)
        val data = ByteArray(m.cols() * m.rows())
        m.get(0, 0, data)
        data.forEach { hist[it.toInt() and 0xFF]++ }
        fun percentile(p: Double): Int {
            val target = (data.size * p).toInt()
            var acc = 0
            for (i in 0 until 256) {
                acc += hist[i]
                if (acc >= target) return i
            }
            return 255
        }
        val lo = percentile(0.01)
        val hi = percentile(0.99)
        if (hi - lo < 20 || (lo < 8 && hi > 247)) return
        val gain = 255.0 / (hi - lo)
        m.convertTo(m, -1, gain, -lo * gain)
    }

    private fun median(m: Mat): Double {
        val hist = IntArray(256)
        val data = ByteArray(m.cols() * m.rows())
        m.get(0, 0, data)
        data.forEach { hist[it.toInt() and 0xFF]++ }
        var acc = 0
        for (i in 0 until 256) {
            acc += hist[i]
            if (acc >= data.size / 2) return i.toDouble()
        }
        return 128.0
    }

    private fun dedupe(quads: List<Quad>, tolerance: Double): List<Quad> {
        val out = ArrayList<Quad>()
        for (q in quads) if (out.none { it.maxCornerDistance(q) < tolerance }) out += q
        return out
    }

    private fun isPlausible(q: Quad, w: Int, h: Int, frameArea: Double): Boolean {
        if (!q.isConvex()) return false
        val fraction = q.area() / frameArea
        if (fraction < minAreaFraction || fraction > 0.985) return false
        if (q.angles().any { it < 35 || it > 145 }) return false
        val (qw, qh) = q.naturalSize()
        if (qw < w * 0.12 || qh < h * 0.12) return false
        return true
    }

    // ------------------------------------------------------------------ scoring

    /**
     * Evidence that [q] is a page outline (0..1), or null when a side has no edge under it.
     */
    private fun score(q: Quad, f: Field, frameArea: Double, strict: Boolean = false): Double? {
        val pts = q.points
        val cx = pts.sumOf { it.x } / 4
        val cy = pts.sumOf { it.y } / 4
        if (f.strong == 0f) f.strong = edgeThreshold(f)
        f.alt?.let { if (it.strong == 0f) it.strong = edgeThreshold(it, 0.95, 0.6f) }
        val strong = f.strong
        val supports = DoubleArray(4)
        var contrastSum = 0.0
        var uniformSum = 0.0
        var borderSides = 0
        for (side in 0 until 4) {
            val a = pts[side]
            val b = pts[(side + 1) % 4]
            val len = a.distanceTo(b)
            var nxs = -(b.y - a.y) / len
            var nys = (b.x - a.x) / len
            // Outward normal.
            val mx = (a.x + b.x) / 2
            val my = (a.y + b.y) / 2
            if ((mx - cx) * nxs + (my - cy) * nys < 0) {
                nxs = -nxs
                nys = -nys
            }
            // A side lying on the frame border (page cut off by the photo) has no edge to find.
            val onBorder = listOf(a, b).all { p -> p.x < 3 || p.y < 3 || p.x > f.w - 4 || p.y > f.h - 4 } &&
                (abs(a.x - b.x) < 4 || abs(a.y - b.y) < 4)
            if (onBorder) {
                if (strict) return null
                borderSides++
                supports[side] = 0.75
                continue
            }
            val n = max(20, (len / 4).toInt())
            var hit = 0
            // Hits per fifth of the side: a real page edge runs the whole length, while a side
            // made of an unrelated line (wood grain, a stripe) plus a stretch of nothing does not.
            val segHits = IntArray(5)
            val segAll = IntArray(5)
            val inside = DoubleArray(3)
            val outside = DoubleArray(3)
            var insideSq = 0.0
            var samples = 0
            for (i in 0 until n) {
                val t = 0.06 + 0.88 * i / (n - 1)
                val px = a.x + (b.x - a.x) * t
                val py = a.y + (b.y - a.y) * t
                var best = 0f
                var bestAlign = 0f
                for (d in -3..3) {
                    val x = px + nxs * d
                    val y = py + nys * d
                    if (!f.inside(x, y)) continue
                    val id = f.idx(x, y)
                    if (f.mag[id] > best) {
                        best = f.mag[id]
                        bestAlign = abs(f.nx[id] * nxs.toFloat() + f.ny[id] * nys.toFloat())
                    }
                }
                var ok = best >= strong && bestAlign > 0.75f
                val alt = f.alt
                if (!ok && alt != null && strict) {
                    var b2 = 0f
                    var a2 = 0f
                    for (d in -3..3) {
                        val x = px + nxs * d
                        val y = py + nys * d
                        if (!alt.inside(x, y)) continue
                        val id = alt.idx(x, y)
                        if (alt.mag[id] > b2) {
                            b2 = alt.mag[id]
                            a2 = abs(alt.nx[id] * nxs.toFloat() + alt.ny[id] * nys.toFloat())
                        }
                    }
                    ok = b2 >= alt.strong && a2 > 0.9f
                }
                val seg = (i * 5 / n).coerceAtMost(4)
                segAll[seg]++
                if (ok) {
                    hit++
                    segHits[seg]++
                }
                val ix = px - nxs * 7
                val iy = py - nys * 7
                val ox = px + nxs * 7
                val oy = py + nys * 7
                if (f.inside(ix, iy) && f.inside(ox, oy)) {
                    val ii = f.idx(ix, iy)
                    val oi = f.idx(ox, oy)
                    for (c in 0 until 3) {
                        inside[c] += f.lab[c][ii]
                        outside[c] += f.lab[c][oi]
                    }
                    insideSq += f.lab[0][ii].toDouble() * f.lab[0][ii]
                    samples++
                }
            }
            supports[side] = hit.toDouble() / n
            if (strict) {
                val weakest = (0 until 5).minOf { if (segAll[it] == 0) 1.0 else segHits[it].toDouble() / segAll[it] }
                if (weakest < 0.5) {
                    supports[side] = 0.0
                } // Unbacked end: likely overshooting the page, but a page corner on a same-coloured
                // band looks the same; penalise so a tighter variant wins rather than rejecting.
                else if (!endsBacked(f, a, b, nxs, nys)) {
                    supports[side] *= 0.6
                }
            }
            if (samples > 0) {
                val dl = (inside[0] - outside[0]) / samples
                val da = (inside[1] - outside[1]) / samples * 2
                val db = (inside[2] - outside[2]) / samples * 2
                contrastSum += sqrt(dl * dl + da * da + db * db)
                val mean = inside[0] / samples
                uniformSum += sqrt(max(0.0, insideSq / samples - mean * mean))
            }
        }
        if (borderSides >= 3) return null
        val minSupport = supports.min()
        val meanSupport = supports.average()
        if (minSupport < 0.45 || meanSupport < 0.6) return null
        val real = (4 - borderSides).coerceAtLeast(1)
        val contrast = (contrastSum / real / 35.0).coerceIn(0.0, 1.0)
        if (contrast < 0.12) return null
        val uniform = (1 - uniformSum / real / 30.0).coerceIn(0.0, 1.0)
        val area = q.area() / frameArea
        val support = 0.5 * minSupport + 0.5 * meanSupport
        return support * support * (0.35 + 0.65 * contrast) * (0.45 + 0.55 * uniform) * (0.55 + 0.45 * sqrt(area))
    }

    /**
     * Hough lines are only accurate to a few pixels, and on striped cloth a page edge has parallel
     * background edges a few pixels away. Each side is moved onto every strong aligned edge within
     * ±8 px (each half fine-tuned, so its angle can change too); all combinations are returned for
     * the strict scorer, which keeps the one whose corners are actually backed.
     */
    private fun snapVariants(q: Quad, f: Field): List<Quad> {
        val pts = q.points
        val cx = pts.sumOf { it.x } / 4
        val cy = pts.sumOf { it.y } / 4
        fun aligned(x: Double, y: Double, nxs: Double, nys: Double): Boolean {
            for (field in listOfNotNull(f, f.alt)) {
                if (!field.inside(x, y)) continue
                val id = field.idx(x, y)
                if (field.mag[id] >= field.strong && abs(field.nx[id] * nxs + field.ny[id] * nys) > 0.85) return true
            }
            return false
        }
        val options = (0 until 4).map { side ->
            val a = pts[side]
            val b = pts[(side + 1) % 4]
            val len = a.distanceTo(b)
            if (len < 8) return emptyList()
            var nxs = -(b.y - a.y) / len
            var nys = (b.x - a.x) / len
            if (((a.x + b.x) / 2 - cx) * nxs + ((a.y + b.y) / 2 - cy) * nys < 0) {
                nxs = -nxs
                nys = -nys
            }
            fun count(o: Int, from: Double, to: Double): Int {
                var c = 0
                for (i in 0 until 16) {
                    val t = from + (to - from) * i / 15
                    if (aligned(a.x + (b.x - a.x) * t + nxs * o, a.y + (b.y - a.y) * t + nys * o, nxs, nys)) c++
                }
                return c
            }
            val whole = (-8..8).associateWith { count(it, 0.05, 0.95) }
            val top = whole.values.max()
            val peaks = whole.filter { (o, c) ->
                c >= top * 0.75 && c >= (whole[o - 1] ?: -1) && c >= (whole[o + 1] ?: -1)
            }.keys.sortedByDescending { whole[it] }.take(2)
            peaks.map { o ->
                fun best(from: Double, to: Double) = (o - 2..o + 2).maxBy { count(it, from, to) * 10 - abs(it - o) }.toDouble()
                val o1 = best(0.05, 0.5)
                val o2 = best(0.5, 0.95)
                val p1 = Pt(a.x + (b.x - a.x) * 0.275 + nxs * o1, a.y + (b.y - a.y) * 0.275 + nys * o1)
                val p2 = Pt(a.x + (b.x - a.x) * 0.725 + nxs * o2, a.y + (b.y - a.y) * 0.725 + nys * o2)
                val d = p1.distanceTo(p2).coerceAtLeast(1e-6)
                p1 to Pt((p2.x - p1.x) / d, (p2.y - p1.y) / d)
            }
        }
        val out = ArrayList<Quad>()
        for (l0 in options[0]) {
            for (l1 in options[1]) {
                for (l2 in options[2]) {
                    for (l3 in options[3]) {
                        val lines = listOf(l0, l1, l2, l3)
                        val corners = (0 until 4).map { i -> intersect(lines[(i + 3) % 4], lines[i]) }
                        if (corners.any { it == null }) continue
                        val quad = Quad(corners[0]!!, corners[1]!!, corners[2]!!, corners[3]!!)
                        if (quad.isConvex() && quad.maxCornerDistance(q) < 20) out += quad
                    }
                }
            }
        }
        return out
    }

    /** Both ends of a side (2–12 px in from each corner) sit on an aligned edge in either field. */
    private fun endsBacked(f: Field, a: Pt, b: Pt, nxs: Double, nys: Double): Boolean {
        fun hit(t: Double): Boolean {
            val px = a.x + (b.x - a.x) * t
            val py = a.y + (b.y - a.y) * t
            for (field in listOfNotNull(f, f.alt)) {
                for (d in -3..3) {
                    val x = px + nxs * d
                    val y = py + nys * d
                    if (!field.inside(x, y)) continue
                    val id = field.idx(x, y)
                    if (field.mag[id] >= field.strong && abs(field.nx[id] * nxs + field.ny[id] * nys) > 0.85) return true
                }
            }
            return false
        }
        // 2–12 px from each corner (working scale): an overshooting side has nothing there.
        val len = a.distanceTo(b)
        val ts = listOf(2.0, 4.5, 7.0, 9.5, 12.0).map { it / len }
        // A side that overshoots the page has nothing at its end; a page edge next to a same-coloured
        // band still shows an edge at some of these points.
        return ts.count { hit(it) } >= 1 && ts.count { hit(1 - it) } >= 1
    }

    /** "Strong edge" level: relative to this image's gradient distribution. */
    private fun edgeThreshold(f: Field, pct: Double = 0.9, gain: Float = 0.45f): Float {
        val hist = IntArray(256)
        for (m in f.mag) hist[min(255, m.toInt())]++
        var acc = 0
        val target = (f.mag.size * pct).toInt()
        var p90 = 255
        for (i in 0 until 256) {
            acc += hist[i]
            if (acc >= target) {
                p90 = i
                break
            }
        }
        return max(6f, p90 * gain)
    }

    /**
     * Highest score, except that a well-supported quad *enclosing* the winner beats it: printed
     * photos, tables and boxes on a page are rectangles too, but the page is the one around them.
     */
    private fun pickOutermost(scored: List<Pair<Quad, Double>>, extra: List<Pair<Quad, Double>> = emptyList()): Pair<Quad, Double>? {
        var best = (scored + extra).maxByOrNull { it.second } ?: return null
        while (true) {
            val outer = scored
                .filter { (q, sc) ->
                    sc >= best.second * 0.45 &&
                        q.area() > best.first.area() * 1.15 &&
                        best.first.points.all { q.contains(it) }
                }
                .maxByOrNull { it.first.area() } ?: return best
            best = outer
        }
    }

    // ------------------------------------------------------------------ refinement

    /** Re-locates each side on a large grey copy with sub-pixel accuracy; null if it fails. */
    private fun refineQuad(bgr: Mat, coarse: Quad): Quad? = withMats {
        val gray = track(bgr.toGray())
        val (img, s) = gray.fitWithin(refineSize).let { track(it.first) to it.second }
        Imgproc.GaussianBlur(img, img, Size(3.0, 3.0), 0.0)
        val w = img.cols()
        val h = img.rows()
        val data = ByteArray(w * h)
        img.get(0, 0, data)
        fun px(x: Double, y: Double): Double {
            val xi = x.toInt().coerceIn(0, w - 2)
            val yi = y.toInt().coerceIn(0, h - 2)
            val fx = (x - xi).coerceIn(0.0, 1.0)
            val fy = (y - yi).coerceIn(0.0, 1.0)
            fun v(xx: Int, yy: Int) = (data[yy * w + xx].toInt() and 0xFF).toDouble()
            return v(xi, yi) * (1 - fx) * (1 - fy) + v(xi + 1, yi) * fx * (1 - fy) + v(xi, yi + 1) * (1 - fx) * fy +
                v(xi + 1, yi + 1) * fx * fy
        }
        val q = coarse.scaled(s)
        val pts = q.points
        val cx = pts.sumOf { it.x } / 4
        val cy = pts.sumOf { it.y } / 4
        val reach = max(6.0, max(w, h) * 0.012)
        val lines = (0 until 4).map { side ->
            val a = pts[side]
            val b = pts[(side + 1) % 4]
            val len = a.distanceTo(b)
            var nxs = -(b.y - a.y) / len
            var nys = (b.x - a.x) / len
            if (((a.x + b.x) / 2 - cx) * nxs + ((a.y + b.y) / 2 - cy) * nys < 0) {
                nxs = -nxs
                nys = -nys
            }
            val found = ArrayList<Pt>()
            val samples = 60
            for (i in 0 until samples) {
                val t = 0.08 + 0.84 * i / (samples - 1)
                val bx = a.x + (b.x - a.x) * t
                val by = a.y + (b.y - a.y) * t
                var bestD = 0.0
                var bestOff = 0.0
                var o = -reach
                val profile = ArrayList<Pair<Double, Double>>()
                while (o <= reach) {
                    val x0 = bx + nxs * (o - 1)
                    val y0 = by + nys * (o - 1)
                    val x1 = bx + nxs * (o + 1)
                    val y1 = by + nys * (o + 1)
                    if (x0 in 0.0..(w - 1.0) && y0 in 0.0..(h - 1.0) && x1 in 0.0..(w - 1.0) && y1 in 0.0..(h - 1.0)) {
                        // Inside → outside step; paper is usually brighter, but take either sign.
                        val d = abs(px(x0, y0) - px(x1, y1))
                        profile += o to d
                        if (d > bestD) {
                            bestD = d
                            bestOff = o
                        }
                    }
                    o += 0.5
                }
                if (bestD < 12) continue
                // Parabolic sub-pixel peak.
                val idx = profile.indexOfFirst { it.first == bestOff }
                var off = bestOff
                if (idx > 0 && idx < profile.size - 1) {
                    val l = profile[idx - 1].second
                    val c = profile[idx].second
                    val r = profile[idx + 1].second
                    val den = l - 2 * c + r
                    if (abs(den) > 1e-9) off += (0.5 * (l - r) / den * 0.5).coerceIn(-0.5, 0.5)
                }
                found += Pt(bx + nxs * off, by + nys * off)
            }
            fitLine(found) ?: return@withMats null
        }
        val corners = (0 until 4).map { i -> intersect(lines[(i + 3) % 4], lines[i]) ?: return@withMats null }
        val refined = Quad(corners[0], corners[1], corners[2], corners[3])
        val limit = hypot(w.toDouble(), h.toDouble()) * 0.03
        if (!refined.isConvex() || refined.maxCornerDistance(q) > limit) return@withMats null
        val c = refined.points.map { Pt(it.x.coerceIn(0.0, w - 1.0), it.y.coerceIn(0.0, h - 1.0)) }
        Quad(c[0], c[1], c[2], c[3]).scaled(1.0 / s)
    }

    /** Line through points as (point, unit direction), by total least squares with outlier rejection. */
    private fun fitLine(points: List<Pt>): Pair<Pt, Pt>? {
        var pts = points
        repeat(3) {
            if (pts.size < 8) return null
            val mx = pts.sumOf { it.x } / pts.size
            val my = pts.sumOf { it.y } / pts.size
            var sxx = 0.0
            var syy = 0.0
            var sxy = 0.0
            for (p in pts) {
                sxx += (p.x - mx) * (p.x - mx)
                syy += (p.y - my) * (p.y - my)
                sxy += (p.x - mx) * (p.y - my)
            }
            val angle = 0.5 * Math.atan2(2 * sxy, sxx - syy)
            val dir = Pt(Math.cos(angle), Math.sin(angle))
            val residual = pts.map { abs(-(it.x - mx) * dir.y + (it.y - my) * dir.x) }
            val sorted = residual.sorted()
            val med = sorted[sorted.size / 2]
            val keep = max(1.0, med * 2.5)
            val next = pts.filterIndexed { i, _ -> residual[i] <= keep }
            if (next.size == pts.size || it == 2) return Pt(mx, my) to dir
            pts = next
        }
        return null
    }

    private fun intersect(l1: Pair<Pt, Pt>, l2: Pair<Pt, Pt>): Pt? {
        val (p, r) = l1
        val (q, s) = l2
        val den = r.x * s.y - r.y * s.x
        if (abs(den) < 1e-9) return null
        val t = ((q.x - p.x) * s.y - (q.y - p.y) * s.x) / den
        return Pt(p.x + r.x * t, p.y + r.y * t)
    }

    companion object {
        /** Variance of the Laplacian: higher = sharper. ~>60 at 480 px is acceptably sharp. */
        fun sharpness(input: Mat): Double = withMats {
            val gray = track(input.toGray())
            val (small, _) = gray.fitWithin(640).let { track(it.first) to it.second }
            val lap = mat()
            Imgproc.Laplacian(small, lap, CvType.CV_64F)
            val mean = track(MatOfDouble())
            val std = track(MatOfDouble())
            Core.meanStdDev(lap, mean, std)
            std.toArray()[0].let { it * it }
        }
    }
}
