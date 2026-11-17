package com.rajdeep.unidoc.feature.scanner.editor.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.rajdeep.unidoc.core.scan.annotation.AnnotationRenderer
import com.rajdeep.unidoc.core.scan.annotation.InkAnnotation
import com.rajdeep.unidoc.core.scan.annotation.InkTool
import com.rajdeep.unidoc.core.scan.annotation.PageAnnotation
import com.rajdeep.unidoc.core.scan.annotation.SignatureAnnotation
import com.rajdeep.unidoc.core.scan.annotation.TextAnnotation
import com.rajdeep.unidoc.feature.scanner.editor.AnnotateTool
import java.util.UUID
import kotlin.math.hypot

/** Stroke widths/sizes as a fraction of page width for slider value 0..1. */
object MarkupMetrics {
    fun pen(v: Float) = 0.002f + v * 0.01f

    fun highlight(v: Float) = 0.015f + v * 0.03f

    fun text(v: Float) = 0.022f + v * 0.04f
}

/**
 * Interactive markup overlay sized exactly to the page image. Draws with the same renderer used for
 * export. [interactive] = false shows annotations only (read view / other tools).
 */
@Composable
fun MarkupLayer(
    annotations: List<PageAnnotation>,
    tool: AnnotateTool?,
    color: Long,
    strokeValue: Float,
    onChange: (List<PageAnnotation>) -> Unit,
    onTextRequest: (x: Float, y: Float) -> Unit,
    onSignatureRequest: (x: Float, y: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val current by rememberUpdatedState(annotations)
    val inProgress = remember { mutableStateListOf<Float>() }
    var dragTarget by remember { mutableStateOf<String?>(null) }

    fun hit(list: List<PageAnnotation>, p: Offset, w: Float, h: Float): PageAnnotation? = list.lastOrNull { a ->
        when (a) {
            is InkAnnotation -> {
                val tol = a.width * w + 18f
                a.points.chunked(2).any { (x, y) -> hypot(x * w - p.x, y * h - p.y) < tol }
            }
            is TextAnnotation -> {
                val lines = a.text.lines()
                val bw = (lines.maxOf { it.length } * a.size * 0.56f) * w
                val bh = lines.size * a.size * 1.3f * w
                p.x in a.x * w..a.x * w + bw && p.y in a.y * h..a.y * h + bh
            }
            is SignatureAnnotation -> {
                val bw = a.width * w
                p.x in a.x * w..a.x * w + bw && p.y in a.y * h..a.y * h + bw / a.aspect
            }
            else -> false
        }
    }

    val gestures = if (tool == null) {
        Modifier
    } else {
        Modifier
            .pointerInput(tool, color, strokeValue) {
                detectTapGestures { p ->
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    when (tool) {
                        AnnotateTool.ERASER -> hit(current, p, w, h)?.let { target -> onChange(current.filterNot { it.id == target.id }) }
                        AnnotateTool.TEXT -> if (hit(current, p, w, h) !is TextAnnotation) onTextRequest(p.x / w, p.y / h)
                        AnnotateTool.SIGNATURE -> if (hit(current, p, w, h) !is SignatureAnnotation) onSignatureRequest(p.x / w, p.y / h)
                        else -> Unit
                    }
                }
            }
            .pointerInput(tool, color, strokeValue) {
                detectDragGestures(
                    onDragStart = { p ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        when (tool) {
                            AnnotateTool.PEN, AnnotateTool.HIGHLIGHT -> {
                                inProgress.clear()
                                inProgress.addAll(listOf(p.x / w, p.y / h))
                            }
                            AnnotateTool.TEXT, AnnotateTool.SIGNATURE -> dragTarget = hit(current, p, w, h)
                                ?.takeIf { it is TextAnnotation || it is SignatureAnnotation }?.id
                            AnnotateTool.ERASER -> hit(current, p, w, h)?.let { target ->
                                onChange(current.filterNot { it.id == target.id })
                            }
                            else -> Unit
                        }
                    },
                    onDragEnd = {
                        if ((tool == AnnotateTool.PEN || tool == AnnotateTool.HIGHLIGHT) && inProgress.size >= 4) {
                            val ink = InkAnnotation(
                                id = UUID.randomUUID().toString(),
                                tool = if (tool == AnnotateTool.PEN) InkTool.PEN else InkTool.HIGHLIGHT,
                                color = color,
                                width = if (tool ==
                                    AnnotateTool.PEN
                                ) {
                                    MarkupMetrics.pen(strokeValue)
                                } else {
                                    MarkupMetrics.highlight(strokeValue)
                                },
                                points = inProgress.toList()
                            )
                            onChange(current + ink)
                        }
                        inProgress.clear()
                        dragTarget = null
                    }
                ) { change, amount ->
                    change.consume()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    when (tool) {
                        AnnotateTool.PEN, AnnotateTool.HIGHLIGHT -> inProgress.addAll(listOf(change.position.x / w, change.position.y / h))
                        AnnotateTool.ERASER -> hit(current, change.position, w, h)?.let { target ->
                            onChange(
                                current.filterNot {
                                    it.id ==
                                        target.id
                                }
                            )
                        }
                        AnnotateTool.TEXT, AnnotateTool.SIGNATURE -> dragTarget?.let { id ->
                            onChange(
                                current.map { a ->
                                    when {
                                        a.id != id -> a
                                        a is TextAnnotation -> a.copy(
                                            x = (a.x + amount.x / w).coerceIn(0f, 0.98f),
                                            y = (a.y + amount.y / h).coerceIn(0f, 0.98f)
                                        )
                                        a is SignatureAnnotation -> a.copy(
                                            x = (a.x + amount.x / w).coerceIn(-0.2f, 1f),
                                            y = (
                                                a.y +
                                                    amount.y / h
                                                ).coerceIn(-0.2f, 1f)
                                        )
                                        else -> a
                                    }
                                }
                            )
                        }
                        else -> Unit
                    }
                }
            }
    }

    Canvas(modifier.then(gestures)) {
        val live = if (inProgress.size >= 2 && (tool == AnnotateTool.PEN || tool == AnnotateTool.HIGHLIGHT)) {
            listOf(
                InkAnnotation(
                    "live",
                    if (tool == AnnotateTool.PEN) InkTool.PEN else InkTool.HIGHLIGHT,
                    color,
                    if (tool == AnnotateTool.PEN) MarkupMetrics.pen(strokeValue) else MarkupMetrics.highlight(strokeValue),
                    inProgress.toList()
                )
            )
        } else {
            emptyList()
        }
        drawIntoCanvas { AnnotationRenderer.draw(it.nativeCanvas, size.width, size.height, annotations + live) }
    }
}
