package com.rajdeep.unidoc.core.scan.annotation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Vector markup stored per page, in coordinates normalized to the rendered page (0..1 on both
 * axes; sizes are fractions of the page width). Drawn live in the editor and flattened on export.
 */
@Serializable
sealed interface PageAnnotation {
    val id: String
}

enum class InkTool { PEN, HIGHLIGHT }

@Serializable
@SerialName("ink")
data class InkAnnotation(
    override val id: String,
    val tool: InkTool,
    /** ARGB. */
    val color: Long,
    /** Stroke width as a fraction of page width. */
    val width: Float,
    /** Flattened x0,y0,x1,y1,… */
    val points: List<Float>
) : PageAnnotation

@Serializable
@SerialName("text")
data class TextAnnotation(
    override val id: String,
    val text: String,
    /** Top-left of the text box. */
    val x: Float,
    val y: Float,
    /** Font size as a fraction of page width. */
    val size: Float,
    val color: Long
) : PageAnnotation

@Serializable
@SerialName("signature")
data class SignatureAnnotation(
    override val id: String,
    /** Strokes in the signature's own box, normalized 0..1 (x,y pairs). */
    val strokes: List<List<Float>>,
    val x: Float,
    val y: Float,
    /** Box width as fraction of page width; height = width / aspect. */
    val width: Float,
    val aspect: Float,
    val color: Long
) : PageAnnotation

@Serializable
@SerialName("watermark")
data class WatermarkAnnotation(override val id: String, val text: String, val opacity: Float, val color: Long) : PageAnnotation
