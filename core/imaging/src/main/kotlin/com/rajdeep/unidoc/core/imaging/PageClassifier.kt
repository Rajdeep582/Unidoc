package com.rajdeep.unidoc.core.imaging

import com.rajdeep.unidoc.core.model.ScanFilter
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc

/** How a processed page should be stored (Phase 0 §5). */
enum class PageKind { TEXT_BW, GRAY, MIXED, PHOTO }

object PageClassifier {
    fun classify(processed: Mat, filter: ScanFilter): PageKind {
        if (filter == ScanFilter.BLACK_WHITE || processed.channels() == 1 && isBinary(processed)) return PageKind.TEXT_BW
        if (processed.channels() == 1 || filter == ScanFilter.GRAYSCALE) return PageKind.GRAY
        val colorful = colorfulFraction(processed)
        return when {
            colorful < 0.015 -> PageKind.GRAY
            colorful > 0.35 -> PageKind.PHOTO
            else -> PageKind.MIXED
        }
    }

    /** Share of pixels with real colour (S > 60 and V > 60) on a downscaled copy. */
    fun colorfulFraction(bgr: Mat): Double = withMats {
        val (small, _) = bgr.fitWithin(400).let { track(it.first) to it.second }
        val hsv = mat()
        Imgproc.cvtColor(small, hsv, Imgproc.COLOR_BGR2HSV)
        val mask = mat()
        Core.inRange(hsv, Scalar(0.0, 60.0, 60.0), Scalar(180.0, 255.0, 255.0), mask)
        Core.countNonZero(mask).toDouble() / (small.cols() * small.rows())
    }

    private fun isBinary(gray: Mat): Boolean = withMats {
        val (small, _) = gray.fitWithin(300).let { track(it.first) to it.second }
        val mid = mat()
        Core.inRange(small, Scalar(16.0), Scalar(239.0), mid)
        Core.countNonZero(mid) < small.total() * 0.01
    }
}
