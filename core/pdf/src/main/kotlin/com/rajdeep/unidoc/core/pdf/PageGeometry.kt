package com.rajdeep.unidoc.core.pdf

import com.rajdeep.unidoc.core.model.PageMargin
import com.rajdeep.unidoc.core.model.PageOrientation
import com.rajdeep.unidoc.core.model.PageSize

/** Rectangle in PDF points (origin bottom-left). */
data class PdfRect(val x: Float, val y: Float, val width: Float, val height: Float)

data class PageLayout(val pageWidth: Float, val pageHeight: Float, val image: PdfRect)

/** Page box + image placement for an image of [imageW]×[imageH] px at [dpi]. */
object PageGeometry {
    private const val POINTS_PER_INCH = 72f

    fun layout(imageW: Int, imageH: Int, dpi: Int, size: PageSize, orientation: PageOrientation, margin: PageMargin): PageLayout {
        val m = when (margin) {
            PageMargin.NONE -> 0f
            PageMargin.SMALL -> 18f
            PageMargin.LARGE -> 36f
        }
        val naturalW = imageW / dpi.toFloat() * POINTS_PER_INCH
        val naturalH = imageH / dpi.toFloat() * POINTS_PER_INCH
        if (size == PageSize.AUTO) {
            return PageLayout(naturalW + 2 * m, naturalH + 2 * m, PdfRect(m, m, naturalW, naturalH))
        }
        val (bw, bh) = when (size) {
            PageSize.A4 -> 595.28f to 841.89f
            PageSize.LETTER -> 612f to 792f
            PageSize.LEGAL -> 612f to 1008f
            PageSize.AUTO -> error("handled above")
        }
        val landscape = when (orientation) {
            PageOrientation.AUTO -> imageW > imageH
            PageOrientation.PORTRAIT -> false
            PageOrientation.LANDSCAPE -> true
        }
        val pw = if (landscape) bh else bw
        val ph = if (landscape) bw else bh
        val availW = pw - 2 * m
        val availH = ph - 2 * m
        val scale = minOf(availW / imageW, availH / imageH)
        val w = imageW * scale
        val h = imageH * scale
        return PageLayout(pw, ph, PdfRect((pw - w) / 2, (ph - h) / 2, w, h))
    }
}
