package com.rajdeep.unidoc.core.imaging

import kotlin.random.Random
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object Fixtures {
    private var loaded = false

    fun loadOpenCv() {
        if (!loaded) {
            nu.pattern.OpenCV.loadLocally()
            loaded = true
        }
    }

    /** White A4 page (BGR) with paragraphs of dark "text" bars and a coloured header band. */
    fun page(width: Int = 1240, height: Int = 1754, colorHeader: Boolean = true, seed: Int = 1): Mat {
        val page = Mat(height, width, CvType.CV_8UC3, Scalar(250.0, 250.0, 250.0))
        val rnd = Random(seed)
        if (colorHeader) Imgproc.rectangle(page, Rect(100, 100, width / 2, 60), Scalar(220.0, 80.0, 60.0), -1)
        var y = 240
        while (y < height - 140) {
            repeat(rnd.nextInt(3, 6)) {
                if (y < height - 140) {
                    val w = (width - 200) * (0.6 + rnd.nextDouble() * 0.4)
                    Imgproc.rectangle(page, Rect(100, y, w.toInt(), 14), Scalar(30.0, 30.0, 30.0), -1)
                    y += 30
                }
            }
            y += 30
        }
        return page
    }

    /** Darker textured desk background (BGR). */
    fun desk(width: Int = 1500, height: Int = 2000): Mat {
        val bg = Mat(height, width, CvType.CV_8UC3, Scalar(70.0, 90.0, 110.0))
        val noise = Mat(height, width, CvType.CV_8UC3)
        Core.randn(noise, 0.0, 12.0)
        Core.add(bg, noise, bg)
        noise.release()
        return bg
    }

    /** Projects [page] onto [bg] at the given corners (TL, TR, BR, BL); returns the composite. */
    fun composite(page: Mat, bg: Mat, corners: List<Point>): Mat {
        val src = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(page.cols() - 1.0, 0.0),
            Point(page.cols() - 1.0, page.rows() - 1.0),
            Point(0.0, page.rows() - 1.0)
        )
        val dst = MatOfPoint2f(*corners.toTypedArray())
        val m = Imgproc.getPerspectiveTransform(src, dst)
        val out = bg.clone()
        Imgproc.warpPerspective(page, out, m, bg.size(), Imgproc.INTER_LINEAR, Core.BORDER_TRANSPARENT)
        return out
    }

    /** Darkens the bottom-right part with a soft gradient (a hand/phone shadow). */
    fun addShadow(img: Mat, strength: Double = 0.45): Mat {
        val mask = Mat(img.size(), CvType.CV_32F)
        val data = FloatArray(img.cols() * img.rows())
        for (y in 0 until img.rows()) {
            for (x in 0 until img.cols()) {
                val t = ((x.toDouble() / img.cols() + y.toDouble() / img.rows()) - 0.9).coerceIn(0.0, 1.0)
                data[y * img.cols() + x] = (1.0 - strength * t).toFloat()
            }
        }
        mask.put(0, 0, data)
        val f = Mat()
        img.convertTo(f, CvType.CV_32FC3)
        val m3 = Mat()
        Core.merge(listOf(mask, mask, mask), m3)
        Core.multiply(f, m3, f)
        val out = Mat()
        f.convertTo(out, CvType.CV_8UC3)
        listOf(mask, f, m3).forEach { it.release() }
        return out
    }

    fun blur(img: Mat, k: Double): Mat {
        val out = Mat()
        Imgproc.GaussianBlur(img, out, Size(k, k), 0.0)
        return out
    }
}
