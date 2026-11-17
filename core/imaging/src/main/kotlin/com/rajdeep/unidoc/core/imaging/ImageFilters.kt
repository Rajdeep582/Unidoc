package com.rajdeep.unidoc.core.imaging

import com.rajdeep.unidoc.core.model.ScanFilter
import kotlin.math.pow
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Document enhancement filters. Input/outputs are 8-bit BGR (B&W/Gray return 1-channel).
 *
 * The core trick is illumination normalization: estimate the paper background per channel
 * (dilate removes ink, median smooths), then divide. That removes shadows, vignetting and
 * colour casts in one step and leaves paper at ~255 with ink untouched.
 */
object ImageFilters {

    fun apply(srcBgr: Mat, filter: ScanFilter): Mat = when (filter) {
        ScanFilter.ORIGINAL -> srcBgr.clone()
        ScanFilter.AUTO -> magic(srcBgr)
        ScanFilter.BLACK_WHITE -> blackWhite(srcBgr)
        ScanFilter.GRAYSCALE -> grayscale(srcBgr)
        ScanFilter.ENHANCED -> enhanced(srcBgr)
        ScanFilter.LIGHTEN -> lighten(srcBgr)
        ScanFilter.WHITEBOARD -> whiteboard(srcBgr)
    }

    /** Shadow-free, white-balanced, gently sharpened colour. */
    fun magic(src: Mat): Mat = withMats {
        val flat = track(normalizeIlluminationColor(src))
        unsharp(flat, amount = 0.5, sigma = 1.0)
    }

    fun blackWhite(src: Mat): Mat = withMats {
        val gray = track(src.toGray())
        val flat = track(normalizeIllumination(gray))
        sauvola(flat)
    }

    fun grayscale(src: Mat): Mat = withMats {
        val gray = track(src.toGray())
        val flat = track(normalizeIllumination(gray))
        val stretched = track(levels(flat, black = 20, white = 245))
        unsharp(stretched, amount = 0.4, sigma = 1.0)
    }

    fun enhanced(src: Mat): Mat = withMats {
        val flat = track(normalizeIlluminationColor(src))
        val clahe = track(claheColor(flat))
        val sat = track(saturate(clahe, 1.25))
        unsharp(sat, amount = 0.6, sigma = 1.0)
    }

    fun lighten(src: Mat): Mat = withMats {
        val flat = track(normalizeIlluminationColor(src))
        gamma(flat, 0.8)
    }

    fun whiteboard(src: Mat): Mat = withMats {
        val flat = track(normalizeIlluminationColor(src, strong = true))
        val sat = track(saturate(flat, 1.6))
        levels(sat, black = 30, white = 215)
    }

    /**
     * Manual adjustments, each in -1..1: brightness ±60 levels, contrast ×0.4..1.6,
     * saturation ×0..2 (colour only), sharpness = unsharp mask (+) or gentle blur (−).
     */
    fun adjust(src: Mat, brightness: Float, contrast: Float, saturation: Float, sharpness: Float): Mat = withMats {
        var cur: Mat = track(src.clone())
        if (brightness != 0f || contrast != 0f) {
            val alpha = 1.0 + contrast * 0.6
            val beta = brightness * 60.0 + (1 - alpha) * 128
            val out = mat()
            cur.convertTo(out, -1, alpha, beta)
            cur = out
        }
        if (saturation != 0f && cur.channels() == 3) cur = track(saturate(cur, (1.0 + saturation).coerceAtLeast(0.0)))
        if (sharpness > 0f) {
            cur = track(unsharp(cur, amount = sharpness * 1.2, sigma = 1.2))
        } else if (sharpness < 0f) {
            val out = mat()
            Imgproc.GaussianBlur(cur, out, Size(0.0, 0.0), -sharpness * 2.0 + 0.1)
            cur = out
        }
        cur.clone()
    }

    // ---- building blocks ----

    /** Background-divide on one 8-bit channel. */
    fun normalizeIllumination(gray: Mat, strong: Boolean = false): Mat = withMats {
        val longest = maxOf(gray.cols(), gray.rows())
        // Estimate background at reduced size for speed; kernel ≈ 1/30 of the page.
        val factor = (longest / 800.0).coerceAtLeast(1.0)
        val small = mat()
        Imgproc.resize(gray, small, Size(gray.cols() / factor, gray.rows() / factor), 0.0, 0.0, Imgproc.INTER_AREA)
        val k = (maxOf(small.cols(), small.rows()) / if (strong) 20 else 30).coerceIn(5, 61).odd()
        val dilated = mat()
        Imgproc.dilate(small, dilated, track(Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(k.toDouble(), k.toDouble()))))
        val bgSmall = mat()
        Imgproc.medianBlur(dilated, bgSmall, (k * 2 + 1).coerceAtMost(255).odd())
        val bg = mat()
        Imgproc.resize(bgSmall, bg, gray.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
        val out = Mat()
        Core.divide(gray, bg, out, 255.0)
        out
    }

