package com.rajdeep.unidoc.core.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import com.rajdeep.unidoc.core.imaging.PageClassifier
import com.rajdeep.unidoc.core.imaging.PageKind
import com.rajdeep.unidoc.core.scan.MatIO
import com.rajdeep.unidoc.core.scan.SessionPage
import com.rajdeep.unidoc.core.scan.annotation.AnnotationRenderer
import com.rajdeep.unidoc.core.scan.annotation.InkAnnotation
import com.rajdeep.unidoc.core.scan.annotation.SignatureAnnotation
import com.rajdeep.unidoc.core.scan.annotation.TextAnnotation
import com.rajdeep.unidoc.core.scan.annotation.WatermarkAnnotation
import java.io.File
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/** A page ready for encoding: rendered pixels with markup burned in, plus its storage class. */
class RasterPage(val mat: Mat, val kind: PageKind) {
    fun release() = mat.release()
}

object PageRasterizer {
    fun rasterize(processed: File, page: SessionPage): RasterPage {
        val mat = MatIO.readBgr(processed, maxSide = Int.MAX_VALUE)
        var kind = page.kind ?: PageClassifier.classify(mat, page.filter)
        if (page.annotations.isNotEmpty()) {
            flatten(mat, page)
            if (kind == PageKind.TEXT_BW && page.annotations.any { it.isColored() }) kind = PageKind.MIXED
        }
        return RasterPage(mat, kind)
    }

    private fun flatten(mat: Mat, page: SessionPage) {
        val rgba = Mat()
        Imgproc.cvtColor(mat, rgba, Imgproc.COLOR_BGR2RGBA)
        val bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(rgba, bmp)
        AnnotationRenderer.draw(Canvas(bmp), bmp.width.toFloat(), bmp.height.toFloat(), page.annotations)
        Utils.bitmapToMat(bmp, rgba)
        Imgproc.cvtColor(rgba, mat, Imgproc.COLOR_RGBA2BGR)
        rgba.release()
        bmp.recycle()
    }

    private fun Long.isGrayish(): Boolean {
        val r = (this shr 16 and 0xFF).toInt()
        val g = (this shr 8 and 0xFF).toInt()
        val b = (this and 0xFF).toInt()
        return maxOf(r, g, b) - minOf(r, g, b) < 24
    }

    private fun com.rajdeep.unidoc.core.scan.annotation.PageAnnotation.isColored(): Boolean = when (this) {
        is InkAnnotation -> !color.isGrayish()
        is TextAnnotation -> !color.isGrayish()
        is SignatureAnnotation -> !color.isGrayish()
        is WatermarkAnnotation -> true // translucent overlay produces gray levels
    }
}
