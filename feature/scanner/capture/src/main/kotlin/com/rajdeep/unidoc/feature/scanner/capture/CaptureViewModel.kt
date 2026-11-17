package com.rajdeep.unidoc.feature.scanner.capture

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rajdeep.unidoc.core.datastore.SettingsRepository
import com.rajdeep.unidoc.core.imaging.StabilityTracker
import com.rajdeep.unidoc.core.model.FlashMode
import com.rajdeep.unidoc.core.model.ScanMode
import com.rajdeep.unidoc.core.scan.ScanRepository
import com.rajdeep.unidoc.feature.scanner.capture.camera.FrameResult
import com.rajdeep.unidoc.feature.scanner.capture.navigation.CaptureRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CaptureEffect {
    /** Ask the camera layer to shoot into [file]. */
    data class TakePicture(val file: File) : CaptureEffect

    data class ApplyFlash(val mode: FlashMode) : CaptureEffect

    data class OpenReview(val sessionId: String) : CaptureEffect

    data object Close : CaptureEffect
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val repository: ScanRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val route = savedState.toRoute<CaptureRoute>()
    private val tracker = StabilityTracker()
    private var lastCaptureAt = 0L

    /** Session id survives process death; created lazily on the first capture. */
    private var sessionId: String? = savedState.get<String>(KEY_SESSION) ?: route.sessionId
        set(value) {
            field = value
            savedState[KEY_SESSION] = value
        }

    private val _uiState = MutableStateFlow(
        CaptureUiState(
            mode = route.mode?.let(ScanMode::valueOf) ?: ScanMode.DOCUMENT,
            pageCount = sessionId?.let { repository.observe(it).value?.pages?.size } ?: 0
        )
    )
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private val _effects = Channel<CaptureEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val autoCapture = settings.settings.first().autoCapture
            _uiState.update { it.copy(autoCapture = autoCapture) }
        }
    }

    fun onCameraReady(hasFlash: Boolean) {
        _uiState.update { it.copy(hasFlashUnit = hasFlash) }
        _effects.trySend(CaptureEffect.ApplyFlash(_uiState.value.flash))
    }

    fun onAction(action: CaptureAction) {
        when (action) {
            is CaptureAction.ModeChanged -> {
                if (_uiState.value.pageCount > 0 && action.mode != _uiState.value.mode) return // mode is fixed once a session has pages
                tracker.reset()
                _uiState.update { it.copy(mode = action.mode, quad = null, stability = 0f, qrValue = null, hint = CaptureHint.SEARCHING) }
            }
            CaptureAction.CycleFlash -> {
                val order = listOf(FlashMode.AUTO, FlashMode.ON, FlashMode.OFF, FlashMode.TORCH)
                val next = order[(order.indexOf(_uiState.value.flash) + 1) % order.size]
                _uiState.update { it.copy(flash = next) }
                _effects.trySend(CaptureEffect.ApplyFlash(next))
            }
            CaptureAction.ToggleGrid -> _uiState.update { it.copy(gridVisible = !it.gridVisible) }
            CaptureAction.ToggleAutoCapture -> {
                tracker.reset()
                _uiState.update { it.copy(autoCapture = !it.autoCapture, stability = 0f) }
            }
            CaptureAction.ToggleBatch -> _uiState.update { it.copy(batch = !it.batch) }
            CaptureAction.Shutter -> shoot()
            CaptureAction.Done -> sessionId?.let { _effects.trySend(CaptureEffect.OpenReview(it)) }
            CaptureAction.OpenGallery -> Unit // handled by the route (system picker)
            CaptureAction.Close -> {
                if (_uiState.value.pageCount == 0) sessionId?.let(repository::discard)
                _effects.trySend(CaptureEffect.Close)
            }
            CaptureAction.DismissQr -> _uiState.update { it.copy(qrValue = null) }
        }
    }

    private var shownQuad: List<Offset>? = null
    private var missedFrames = 0

    /**
     * Steadies the live outline: small frame-to-frame jitter is eased, big jumps snap, and a few
     * missed detections keep the last outline instead of blinking it off and on.
     */
    private fun smoothQuad(found: List<Offset>?): List<Offset>? {
        val prev = shownQuad
        if (found == null) {
            missedFrames++
            if (missedFrames > 4) shownQuad = null
            return shownQuad
        }
        missedFrames = 0
        shownQuad = if (prev == null || prev.zip(found).maxOf { (a, b) -> (a - b).getDistance() } > 0.08f) {
            found
        } else {
            prev.zip(found) { a, b -> a + (b - a) * 0.5f }
        }
        return shownQuad
    }

    fun onFrame(frame: FrameResult) {
        val s = _uiState.value
        if (s.mode == ScanMode.QR || s.capturing) return
        val stability = tracker.update(frame.quad, frame.sharpness)
        val inCooldown = System.currentTimeMillis() - lastCaptureAt < COOLDOWN_MS
        val awaitingBack = s.mode == ScanMode.ID_CARD && s.pageCount > 0 && needsBack
        val hint = when {
            awaitingBack && inCooldown -> CaptureHint.FLIP_CARD
            frame.brightness < 45 && s.flash != FlashMode.TORCH -> CaptureHint.TOO_DARK
            frame.quad == null -> CaptureHint.SEARCHING
            frame.areaFraction < MIN_AREA -> CaptureHint.MOVE_CLOSER
            tracker.isReady -> CaptureHint.CAPTURING
            else -> CaptureHint.HOLD_STEADY
        }
        _uiState.update {
            it.copy(
                quad = smoothQuad(frame.quad?.points?.map { p -> Offset(p.x.toFloat(), p.y.toFloat()) }),
                stability = if (it.autoCapture) stability else 0f,
                hint = hint
            )
        }
        if (s.autoCapture && tracker.isReady && !inCooldown && frame.areaFraction >= MIN_AREA) shoot()
    }

    fun onBarcode(value: String) {
        if (_uiState.value.qrValue == null) _uiState.update { it.copy(qrValue = value) }
    }

    /** ID card: true while the last page still needs its back side. */
    private var needsBack = false

    private fun shoot() {
        if (_uiState.value.capturing) return
        lastCaptureAt = System.currentTimeMillis()
        tracker.reset()
        _uiState.update { it.copy(capturing = true, stability = 0f) }
        val file = File(context.cacheDir, "capture_${System.nanoTime()}.jpg")
        _effects.trySend(CaptureEffect.TakePicture(file))
    }

    fun onPictureTaken(file: File) {
        val replace = route.replacePageId
        if (replace != null && sessionId != null) {
            viewModelScope.launch {
                _uiState.update { it.copy(capturing = false) }
                runCatching { repository.replaceOriginal(sessionId!!, replace, file) }
                    .onFailure { e -> _uiState.update { it.copy(error = e.message ?: "Couldn't save the photo") } }
                _effects.trySend(CaptureEffect.OpenReview(sessionId!!))
            }
            return
        }
        viewModelScope.launch {
            val mode = _uiState.value.mode
            val id = sessionId ?: repository.createSession(mode).id.also { sessionId = it }
            _uiState.update { it.copy(capturing = false) }
            runCatching { repository.addCapture(id, file) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message ?: "Couldn't save the photo") } }
            val pages = repository.observe(id).value?.pages.orEmpty()
            needsBack = mode == ScanMode.ID_CARD && pages.lastOrNull()?.backOriginal == null
            _uiState.update { it.copy(pageCount = pages.size) }
            val singleDone = !_uiState.value.batch && !(mode == ScanMode.ID_CARD && needsBack)
            if (singleDone) _effects.trySend(CaptureEffect.OpenReview(id))
        }
    }

    fun onPictureFailed(message: String?) {
        _uiState.update { it.copy(capturing = false, error = message ?: "Capture failed") }
    }

    fun consumeError() = _uiState.update { it.copy(error = null) }

    private companion object {
        const val KEY_SESSION = "session_id"
        const val COOLDOWN_MS = 2_000L

        /** Document must cover this much of the frame (receipts and ID cards are small). */
        const val MIN_AREA = 0.14
    }
}
