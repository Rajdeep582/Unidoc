package com.rajdeep.unidoc.core.imaging

import kotlin.math.roundToInt
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object PerspectiveWarper {
    /** A4 at 300 dpi long side; also the cap for processed pages. */
    const val MAX_SIDE = 3508

    /**
     * Rectifies [quad] (pixel coords in [src]) into an upright image.
     * [aspect] (width/height) forces a known shape (e.g. ID-1 card); otherwise edge lengths decide.
     * [bends] (8 values, pixels: dx,dy for the top, right, bottom and left side midpoints) bend the
     * sides through user-placed midpoints — curved pages, book spines; all zero = pure perspective.
     */
    fun warp(
        src: Mat,
        quad: Quad,
        aspect: Double? = null,
        maxSide: Int = MAX_SIDE,
        inset: Double = 0.004,
        bends: DoubleArray? = null,
        focalPx: Double? = null
    ): Mat = withMats {
        val (maxW, maxH) = quad.naturalSize()
        // Aspect from averaged opposite edges (less biased by perspective than the max),
        // snapped to a standard paper ratio when within 3%. Resolution follows the longest edges.
        val ratio = aspect ?: snapToPaper(estimateAspect(quad, src.cols(), src.rows(), focalPx))
        val w: Double
        val h: Double
        if (maxW / maxH > ratio) {
            w = maxW
            h = maxW / ratio
        } else {
            h = maxH
            w = maxH * ratio
        }
        val scale = minOf(1.0, maxSide / maxOf(w, h))
        val outW = (w * scale).roundToInt().coerceAtLeast(16)
        val outH = (h * scale).roundToInt().coerceAtLeast(16)
        val srcPts = track(MatOfPoint2f(*quad.points.map { Point(it.x, it.y) }.toTypedArray()))
        val dstPts = track(
            MatOfPoint2f(
                Point(0.0, 0.0),
                Point(outW - 1.0, 0.0),
                Point(outW - 1.0, outH - 1.0),
                Point(0.0, outH - 1.0)
            )
        )
        val full = mat()
        if (bends != null && hasBend(bends)) {
            bentWarp(src, quad, bends, outW, outH, full)
        } else {
            val m = track(Imgproc.getPerspectiveTransform(srcPts, dstPts))
            Imgproc.warpPerspective(src, full, m, Size(outW.toDouble(), outH.toDouble()), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE)
        }
        // Trim a hair of each edge: detected edges sit on the paper border and catch background.
        val dx = (outW * inset).roundToInt()
        val dy = (outH * inset).roundToInt()
        full.submat(dy, outH - dy, dx, outW - dx).clone()
    }

    /** True when any side midpoint was moved off its straight-edge position by more than ~0.5 px. */
    fun hasBend(bends: DoubleArray): Boolean = bends.size == 8 && bends.any { kotlin.math.abs(it) > 0.5 }

    /**
     * Perspective map plus a smooth displacement field: each side's offset is a parabola that is
     * zero at the corners and equals the midpoint offset at the middle; interior points blend the
     * opposite sides (Coons patch of the side deviations). Output is computed in row strips so
     * peak memory stays small even for A4 at 300 dpi.
     */
    private fun bentWarp(src: Mat, quad: Quad, bends: DoubleArray, outW: Int, outH: Int, out: Mat) {
        val m = quad.unitSquareMap()
        out.create(outH, outW, src.type())
        val strip = 128
        val mapX = Mat()
        val mapY = Mat()
        val part = Mat()
        try {
            var y0 = 0
            while (y0 < outH) {
                val rows = minOf(strip, outH - y0)
                val xs = FloatArray(rows * outW)
                val ys = FloatArray(rows * outW)
                for (r in 0 until rows) {
                    val v = (y0 + r).toDouble() / (outH - 1).coerceAtLeast(1)
                    val bv = 4 * v * (1 - v)
                    for (c in 0 until outW) {
                        val u = c.toDouble() / (outW - 1).coerceAtLeast(1)
                        val bu = 4 * u * (1 - u)
                        val w = m[6] * u + m[7] * v + 1
                        val px = (m[0] * u + m[1] * v + m[2]) / w
                        val py = (m[3] * u + m[4] * v + m[5]) / w
                        val dx = (1 - v) * bu * bends[0] + u * bv * bends[2] + v * bu * bends[4] + (1 - u) * bv * bends[6]
                        val dy = (1 - v) * bu * bends[1] + u * bv * bends[3] + v * bu * bends[5] + (1 - u) * bv * bends[7]
                        val i = r * outW + c
                        xs[i] = (px + dx).toFloat()
                        ys[i] = (py + dy).toFloat()
                    }
                }
                mapX.create(rows, outW, org.opencv.core.CvType.CV_32FC1)
                mapY.create(rows, outW, org.opencv.core.CvType.CV_32FC1)
                mapX.put(0, 0, xs)
                mapY.put(0, 0, ys)
                Imgproc.remap(src, part, mapX, mapY, Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE)
                val target = out.submat(y0, y0 + rows, 0, outW)
                part.copyTo(target)
                target.release()
                y0 += rows
            }
        } finally {
            mapX.release()
            mapY.release()
            part.release()
        }
    }

    /**
     * True width/height of the photographed rectangle. Uses the pinhole-camera method of Zhang & He
     * ("Whiteboard scanning and image enhancement"): with the principal point at the image centre
     * the quad determines the focal length, which undoes foreshortening. Plain edge-length ratios
     * squash pages shot at an angle. Falls back to averaged edges when the geometry is degenerate
     * (nearly frontal shot) or the estimate is implausible.
     */
    fun estimateAspect(quad: Quad, imageWidth: Int, imageHeight: Int, focalPx: Double? = null): Double {
        val fallback = quad.averageWidth() / quad.averageHeight()
        val u0 = imageWidth / 2.0
        val v0 = imageHeight / 2.0
        // Homogeneous corners: m1 = TL, m2 = TR, m3 = BL, m4 = BR.
        val m1 = doubleArrayOf(quad.tl.x, quad.tl.y, 1.0)
        val m2 = doubleArrayOf(quad.tr.x, quad.tr.y, 1.0)
        val m3 = doubleArrayOf(quad.bl.x, quad.bl.y, 1.0)
        val m4 = doubleArrayOf(quad.br.x, quad.br.y, 1.0)
        fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        )
        fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
        val d2 = dot(cross(m2, m4), m3)
        val d3 = dot(cross(m3, m4), m2)
        if (kotlin.math.abs(d2) < 1e-9 || kotlin.math.abs(d3) < 1e-9) return fallback
        val k2 = dot(cross(m1, m4), m3) / d2
        val k3 = dot(cross(m1, m4), m2) / d3
        val n2 = DoubleArray(3) { k2 * m2[it] - m1[it] }
        val n3 = DoubleArray(3) { k3 * m3[it] - m1[it] }
        val longest = maxOf(imageWidth, imageHeight).toDouble()
        val f2 = -(
            (n2[0] - n2[2] * u0) * (n3[0] - n3[2] * u0) + (n2[1] - n2[2] * v0) * (n3[1] - n3[2] * v0)
            ) / (n2[2] * n3[2])
        // With two finite vanishing points the focal length follows from the quad. With one (page
        // tilted about a single axis — the usual "phone held above the desk" shot) or none it is
        // undefined: use the photo's EXIF focal length when known, else a typical phone main
        // camera (~26 mm equivalent ≈ 0.75 × the long side in pixels).
        val fromQuad = kotlin.math.sqrt(f2).takeIf { f2.isFinite() && f2 > 0 && it in longest * 0.35..longest * 4 }
        val hint = focalPx?.takeIf { it in longest * 0.3..longest * 5 }
        val f = when {
            // Near-degenerate geometry gives an unstable estimate; trust EXIF when it disagrees a lot.
            fromQuad != null && hint != null && (fromQuad / hint !in 0.6..1.6) -> hint
            fromQuad != null -> fromQuad
            else -> hint ?: (longest * 0.75)
        }

        // |K⁻¹ n|² for n2 and n3 (K = [[f,0,u0],[0,f,v0],[0,0,1]]).
        fun norm(n: DoubleArray): Double {
            val x = (n[0] - u0 * n[2]) / f
            val y = (n[1] - v0 * n[2]) / f
            return x * x + y * y + n[2] * n[2]
        }
        val ratio = kotlin.math.sqrt(norm(n2) / norm(n3))
        if (!ratio.isFinite() || ratio <= 0 || ratio / fallback > 1.6 || fallback / ratio > 1.6) return fallback
        return ratio
    }

    /** Focal length in pixels from a 35 mm-equivalent focal length (EXIF FocalLengthIn35mmFilm). */
    fun focalPxFrom35mm(focal35: Double, imageWidth: Int, imageHeight: Int): Double =
        focal35 / 43.27 * kotlin.math.hypot(imageWidth.toDouble(), imageHeight.toDouble())

    private val PaperRatios = doubleArrayOf(1 / 1.4142, 8.5 / 11, 8.5 / 14, 1.4142, 11 / 8.5)

    internal fun snapToPaper(ratio: Double): Double =
        PaperRatios.minByOrNull { kotlin.math.abs(it - ratio) / it }?.takeIf { kotlin.math.abs(it - ratio) / it < 0.03 } ?: ratio

    fun flip(src: Mat): Mat {
        val out = Mat()
        Core.flip(src, out, 1)
        return out
    }

    /** Fine rotation by [degrees] (clockwise positive) keeping size; corners filled from the edge. */
    fun straighten(src: Mat, degrees: Double): Mat = withMats {
        val center = Point(src.cols() / 2.0, src.rows() / 2.0)
        // Zoom slightly so no empty corners show (cover scale for the rotated rectangle).
        val rad = Math.toRadians(kotlin.math.abs(degrees))
        val w = src.cols().toDouble()
        val h = src.rows().toDouble()
        val scale = maxOf(
            (w * kotlin.math.cos(rad) + h * kotlin.math.sin(rad)) / w,
            (w * kotlin.math.sin(rad) + h * kotlin.math.cos(rad)) / h
        )
        val m = track(Imgproc.getRotationMatrix2D(center, -degrees, scale))
        val out = Mat()
        Imgproc.warpAffine(src, out, m, src.size(), Imgproc.INTER_CUBIC, Core.BORDER_REPLICATE)
        out
    }

    /** Rotates by a multiple of 90° (positive = clockwise). Returns a new Mat. */
    fun rotate(src: Mat, degrees: Int): Mat {
        val out = Mat()
        when (((degrees % 360) + 360) % 360) {
            90 -> Core.rotate(src, out, Core.ROTATE_90_CLOCKWISE)
            180 -> Core.rotate(src, out, Core.ROTATE_180)
            270 -> Core.rotate(src, out, Core.ROTATE_90_COUNTERCLOCKWISE)
            else -> src.copyTo(out)
        }
        return out
    }
}
