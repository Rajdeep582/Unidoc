package com.rajdeep.unidoc.feature.scanner.editor.review

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rajdeep.unidoc.core.data.LibraryRepository
import com.rajdeep.unidoc.core.scan.ScanRepository
import com.rajdeep.unidoc.core.scan.ScanSession
import com.rajdeep.unidoc.core.scan.SessionPage
import com.rajdeep.unidoc.core.scan.annotation.PageAnnotation
import com.rajdeep.unidoc.core.scan.annotation.SignatureAnnotation
import com.rajdeep.unidoc.core.scan.annotation.SignatureStore
import com.rajdeep.unidoc.core.scan.annotation.TextAnnotation
import com.rajdeep.unidoc.core.scan.annotation.WatermarkAnnotation
import com.rajdeep.unidoc.core.scan.ocr.OcrResult
import com.rajdeep.unidoc.feature.scanner.editor.Adjustment
import com.rajdeep.unidoc.feature.scanner.editor.AnnotateTool
import com.rajdeep.unidoc.feature.scanner.editor.EditorAction
import com.rajdeep.unidoc.feature.scanner.editor.EditorPage
import com.rajdeep.unidoc.feature.scanner.editor.EditorTool
import com.rajdeep.unidoc.feature.scanner.editor.EditorUiState
import com.rajdeep.unidoc.feature.scanner.editor.components.AnnotateColors
import com.rajdeep.unidoc.feature.scanner.editor.navigation.ReviewRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface OcrSheetState {
    data object Running : OcrSheetState

    data class Ready(val result: OcrResult) : OcrSheetState

    data class Failed(val message: String) : OcrSheetState
}

/** Dialogs/sheets owned by the review screen. */
sealed interface ReviewDialog {
    data class AddText(val x: Float, val y: Float) : ReviewDialog

    data class Signature(val x: Float, val y: Float) : ReviewDialog

    data object Watermark : ReviewDialog
}

data class ReviewUiState(
    val editor: EditorUiState?,
    val ocr: OcrSheetState? = null,
    val dialog: ReviewDialog? = null,
    val missing: Boolean = false
)

sealed interface ReviewEffect {
    data class OpenCrop(val sessionId: String, val pageKey: String) : ReviewEffect

    data class OpenPages(val sessionId: String) : ReviewEffect

    data class Export(val sessionId: String) : ReviewEffect

    data class Message(val text: String) : ReviewEffect

    data object Close : ReviewEffect
}

