package com.rajdeep.unidoc.core.imaging

import com.rajdeep.unidoc.core.model.QualityPreset
import kotlin.math.roundToInt
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfInt
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

class EncodedImage(val bytes: ByteArray, val mimeType: String, val width: Int, val height: Int, val kind: PageKind, val dpi: Int)

/**
 * Smart compression: resample to the preset DPI for the page's physical size, then pick the
 * cheapest faithful encoding for its content:
 *  - TEXT_BW → 1-bit PNG (bilevel)      ~30–90 KB per A4 page at 200 dpi
 *  - GRAY    → single-channel JPEG
 *  - MIXED/PHOTO → colour JPEG (optimized Huffman tables)
 */
object ImageEncoder {
    /** A4 long side in inches; used when the real page size is unknown. */
    const val A4_LONG_SIDE_IN = 11.69

    private fun jpegQuality(preset: QualityPreset, kind: PageKind): Int {
        val base = when (preset) {
            QualityPreset.HIGH -> 85
            QualityPreset.BALANCED -> 74
            QualityPreset.SMALL -> 58
        }
        return when (kind) {
            PageKind.PHOTO -> base + 4
            PageKind.GRAY -> base - 2
            else -> base
        }
    }

    fun targetLongSide(preset: QualityPreset, longSideInches: Double = A4_LONG_SIDE_IN): Int = (preset.dpi * longSideInches).roundToInt()

    fun encode(processed: Mat, preset: QualityPreset, kind: PageKind, longSideInches: Double = A4_LONG_SIDE_IN): EncodedImage = withMats {
        val (resized, _) = processed.fitWithin(targetLongSide(preset, longSideInches)).let { track(it.first) to it.second }
        val bytes = track(MatOfByte())
        val mime: String
        when (kind) {
            PageKind.TEXT_BW -> {
                val gray = track(resized.toGray())
                val bin = mat()
                Imgproc.threshold(gray, bin, 127.0, 255.0, Imgproc.THRESH_BINARY)
                Imgcodecs.imencode(
                    ".png",
                    bin,
                    bytes,
                    track(MatOfInt(Imgcodecs.IMWRITE_PNG_BILEVEL, 1, Imgcodecs.IMWRITE_PNG_COMPRESSION, 9))
                )
                mime = "image/png"
            }
            PageKind.GRAY -> {
                val gray = track(resized.toGray())
                Imgcodecs.imencode(".jpg", gray, bytes, jpegParams(jpegQuality(preset, kind)))
                mime = "image/jpeg"
            }
            PageKind.MIXED, PageKind.PHOTO -> {
                val bgr = track(resized.toBgr())
                Imgcodecs.imencode(".jpg", bgr, bytes, jpegParams(jpegQuality(preset, kind)))
                mime = "image/jpeg"
            }
        }
        val dpi = (maxOf(resized.cols(), resized.rows()) / longSideInches).roundToInt()
        EncodedImage(bytes.toArray(), mime, resized.cols(), resized.rows(), kind, dpi)
    }

    /** Resized, thresholded single-channel image for 1-bit PDF embedding (0 = black, 255 = white). */
    fun bilevel(processed: Mat, preset: QualityPreset, longSideInches: Double = A4_LONG_SIDE_IN): Mat = withMats {
        val (resized, _) = processed.fitWithin(targetLongSide(preset, longSideInches)).let { track(it.first) to it.second }
        val gray = track(resized.toGray())
        val bin = Mat()
        Imgproc.threshold(gray, bin, 127.0, 255.0, Imgproc.THRESH_BINARY)
        bin
    }

    private fun MatScope.jpegParams(q: Int) = track(MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, q, Imgcodecs.IMWRITE_JPEG_OPTIMIZE, 1))

    /** Lossless-ish working copy for the session (high quality JPEG / PNG for B&W). */
    fun encodeWorking(processed: Mat): Pair<ByteArray, String> = withMats {
        val bytes = track(MatOfByte())
        if (processed.channels() == 1) {
            Imgcodecs.imencode(".png", processed, bytes, track(MatOfInt(Imgcodecs.IMWRITE_PNG_COMPRESSION, 3)))
            bytes.toArray() to "png"
        } else {
            Imgcodecs.imencode(".jpg", processed, bytes, jpegParams(93))
            bytes.toArray() to "jpg"
        }
    }

    /** Small preview for thumbnails. */
    fun thumbnail(processed: Mat, longSide: Int = 480): ByteArray = withMats {
        val small = mat()
        val s = longSide.toDouble() / maxOf(processed.cols(), processed.rows())
        Imgproc.resize(
            processed,
            small,
            Size(processed.cols() * minOf(1.0, s), processed.rows() * minOf(1.0, s)),
            0.0,
            0.0,
            Imgproc.INTER_AREA
        )
        val bytes = track(MatOfByte())
        Imgcodecs.imencode(".jpg", small, bytes, jpegParams(82))
        bytes.toArray()
    }
}
