package com.rajdeep.unidoc.feature.scanner.editor.review

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rajdeep.unidoc.core.imaging.Pt
import com.rajdeep.unidoc.core.imaging.Quad
import com.rajdeep.unidoc.core.scan.MatIO
import com.rajdeep.unidoc.core.scan.ScanRepository
import com.rajdeep.unidoc.feature.scanner.editor.navigation.CropRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CropUiState(
    /** The upright original, decoded exactly like the processing pipeline sees it. */
    val image: ImageBitmap? = null,
    val aspect: Float = 1f / 1.414f,
    val corners: List<Offset> = FULL,
    val detecting: Boolean = false,
    val saving: Boolean = false,
    val done: Boolean = false,
    /** Set once the user moves a handle: late auto-detection must never overwrite their outline. */
    val edited: Boolean = false
) {
    companion object {
        val FULL = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f))
    }
}

@HiltViewModel
class CropViewModel @Inject constructor(savedState: SavedStateHandle, private val repository: ScanRepository) : ViewModel() {
    private val route = savedState.toRoute<CropRoute>()
    private val _uiState = MutableStateFlow(CropUiState())
    val uiState: StateFlow<CropUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val page = repository.observe(route.sessionId).value?.pages?.firstOrNull { it.id == route.pageKey } ?: return@launch
            val file = repository.file(route.sessionId, page.original)
            val (w, h) = withContext(Dispatchers.IO) { MatIO.orientedSize(file) }
            _uiState.update { it.copy(aspect = w.toFloat() / h) }
            // Corners first (fast), then the photo for the editor and its magnifier.
            launch {
                val quad =
                    page.quad ?: if (page.corners?.isEmpty() == true) null else repository.detectCorners(route.sessionId, route.pageKey)
                _uiState.update { if (it.edited) it else it.copy(corners = quad?.toOffsets() ?: CropUiState.FULL) }
            }
            val bitmap = withContext(Dispatchers.IO) { runCatching { MatIO.readBitmap(file, 2400).asImageBitmap() }.getOrNull() }
            _uiState.update { it.copy(image = bitmap) }
        }
    }

    fun onCornersChange(corners: List<Offset>) = _uiState.update { it.copy(corners = corners, edited = true) }

    fun autoDetect() {
        if (_uiState.value.detecting) return
        _uiState.update { it.copy(detecting = true) }
        viewModelScope.launch {
            val q = repository.detectCorners(route.sessionId, route.pageKey)
            _uiState.update { it.copy(corners = q?.toOffsets() ?: it.corners, detecting = false, edited = true) }
        }
    }

    fun noCrop() = _uiState.update { it.copy(corners = CropUiState.FULL, edited = true) }

    fun apply() {
        if (_uiState.value.saving) return
        val c = _uiState.value.corners
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val pts = c.map { Pt(it.x.toDouble(), it.y.toDouble()) }
            // The editor only allows convex clockwise outlines, so keep the user's corner labels:
            // re-ordering could pick a different "top" side and rotate the page by 90°.
            val asGiven = Quad(pts[0], pts[1], pts[2], pts[3])
            val quad = if (asGiven.isConvex()) asGiven else Quad.ordered(pts)
            // Wait for the page to be re-rendered with exactly these corners before leaving, so the
            // editor and export can only ever show this crop (never an older image).
            runCatching { repository.setCorners(route.sessionId, route.pageKey, quad) }
            _uiState.update { it.copy(saving = false, done = true) }
        }
    }

    private fun Quad.toOffsets() = points.map { Offset(it.x.toFloat(), it.y.toFloat()) }
}
