package com.rajdeep.unidoc.feature.scanner.editor

import com.rajdeep.unidoc.core.model.ScanFilter
import com.rajdeep.unidoc.core.scan.annotation.PageAnnotation

enum class EditorTool { CROP, FILTERS, ADJUST, ROTATE, ANNOTATE, PAGES, TEXT }

enum class Adjustment { BRIGHTNESS, CONTRAST, SATURATION, SHARPNESS }

enum class AnnotateTool { PEN, HIGHLIGHT, TEXT, SIGNATURE, ERASER, WATERMARK }

data class EditorPage(
    val id: Long,
    val filter: ScanFilter = ScanFilter.AUTO,
    val rotationDegrees: Int = 0,
    /** Session page key (real scans). */
    val key: String = id.toString(),
    /** Absolute path of the rendered page; null while processing for the first time. */
    val imagePath: String? = null,
    /** True while a new render is in progress. */
    val processing: Boolean = false,
    /** Width / height of the rendered page; null until known. */
    val aspect: Float? = null,
    val annotations: List<PageAnnotation> = emptyList()
)

data class EditorUiState(
    val title: String,
    val pages: List<EditorPage>,
    val currentPage: Int = 0,
    val activeTool: EditorTool? = null,
    val adjustment: Adjustment = Adjustment.BRIGHTNESS,
    /** -1..1 per adjustment, 0 = neutral. */
    val adjustments: Map<Adjustment, Float> = Adjustment.entries.associateWith { 0f },
    val annotateTool: AnnotateTool = AnnotateTool.PEN,
    val annotateColorIndex: Int = 0,
    val strokeWidth: Float = 0.35f,
    val straighten: Float = 0f,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
) {
    val page: EditorPage? get() = pages.getOrNull(currentPage)
}

sealed interface EditorAction {
    data object Back : EditorAction

    data object Undo : EditorAction

    data object Redo : EditorAction

    data object Export : EditorAction

    data class PageChanged(val index: Int) : EditorAction

    data class ToolSelected(val tool: EditorTool?) : EditorAction

    data class FilterSelected(val filter: ScanFilter) : EditorAction

    data object ApplyFilterToAll : EditorAction

    data class AdjustmentSelected(val adjustment: Adjustment) : EditorAction

    data class AdjustmentChanged(val value: Float) : EditorAction

    data object RotateLeft : EditorAction

    data object RotateRight : EditorAction

    data object FlipHorizontal : EditorAction

    data class StraightenChanged(val degrees: Float) : EditorAction

    data class AnnotateToolSelected(val tool: AnnotateTool) : EditorAction

    data class AnnotateColorSelected(val index: Int) : EditorAction

    data class StrokeWidthChanged(val value: Float) : EditorAction

    data object NewSignature : EditorAction
}

object SampleEditor {
    val state = EditorUiState(
        title = "Offer letter — Northwind",
        pages = listOf(EditorPage(11), EditorPage(12, rotationDegrees = 0), EditorPage(13)),
        currentPage = 0,
        activeTool = EditorTool.FILTERS,
        canUndo = true
    )
}
