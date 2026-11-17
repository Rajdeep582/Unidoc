package com.rajdeep.unidoc.core.scan.annotation

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Single renderer for markup, used by both the live editor overlay (Compose native canvas) and
 * export flattening, so what you see is exactly what gets saved.
 */
object AnnotationRenderer {
    fun draw(canvas: Canvas, width: Float, height: Float, annotations: List<PageAnnotation>) {
        annotations.forEach { a ->
            when (a) {
                is InkAnnotation -> drawInk(canvas, width, height, a)
                is TextAnnotation -> drawText(canvas, width, height, a)
                is SignatureAnnotation -> drawSignature(canvas, width, height, a)
                is WatermarkAnnotation -> drawWatermark(canvas, width, height, a)
            }
        }
    }

    private fun strokePaint(color: Long, widthPx: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toInt()
        style = Paint.Style.STROKE
        strokeWidth = widthPx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private fun smoothPath(points: List<Float>, sx: Float, sy: Float, ox: Float = 0f, oy: Float = 0f): Path {
        val path = Path()
        if (points.size < 2) return path
        path.moveTo(ox + points[0] * sx, oy + points[1] * sy)
        if (points.size == 2) {
            path.lineTo(ox + points[0] * sx + 0.1f, oy + points[1] * sy)
            return path
        }
        var px = points[0]
        var py = points[1]
        var i = 2
        while (i + 1 < points.size) {
            val x = points[i]
            val y = points[i + 1]
            // Quadratic smoothing through midpoints gives clean ink at any sampling rate.
            path.quadTo(ox + px * sx, oy + py * sy, ox + (px + x) / 2 * sx, oy + (py + y) / 2 * sy)
            px = x
            py = y
            i += 2
        }
        path.lineTo(ox + px * sx, oy + py * sy)
        return path
    }

    private fun drawInk(canvas: Canvas, w: Float, h: Float, a: InkAnnotation) {
        val paint = strokePaint(a.color, a.width * w)
        if (a.tool == InkTool.HIGHLIGHT) {
            paint.alpha = 110
            paint.strokeCap = Paint.Cap.SQUARE
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        }
        canvas.drawPath(smoothPath(a.points, w, h), paint)
    }

    private fun drawText(canvas: Canvas, w: Float, h: Float, a: TextAnnotation) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = a.color.toInt()
            textSize = a.size * w
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        var y = a.y * h - paint.ascent()
        a.text.lines().forEach { line ->
            canvas.drawText(line, a.x * w, y, paint)
            y += paint.fontSpacing
        }
    }

    private fun drawSignature(canvas: Canvas, w: Float, h: Float, a: SignatureAnnotation) {
        val boxW = a.width * w
        val boxH = boxW / a.aspect
        val paint = strokePaint(a.color, (boxW * 0.012f).coerceAtLeast(1.5f))
        a.strokes.forEach { stroke -> canvas.drawPath(smoothPath(stroke, boxW, boxH, a.x * w, a.y * h), paint) }
    }

    private fun drawWatermark(canvas: Canvas, w: Float, h: Float, a: WatermarkAnnotation) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = a.color.toInt()
            alpha = (a.opacity.coerceIn(0f, 1f) * 255).toInt()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val diagonal = hypot(w, h)
        paint.textSize = 100f
        val textW = paint.measureText(a.text).coerceAtLeast(1f)
        paint.textSize = 100f * diagonal * 0.7f / textW
        canvas.save()
        canvas.rotate(Math.toDegrees(-atan2(h, w).toDouble()).toFloat(), w / 2, h / 2)
        canvas.drawText(a.text, w / 2, h / 2 - (paint.ascent() + paint.descent()) / 2, paint)
        canvas.restore()
    }
}
