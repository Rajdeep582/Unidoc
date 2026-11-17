package com.rajdeep.unidoc.core.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc

/** Bitmap/file ↔ OpenCV Mat helpers that respect EXIF orientation and memory limits. */
object MatIO {
    /** Longest side kept for originals (≈ 12 MP); keeps peak memory near 50 MB per page. */
    const val MAX_ORIGINAL_SIDE = 4000

    fun readBgr(file: File, maxSide: Int = MAX_ORIGINAL_SIDE): Mat {
        OpenCv.ensureLoaded()
        val bitmap = readBitmap(file, maxSide)
        return bitmapToBgr(bitmap).also { bitmap.recycle() }
    }

    /**
     * Decodes [file] upright (EXIF applied) with the long side ≤ [maxSide] — exactly the frame the
     * processing pipeline uses, so normalized corners mean the same thing on screen and in output.
     */
    fun readBitmap(file: File, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        require(bounds.outWidth > 0) { "Unreadable image: ${file.name}" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Unreadable image: ${file.name}")
        val oriented = applyExif(bitmap, ExifInterface(file.path))
        val scaled = fit(oriented, maxSide)
        if (scaled !== oriented) oriented.recycle()
        return scaled
    }

    /** Width/height after EXIF rotation, without decoding pixels. */
    fun orientedSize(file: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, o)
        val rotation = ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val swap = rotation in setOf(
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_TRANSVERSE
        )
        return if (swap) o.outHeight to o.outWidth else o.outWidth to o.outHeight
    }

    fun bitmapToBgr(bitmap: Bitmap): Mat {
        OpenCv.ensureLoaded()
        val src = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val rgba = Mat()
        Utils.bitmapToMat(src, rgba)
        val bgr = Mat()
        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
        rgba.release()
        if (src !== bitmap) src.recycle()
        return bgr
    }

    fun writeImage(file: File, mat: Mat, jpegQuality: Int = 93) {
        OpenCv.ensureLoaded()
        file.parentFile?.mkdirs()
        val params = org.opencv.core.MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, jpegQuality)
        check(Imgcodecs.imwrite(file.path, mat, params)) { "Failed to write ${file.name}" }
        params.release()
    }

    fun copyStream(input: InputStream, dest: File) {
        dest.parentFile?.mkdirs()
        dest.outputStream().use { input.copyTo(it) }
    }

    private fun applyExif(bitmap: Bitmap, exif: ExifInterface): Bitmap {
        val m = Matrix()
        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                m.postRotate(90f)
                m.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                m.postRotate(270f)
                m.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true).also { if (it !== bitmap) bitmap.recycle() }
    }

    private fun fit(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val s = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * s).toInt(), (bitmap.height * s).toInt(), true)
    }
}