private data class LocalState(
    val currentPage: Int = 0,
    val tool: EditorTool? = EditorTool.FILTERS,
    val adjustment: Adjustment = Adjustment.BRIGHTNESS,
    val annotateTool: AnnotateTool = AnnotateTool.PEN,
    val colorIndex: Int = 0,
    val strokeWidth: Float = 0.35f,
    /** Slider values not yet committed (pageKey → values). */
    val pendingAdjust: Map<String, Map<Adjustment, Float>> = emptyMap(),
    val pendingTilt: Map<String, Float> = emptyMap(),
    val ocr: OcrSheetState? = null,
    val dialog: ReviewDialog? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

@OptIn(FlowPreview::class)
@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedState: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val repository: ScanRepository,
    private val library: LibraryRepository,
    private val signatures: SignatureStore
) : ViewModel() {
    val sessionId: String = savedState.toRoute<ReviewRoute>().sessionId

    private val local = MutableStateFlow(LocalState(currentPage = savedState[KEY_PAGE] ?: 0))
    private val _effects = Channel<ReviewEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()
    private val aspects = ConcurrentHashMap<String, Float>()

    // ---- undo / redo over page edit state ----
    private val undo = ArrayDeque<List<SessionPage>>()
    private val redo = ArrayDeque<List<SessionPage>>()
    private var lastEditState: List<SessionPage>? = null
    private var restoring = false

    private var adjustJob: Job? = null
    private var tiltJob: Job? = null

    val uiState: StateFlow<ReviewUiState> = combine(repository.observe(sessionId), local) { session, l ->
        if (session == null) return@combine ReviewUiState(editor = null, missing = true)
        ReviewUiState(editor = session.toEditor(l), ocr = l.ocr, dialog = l.dialog)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState(editor = null))

    init {
        viewModelScope.launch { repository.ensureRendered(sessionId) }
        viewModelScope.launch {
            repository.observe(sessionId).filterNotNull().collect { trackHistory(it) }
        }
        viewModelScope.launch {
            repository.observe(sessionId).filterNotNull().debounce(600).collect { runCatching { library.sync(it) } }
        }
    }

    private fun SessionPage.editState() =
        copy(processed = null, thumbnail = null, kind = null, version = 0, editStamp = 0, renderedStamp = -1, ocr = null)

    private fun trackHistory(session: ScanSession) {
        val state = session.pages.map { it.editState() }
        val last = lastEditState
        if (last != null && state != last) {
            if (restoring) {
                restoring = false
            } else {
                undo.addLast(last)
                if (undo.size > MAX_HISTORY) undo.removeFirst()
                redo.clear()
            }
        }
        lastEditState = state
        local.update { it.copy(canUndo = undo.isNotEmpty(), canRedo = redo.isNotEmpty()) }
    }

    private fun applyHistory(from: ArrayDeque<List<SessionPage>>, to: ArrayDeque<List<SessionPage>>) {
        val target = from.removeLastOrNull() ?: return
        lastEditState?.let { to.addLast(it) }
        restoring = true
        viewModelScope.launch { repository.restorePages(sessionId, target) }
    }

    private fun aspectOf(path: String): Float? = aspects[path] ?: run {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        if (o.outWidth > 0) (o.outWidth.toFloat() / o.outHeight).also { aspects[path] = it } else null
    }

    private fun ScanSession.toEditor(l: LocalState): EditorUiState {
        val pages = pages.map { p ->
            val path = p.processed?.let { repository.file(id, it).absolutePath }
            EditorPage(
                id = p.id.hashCode().toLong(),
                key = p.id,
                filter = p.filter,
                imagePath = path,
                processing = p.isRendering,
                aspect = path?.let(::aspectOf),
                annotations = p.annotations
            )
        }
        val index = l.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        val page = this.pages.getOrNull(index)
        val adjust = page?.let { p ->
            l.pendingAdjust[p.id] ?: mapOf(
                Adjustment.BRIGHTNESS to p.brightness,
                Adjustment.CONTRAST to p.contrast,
                Adjustment.SATURATION to p.saturation,
                Adjustment.SHARPNESS to p.sharpness
            )
        } ?: Adjustment.entries.associateWith { 0f }
        return EditorUiState(
            title = title,
            pages = pages,
            currentPage = index,
            activeTool = l.tool,
            adjustment = l.adjustment,
            adjustments = adjust,
            annotateTool = l.annotateTool,
            annotateColorIndex = l.colorIndex,
            strokeWidth = l.strokeWidth,
            straighten = page?.let { l.pendingTilt[it.id] ?: it.tilt } ?: 0f,
            canUndo = l.canUndo,
            canRedo = l.canRedo
        )
    }

    private val currentPage: SessionPage?
        get() {
            val editor = uiState.value.editor ?: return null
            val key = editor.pages.getOrNull(editor.currentPage)?.key ?: return null
            return repository.observe(sessionId).value?.pages?.firstOrNull { it.id == key }
        }

    val annotationColor: Long get() = AnnotateColors[local.value.colorIndex].toArgb().toLong() and 0xFFFFFFFFL

    fun onAction(action: EditorAction) {
        when (action) {
            EditorAction.Back -> _effects.trySend(ReviewEffect.Close)
            is EditorAction.PageChanged -> local.update { it.copy(currentPage = action.index) }
            is EditorAction.ToolSelected -> when (action.tool) {
                EditorTool.CROP -> currentPage?.let { _effects.trySend(ReviewEffect.OpenCrop(sessionId, it.id)) }
                EditorTool.TEXT -> recognize()
                EditorTool.PAGES -> _effects.trySend(ReviewEffect.OpenPages(sessionId))
                else -> local.update { it.copy(tool = action.tool) }
            }
            is EditorAction.FilterSelected -> launchEdit { repository.setFilter(sessionId, it.id, action.filter) }
            EditorAction.ApplyFilterToAll -> currentPage?.let { p ->
                viewModelScope.launch { repository.applyFilterToAll(sessionId, p.filter) }
                _effects.trySend(ReviewEffect.Message("Applying to all pages"))
            }
            is EditorAction.AdjustmentSelected -> local.update { it.copy(adjustment = action.adjustment) }
            is EditorAction.AdjustmentChanged -> adjust(action.value)
            EditorAction.RotateLeft -> launchEdit { repository.rotate(sessionId, it.id, -90) }
            EditorAction.RotateRight -> launchEdit { repository.rotate(sessionId, it.id, 90) }
            EditorAction.FlipHorizontal -> launchEdit { repository.toggleFlip(sessionId, it.id) }
            is EditorAction.StraightenChanged -> tilt(action.degrees)
            is EditorAction.AnnotateToolSelected -> {
                local.update { it.copy(annotateTool = action.tool) }
                if (action.tool == AnnotateTool.WATERMARK) local.update { it.copy(dialog = ReviewDialog.Watermark) }
            }
            is EditorAction.AnnotateColorSelected -> local.update { it.copy(colorIndex = action.index) }
            is EditorAction.StrokeWidthChanged -> local.update { it.copy(strokeWidth = action.value) }
            EditorAction.Undo -> applyHistory(undo, redo)
            EditorAction.Redo -> applyHistory(redo, undo)
            EditorAction.Export -> _effects.trySend(ReviewEffect.Export(sessionId))
            EditorAction.NewSignature -> newSignature()
        }
    }

    private fun adjust(value: Float) {
        val page = currentPage ?: return
        val adjustment = local.value.adjustment
        val values = (uiState.value.editor?.adjustments ?: emptyMap()) + (adjustment to value)
        local.update { it.copy(pendingAdjust = it.pendingAdjust + (page.id to values)) }
        adjustJob?.cancel()
        adjustJob = viewModelScope.launch {
            delay(COMMIT_DELAY)
            repository.setAdjustments(
                sessionId,
                page.id,
                values[Adjustment.BRIGHTNESS] ?: 0f,
                values[Adjustment.CONTRAST] ?: 0f,
                values[Adjustment.SATURATION] ?: 0f,
                values[Adjustment.SHARPNESS] ?: 0f
            )
            local.update { it.copy(pendingAdjust = it.pendingAdjust - page.id) }
        }
    }

    private fun tilt(degrees: Float) {
        val page = currentPage ?: return
        local.update { it.copy(pendingTilt = it.pendingTilt + (page.id to degrees)) }
        tiltJob?.cancel()
        tiltJob = viewModelScope.launch {
            delay(COMMIT_DELAY)
            repository.setTilt(sessionId, page.id, degrees)
            local.update { it.copy(pendingTilt = it.pendingTilt - page.id) }
        }
    }

    // ---- markup ----

    fun setAnnotations(pageKey: String, annotations: List<PageAnnotation>) {
        viewModelScope.launch { repository.setAnnotations(sessionId, pageKey, annotations) }
    }

    fun requestText(x: Float, y: Float) = local.update { it.copy(dialog = ReviewDialog.AddText(x, y)) }

    fun addText(text: String) {
        val d = local.value.dialog as? ReviewDialog.AddText ?: return
        val page = currentPage ?: return
        dismissDialog()
        if (text.isBlank()) return
        val annotation =
            TextAnnotation(
                UUID.randomUUID().toString(),
                text.trim(),
                d.x,
                d.y,
                MarkupMetrics.text(local.value.strokeWidth),
                annotationColor
            )
        setAnnotations(page.id, page.annotations + annotation)
    }

    fun requestSignature(x: Float, y: Float) {
        val saved = signatures.current()
        if (saved != null) {
            placeSignature(saved.strokes, saved.aspect, x, y)
        } else {
            local.update { it.copy(dialog = ReviewDialog.Signature(x, y)) }
        }
    }

    fun newSignature() = local.update { it.copy(dialog = ReviewDialog.Signature(0.3f, 0.75f)) }

    fun useSignature(strokes: List<List<Float>>, aspect: Float) {
        val d = local.value.dialog as? ReviewDialog.Signature ?: return
        dismissDialog()
        signatures.save(strokes, aspect)
        placeSignature(strokes, aspect, d.x, d.y)
    }

    private fun placeSignature(strokes: List<List<Float>>, aspect: Float, x: Float, y: Float) {
        val page = currentPage ?: return
        val width = 0.32f
        val sig =
            SignatureAnnotation(
                UUID.randomUUID().toString(),
                strokes,
                (x - width / 2).coerceIn(0f, 1f - width),
                y,
                width,
                aspect,
                annotationColor
            )
        setAnnotations(page.id, page.annotations + sig)
    }

    fun applyWatermark(text: String, opacity: Float) {
        dismissDialog()
        viewModelScope.launch {
            repository.setWatermark(
                sessionId,
                text.trim().takeIf { it.isNotEmpty() }?.let { WatermarkAnnotation("watermark", it, opacity, 0xFF7A7F99) }
            )
        }
    }

    fun dismissDialog() = local.update { it.copy(dialog = null) }

    // ---- OCR ----

    fun dismissOcr() = local.update { it.copy(ocr = null) }

    private fun recognize() {
        val page = currentPage ?: return
        local.update { it.copy(ocr = OcrSheetState.Running) }
        viewModelScope.launch {
            val state = runCatching { repository.recognizeText(sessionId, page.id) }
                .fold({ OcrSheetState.Ready(it) }, { OcrSheetState.Failed(it.message ?: "Text recognition failed") })
            local.update { it.copy(ocr = state) }
        }
    }

    private fun launchEdit(block: suspend (SessionPage) -> Unit) {
        val page = currentPage ?: return
        viewModelScope.launch {
            runCatching { block(page) }.onFailure { _effects.trySend(ReviewEffect.Message(it.message ?: "Edit failed")) }
        }
    }

    private companion object {
        const val KEY_PAGE = "page"
        const val MAX_HISTORY = 40
        const val COMMIT_DELAY = 280L
    }
}
