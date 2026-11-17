package com.rajdeep.unidoc.core.scan

import com.rajdeep.unidoc.core.imaging.PageKind
import com.rajdeep.unidoc.core.imaging.Pt
import com.rajdeep.unidoc.core.imaging.Quad
import com.rajdeep.unidoc.core.model.ScanFilter
import com.rajdeep.unidoc.core.model.ScanMode
import com.rajdeep.unidoc.core.scan.annotation.PageAnnotation
import kotlinx.serialization.Serializable

/** Which part of the warped original a page shows (book spreads produce LEFT + RIGHT pages). */
enum class PageRegion { FULL, LEFT, RIGHT }

@Serializable
data class SessionPage(
    val id: String,
    /** File names inside the session folder. */
    val original: String,
    val backOriginal: String? = null,
    /** Normalized corners TL,TR,BR,BL as x0,y0,…; null = auto-detect; empty = whole image. */
    val corners: List<Double>? = null,
    val backCorners: List<Double>? = null,
    /**
     * Side bends for the front corners: dx,dy (normalized) of the top, right, bottom and left side
     * midpoints, relative to their perspective-correct straight-edge positions. null = straight.
     */
    val bends: List<Double>? = null,
    val filter: ScanFilter = ScanFilter.AUTO,
    val rotation: Int = 0,
    val region: PageRegion = PageRegion.FULL,
    val processed: String? = null,
    val thumbnail: String? = null,
    val kind: PageKind? = null,
    val version: Int = 0,
    val ocr: String? = null,
    val warp: Boolean = true,
    /** Bumped on every edit; a render is applied only if no newer edit arrived meanwhile. */
    val editStamp: Int = 0,
    /** Manual adjustments, each -1..1 (0 = neutral), applied after the filter. */
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val sharpness: Float = 0f,
    val flipHorizontal: Boolean = false,
    /** Fine straighten in degrees (-45..45). */
    val tilt: Float = 0f,
    val annotations: List<PageAnnotation> = emptyList(),
    /** [editStamp] the current [processed] image was rendered from. */
    val renderedStamp: Int = -1
) {
    val isRendering: Boolean get() = processed == null || renderedStamp != editStamp
    val quad: Quad? get() = corners?.toQuad()

    /** Everything that changes the rendered pixels (annotations are an overlay and don't). */
    val renderInputs: List<Any?>
        get() = listOf(
            original, backOriginal, corners, backCorners, bends, filter, rotation, region, warp,
            brightness, contrast, saturation, sharpness, flipHorizontal, tilt
        )

    val hasAdjustments: Boolean get() = brightness != 0f || contrast != 0f || saturation != 0f || sharpness != 0f
    val isProcessed: Boolean get() = processed != null
}

@Serializable
data class ScanSession(
    val id: String,
    val createdAtMillis: Long,
    val mode: ScanMode,
    val title: String,
    val pages: List<SessionPage> = emptyList()
)

internal fun List<Double>.toQuad(): Quad? = if (size == 8) {
    Quad(Pt(this[0], this[1]), Pt(this[2], this[3]), Pt(this[4], this[5]), Pt(this[6], this[7]))
} else {
    null
}

internal fun Quad.toList(): List<Double> = points.flatMap { listOf(it.x, it.y) }
