package com.rajdeep.unidoc.core.imaging

import kotlin.math.roundToInt
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object BookSplitter {
    /**
     * Splits a two-page spread at the gutter: the darkest smoothed column within the middle 40%.
     * Returns the input as a single page when it is portrait (not a spread).
     */
    fun split(spread: Mat): List<Mat> = withMats {
        if (spread.cols() < spread.rows() * 1.05) return@withMats listOf(spread.clone())
        val gray = track(spread.toGray())
        val profile = mat()
        Core.reduce(gray, profile, 0, Core.REDUCE_AVG, CvType.CV_32F)
        val smooth = mat()
        Imgproc.GaussianBlur(profile, smooth, Size((spread.cols() / 50).coerceAtLeast(3).odd().toDouble(), 1.0), 0.0)
        val from = (spread.cols() * 0.3).roundToInt()
        val to = (spread.cols() * 0.7).roundToInt()
        val roi = smooth.submat(0, 1, from, to)
        val gutter = from + Core.minMaxLoc(roi).minLoc.x.roundToInt()
        roi.release()
        listOf(
            spread.submat(Rect(0, 0, gutter, spread.rows())).clone(),
            spread.submat(Rect(gutter, 0, spread.cols() - gutter, spread.rows())).clone()
        )
    }
}

object IdCardComposer {
    const val CARD_ASPECT = 85.6 / 53.98

    /** Places front (and back) at true ID-1 size on a white A4 page at [dpi]. */
    fun compose(front: Mat, back: Mat?, dpi: Int = 300): Mat = withMats {
        val pageW = (8.27 * dpi).roundToInt()
        val pageH = (11.69 * dpi).roundToInt()
        val page = Mat(pageH, pageW, CvType.CV_8UC3, Scalar(255.0, 255.0, 255.0))
        val cardW = (85.6 / 25.4 * dpi).roundToInt()
        val cardH = (53.98 / 25.4 * dpi).roundToInt()
        val x = (pageW - cardW) / 2
        val cards = listOfNotNull(front, back)
        val gap = (0.6 * dpi).roundToInt()
        val totalH = cards.size * cardH + (cards.size - 1) * gap
        var y = (pageH * 0.38 - totalH / 2.0).roundToInt().coerceAtLeast(gap)
        for (c in cards) {
            val bgr = track(c.toBgr())
            val resized = mat()
            Imgproc.resize(bgr, resized, Size(cardW.toDouble(), cardH.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            val dst = page.submat(Rect(x, y, cardW, cardH))
            resized.copyTo(dst)
            dst.release()
            y += cardH + gap
        }
        page
    }
}
