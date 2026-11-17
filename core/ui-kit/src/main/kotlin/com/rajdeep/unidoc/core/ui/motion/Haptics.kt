package com.rajdeep.unidoc.core.ui.motion

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

/** Global haptics switch, provided from user settings at the app root. */
val LocalHapticsEnabled = staticCompositionLocalOf { true }

/** Semantic haptic feedback that degrades gracefully on older API levels. */
@Stable
class UnidocHaptics internal constructor(private val view: View, private val enabled: Boolean) {
    /** Light tick: selection change, tab switch, slider detent. */
    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    /** Success/confirm: capture done, export finished. */
    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.R
        ) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
    )

    /** Rejection: invalid action, error. */
    fun reject() = perform(
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.R
        ) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
    )

    /** Long-press / drag start. */
    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)

    /** Edge or snap reached during a drag (e.g. crop handle snapping to detected corner). */
    fun snap() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CLOCK_TICK
        }
    )

    private fun perform(constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(): UnidocHaptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { UnidocHaptics(view, enabled) }
}