    fun normalizeIlluminationColor(bgr: Mat, strong: Boolean = false): Mat = withMats {
        val channels = ArrayList<Mat>()
        Core.split(bgr, channels)
        channels.forEach { track(it) }
        val flat = channels.map { track(normalizeIllumination(it, strong)) }
        val out = Mat()
        Core.merge(flat, out)
        out
    }

    /**
     * Sauvola adaptive binarization — robust to uneven lighting and faint text.
     * T = m · (1 + k · (s / R − 1)) over a window ≈ page/40.
     */
    fun sauvola(gray: Mat, k: Double = 0.2, r: Double = 128.0): Mat = withMats {
        val w = (maxOf(gray.cols(), gray.rows()) / 40).coerceIn(15, 101).odd().toDouble()
        val f = mat()
        gray.convertTo(f, CvType.CV_32F)
        val mean = mat()
        Imgproc.boxFilter(f, mean, CvType.CV_32F, Size(w, w))
        val sq = mat()
        Core.multiply(f, f, sq)
        val sqMean = mat()
        Imgproc.boxFilter(sq, sqMean, CvType.CV_32F, Size(w, w))
        val meanSq = mat()
        Core.multiply(mean, mean, meanSq)
        val variance = mat()
        Core.subtract(sqMean, meanSq, variance)
        Core.max(variance, Scalar(0.0), variance)
        val std = mat()
        Core.sqrt(variance, std)
        // thresh = mean * (1 + k*(std/r - 1))
        val t = mat()
        Core.multiply(std, Scalar(k / r), t)
        Core.add(t, Scalar(1.0 - k), t)
        Core.multiply(mean, t, t)
        val mask = mat()
        Core.compare(f, t, mask, Core.CMP_GT)
        // Sauvola hollows out large solid ink areas (headers, logos, photos); force clearly dark
        // pixels to black so they stay filled.
        val dark = mat()
        Core.compare(f, Scalar(110.0), dark, Core.CMP_LT)
        mask.setTo(Scalar(0.0), dark)
        mask.clone()
    }

    fun unsharp(src: Mat, amount: Double, sigma: Double): Mat = withMats {
        val blur = mat()
        Imgproc.GaussianBlur(src, blur, Size(0.0, 0.0), sigma)
        val out = Mat()
        Core.addWeighted(src, 1 + amount, blur, -amount, 0.0, out)
        out
    }

    fun claheColor(bgr: Mat): Mat = withMats {
        val lab = mat()
        Imgproc.cvtColor(bgr, lab, Imgproc.COLOR_BGR2Lab)
        val ch = ArrayList<Mat>()
        Core.split(lab, ch)
        ch.forEach { track(it) }
        Imgproc.createCLAHE(1.8, Size(8.0, 8.0)).apply(ch[0], ch[0])
        Core.merge(ch, lab)
        val out = Mat()
        Imgproc.cvtColor(lab, out, Imgproc.COLOR_Lab2BGR)
        out
    }

    fun saturate(bgr: Mat, factor: Double): Mat = withMats {
        val hsv = mat()
        Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV)
        val ch = ArrayList<Mat>()
        Core.split(hsv, ch)
        ch.forEach { track(it) }
        ch[1].convertTo(ch[1], -1, factor, 0.0)
        Core.merge(ch, hsv)
        val out = Mat()
        Imgproc.cvtColor(hsv, out, Imgproc.COLOR_HSV2BGR)
        out
    }

    /** Linear levels: maps [black]..[white] to 0..255 with clipping. */
    fun levels(src: Mat, black: Int, white: Int): Mat {
        val lut = Mat(1, 256, CvType.CV_8U)
        val data = ByteArray(256) { i -> (((i - black) * 255.0 / (white - black)).coerceIn(0.0, 255.0)).toInt().toByte() }
        lut.put(0, 0, data)
        val out = Mat()
        Core.LUT(src, lut, out)
        lut.release()
        return out
    }

    fun gamma(src: Mat, g: Double): Mat {
        val lut = Mat(1, 256, CvType.CV_8U)
        lut.put(0, 0, ByteArray(256) { i -> (255.0 * (i / 255.0).pow(g)).toInt().coerceIn(0, 255).toByte() })
        val out = Mat()
        Core.LUT(src, lut, out)
        lut.release()
        return out
    }
}
