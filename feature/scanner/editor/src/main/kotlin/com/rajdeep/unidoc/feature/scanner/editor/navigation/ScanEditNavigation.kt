package com.rajdeep.unidoc.feature.scanner.editor.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.rajdeep.unidoc.core.ui.brand.ScreenSurface
import com.rajdeep.unidoc.feature.scanner.editor.review.CropRouteScreen
import com.rajdeep.unidoc.feature.scanner.editor.review.DocumentRouteScreen
import com.rajdeep.unidoc.feature.scanner.editor.review.PagesRouteScreen
import com.rajdeep.unidoc.feature.scanner.editor.review.ReviewRouteScreen
import kotlinx.serialization.Serializable

@Serializable
data class ReviewRoute(val sessionId: String)

@Serializable
data class CropRoute(val sessionId: String, val pageKey: String)

@Serializable
data class PagesRoute(val sessionId: String)

@Serializable
data class DocumentRoute(val sessionId: String)

/** Opens the editor; [popCurrent] removes the current screen (e.g. camera) so Back returns to where scanning started. */
fun NavController.navigateToReview(sessionId: String, popCurrent: Boolean = false) = navigate(ReviewRoute(sessionId)) {
    if (popCurrent) currentDestination?.id?.let { popUpTo(it) { inclusive = true } }
    launchSingleTop = true
}

fun NavController.navigateToCrop(sessionId: String, pageKey: String) = navigate(CropRoute(sessionId, pageKey))

fun NavController.navigateToPages(sessionId: String) = navigate(PagesRoute(sessionId)) { launchSingleTop = true }

fun NavController.navigateToDocument(sessionId: String) = navigate(DocumentRoute(sessionId)) { launchSingleTop = true }

class ScanEditCallbacks(
    val onBack: () -> Unit,
    val onOpenCrop: (String, String) -> Unit,
    val onOpenPages: (String) -> Unit,
    val onOpenEditor: (String) -> Unit,
    val onExport: (String) -> Unit,
    val onShare: (String) -> Unit,
    val onAddFromCamera: (String) -> Unit,
    val onAddFromPhotos: (String) -> Unit,
    val onRetake: (String, String) -> Unit
)

fun NavGraphBuilder.scanEditScreens(c: ScanEditCallbacks) {
    composable<ReviewRoute> {
        ScreenSurface {
            ReviewRouteScreen(onClose = c.onBack, onOpenCrop = c.onOpenCrop, onOpenPages = c.onOpenPages, onExport = c.onExport)
        }
    }
    composable<CropRoute> { ScreenSurface { CropRouteScreen(onDone = c.onBack) } }
    composable<PagesRoute> {
        ScreenSurface {
            PagesRouteScreen(
                onBack = c.onBack,
                onAddFromCamera = c.onAddFromCamera,
                onAddFromPhotos = c.onAddFromPhotos,
                onRetake = c.onRetake
            )
        }
    }
    composable<DocumentRoute> {
        ScreenSurface {
            DocumentRouteScreen(onBack = c.onBack, onEdit = c.onOpenEditor, onShare = c.onShare, onExport = c.onExport)
        }
    }
}
