package com.rajdeep.unidoc.feature.scanner.capture

import androidx.compose.ui.geometry.Offset
import com.rajdeep.unidoc.core.model.FlashMode
import com.rajdeep.unidoc.core.model.ScanMode

/** Guidance shown above the viewfinder, driven by the detector in Phase 3. */
enum class CaptureHint {
    SEARCHING,
    MOVE_CLOSER,
    HOLD_STEADY,
    CAPTURING,
    TOO_DARK,
    FLIP_CARD
}

data class CaptureUiState(
    val mode: ScanMode = ScanMode.DOCUMENT,
    val flash: FlashMode = FlashMode.AUTO,
    val gridVisible: Boolean = false,
    val autoCapture: Boolean = true,
    // Single shot by default: each capture opens the page for review (adjust edges, filters).
    val batch: Boolean = false,
    val pageCount: Int = 0,
    val hint: CaptureHint = CaptureHint.SEARCHING,
    /** 0..1 progress of the auto-capture stability window. */
    val stability: Float = 0f,
    /** Detected document corners, normalized to the preview (TL, TR, BR, BL); null when none. */
    val quad: List<Offset>? = null,
    val capturing: Boolean = false,
    /** Decoded QR/barcode payload in QR mode. */
    val qrValue: String? = null,
    val hasFlashUnit: Boolean = true,
    val error: String? = null
)

sealed interface CaptureAction {
    data class ModeChanged(val mode: ScanMode) : CaptureAction

    data object CycleFlash : CaptureAction

    data object ToggleGrid : CaptureAction

    data object ToggleAutoCapture : CaptureAction

    data object ToggleBatch : CaptureAction

    data object Shutter : CaptureAction

    data object OpenGallery : CaptureAction

    data object Done : CaptureAction

    data object Close : CaptureAction

    data object DismissQr : CaptureAction
}
