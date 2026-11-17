package com.rajdeep.unidoc.feature.scanner.capture.camera

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.rajdeep.unidoc.core.imaging.DocumentDetector
import com.rajdeep.unidoc.core.imaging.PerspectiveWarper
import com.rajdeep.unidoc.core.imaging.Quad
import com.rajdeep.unidoc.core.scan.OpenCv
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat

/** One analyzed preview frame, in upright (display-rotated) orientation. */
data class FrameResult(
    /** Normalized to the upright frame; null when no document found. */
    val quad: Quad?,
    val areaFraction: Double,
    val sharpness: Double,
    val brightness: Double
)

/**
 * Runs document detection on the luminance plane of each preview frame (no colour conversion),
 * or barcode scanning in QR mode. Frames are dropped while busy (KEEP_ONLY_LATEST).
 */
class FrameAnalyzer(
    private val qrMode: () -> Boolean,
    private val onFrame: (FrameResult) -> Unit,
    private val onBarcode: (Barcode) -> Unit
) : ImageAnalysis.Analyzer {
    // Live preview: coarse quad only (sub-pixel refinement runs on the captured photo).
    private val detector = DocumentDetector(workingSize = 400, refine = false)
    private val barcodeScanner by lazy { BarcodeScanning.getClient() }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(image: ImageProxy) {
        if (qrMode()) {
            val media = image.image
            if (media == null) {
                image.close()
                return
            }
            barcodeScanner.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
                .addOnSuccessListener { codes -> codes.firstOrNull { it.rawValue != null }?.let(onBarcode) }
                .addOnCompleteListener { image.close() }
            return
        }
        try {
            OpenCv.ensureLoaded()
            val gray = lumaToMat(image)
            val upright = PerspectiveWarper.rotate(gray, image.imageInfo.rotationDegrees)
            gray.release()
            val brightness = Core.mean(upright).`val`[0]
            val result = detector.detect(upright)
            val sharpness = DocumentDetector.sharpness(upright)
            val w = upright.cols()
            val h = upright.rows()
            upright.release()
            onFrame(
                FrameResult(
                    quad = result?.quad?.normalized(w, h),
                    areaFraction = result?.quad?.let { it.area() / (w.toDouble() * h) } ?: 0.0,
                    sharpness = sharpness,
                    brightness = brightness
                )
            )
        } finally {
            image.close()
        }
    }

    private fun lumaToMat(image: ImageProxy): Mat {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate().apply { rewind() }
        val rowStride = plane.rowStride
        val w = image.width
        val h = image.height
        val data = ByteArray(w * h)
        if (rowStride == w) {
            buffer.get(data, 0, minOf(data.size, buffer.remaining()))
        } else {
            val row = ByteArray(rowStride)
            for (y in 0 until h) {
                val len = minOf(rowStride, buffer.remaining())
                buffer.get(row, 0, len)
                System.arraycopy(row, 0, data, y * w, minOf(w, len))
            }
        }
        val mat = Mat(h, w, CvType.CV_8UC1)
        mat.put(0, 0, data)
        return mat
    }
}
