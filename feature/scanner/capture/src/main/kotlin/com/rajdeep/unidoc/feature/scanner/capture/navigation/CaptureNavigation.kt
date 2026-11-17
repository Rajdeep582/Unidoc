package com.rajdeep.unidoc.feature.scanner.capture.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.rajdeep.unidoc.core.model.ScanMode
import com.rajdeep.unidoc.feature.scanner.capture.CaptureRouteScreen
import kotlinx.serialization.Serializable

/** [sessionId] set when adding pages to an existing session (retake / add page). */
@Serializable
data class CaptureRoute(val mode: String? = null, val sessionId: String? = null, val replacePageId: String? = null)

fun NavController.navigateToCapture(mode: ScanMode? = null, sessionId: String? = null, replacePageId: String? = null) =
    navigate(CaptureRoute(mode?.name, sessionId, replacePageId))

fun NavGraphBuilder.captureScreen(onClose: () -> Unit, onReview: (String) -> Unit, onPickFromGallery: () -> Unit) {
    composable<CaptureRoute> { CaptureRouteScreen(onClose = onClose, onReview = onReview, onPickFromGallery = onPickFromGallery) }
}
