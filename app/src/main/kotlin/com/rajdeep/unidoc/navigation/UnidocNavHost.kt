package com.rajdeep.unidoc.navigation

import android.widget.Toast
import androidx.activity.BackEventCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.toRoute
import com.rajdeep.unidoc.BuildConfig
import com.rajdeep.unidoc.core.model.ToolId
import com.rajdeep.unidoc.core.ui.components.ProgressRing
import com.rajdeep.unidoc.core.ui.motion.UnidocMotion
import com.rajdeep.unidoc.feature.converter.hub.navigation.converterHubScreen
import com.rajdeep.unidoc.feature.converter.tools.navigation.navigateToTool
import com.rajdeep.unidoc.feature.converter.tools.navigation.toolScreen
import com.rajdeep.unidoc.feature.scanner.capture.ImportViewModel
import com.rajdeep.unidoc.feature.scanner.capture.navigation.CaptureRoute
import com.rajdeep.unidoc.feature.scanner.capture.navigation.captureScreen
import com.rajdeep.unidoc.feature.scanner.capture.navigation.navigateToCapture
import com.rajdeep.unidoc.feature.scanner.editor.navigation.ScanEditCallbacks
import com.rajdeep.unidoc.feature.scanner.editor.navigation.navigateToCrop
import com.rajdeep.unidoc.feature.scanner.editor.navigation.navigateToPages
import com.rajdeep.unidoc.feature.scanner.editor.navigation.navigateToReview
import com.rajdeep.unidoc.feature.scanner.editor.navigation.scanEditScreens
import com.rajdeep.unidoc.feature.scanner.home.ScannerHomeCallbacks
import com.rajdeep.unidoc.feature.scanner.home.navigation.ScannerHomeRoute
import com.rajdeep.unidoc.feature.scanner.home.navigation.scannerHomeScreen
import com.rajdeep.unidoc.feature.settings.navigation.navigateToSettings
import com.rajdeep.unidoc.feature.settings.navigation.settingsScreen
import com.rajdeep.unidoc.gallery.galleryScreens
import com.rajdeep.unidoc.gallery.navigateToGallery
import com.rajdeep.unidoc.ui.UnidocAppState

/** Shared-axis page motion (Material): one duration, no delays — the predictive-back gesture scrubs it 1:1. */
private const val PAGE_MS = 300
private const val FADE_IN_MS = 220
private const val FADE_OUT_MS = 150
private const val TAB_MS = 220

private fun NavBackStackEntry.isTab() = TopLevelDestination.entries.any { destination.hasRoute(it.route) }

private fun NavBackStackEntry.isCamera() = destination.hasRoute(CaptureRoute::class)

/*
 * Stock-Android page motion. Every destination sits on an opaque ScreenSurface, and the page that
 * leaves is always the one on top:
 *  - forward: the new page slides in a short way and fades in over the old one (which only shifts).
 *  - back (button or gesture): the current page slides toward the swipe edge and fades out quickly,
 *    uncovering the previous page, which is already fully drawn underneath (no fade-in, so the two
 *    never blend). While the back gesture is held, these are scrubbed with the finger: the page is
 *    already mostly transparent at a third of the gesture instead of sitting there full-size.
 * No start delays anywhere — a delay reads as the old page "hanging" after release.
 */
private fun <T> page() = tween<T>(PAGE_MS, easing = UnidocMotion.EmphasizedDecelerate)
private fun <T> scrub() = tween<T>(PAGE_MS, easing = LinearEasing)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition = when {
    initialState.isTab() && targetState.isTab() ->
        fadeIn(tween(TAB_MS, easing = UnidocMotion.EmphasizedDecelerate)) +
            scaleIn(tween(TAB_MS + 60, easing = UnidocMotion.EmphasizedDecelerate), initialScale = 0.98f)
    else -> slideInHorizontally(page()) { w -> w / 10 } + fadeIn(tween(FADE_IN_MS, easing = UnidocMotion.EmphasizedDecelerate))
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition = when {
    initialState.isTab() && targetState.isTab() -> fadeOut(tween(TAB_MS / 2, easing = LinearOutSlowInEasing))
    // Camera preview is a SurfaceView that ignores alpha: remove it at once instead of a slow fade.
    initialState.isCamera() -> ExitTransition.None
    // Covered by the incoming page; only drifts a little so the motion reads as one direction.
    else -> slideOutHorizontally(page()) { w -> -w / 12 } + fadeOut(tween(PAGE_MS, easing = LinearEasing), targetAlpha = 0.6f)
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition = slideInHorizontally(page()) { w -> -w / 12 }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition = when {
    initialState.isCamera() -> ExitTransition.None
    else -> slideOutHorizontally(page()) { w -> w / 6 } + fadeOut(tween(FADE_OUT_MS, easing = LinearOutSlowInEasing))
}

/** Gesture-driven back (finger on screen): linear so the page tracks the finger, gone by ~25% of the gesture. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.predictivePopEnter(): EnterTransition =
    slideInHorizontally(scrub()) { w -> -w / 12 }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.predictivePopExit(edge: Int): ExitTransition = when {
    initialState.isCamera() -> ExitTransition.None
    else -> slideOutHorizontally(scrub()) { w -> if (edge == BackEventCompat.EDGE_RIGHT) -w / 4 else w / 4 } +
        fadeOut(tween(PAGE_MS / 4, easing = LinearEasing))
}

@Composable
fun UnidocNavHost(appState: UnidocAppState, contentPadding: PaddingValues, versionName: String, modifier: Modifier = Modifier) {
    val nav = appState.navController
    val context = LocalContext.current
    val importer: ImportViewModel = hiltViewModel()
    val importing by importer.busy.collectAsStateWithLifecycle()

    LaunchedEffect(importer) {
        importer.results.collect { result ->
            result
                .onSuccess { id -> nav.navigateToReview(id, popCurrent = nav.currentBackStackEntry?.isCamera() == true) }
                .onFailure { Toast.makeText(context, it.message ?: "Import failed", Toast.LENGTH_LONG).show() }
        }
    }
    // When set, picked photos are appended to this document instead of creating a new one.
    val addTarget = androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        val target = addTarget.value
        addTarget.value = null
        if (target != null) importer.addTo(target, uris) else importer.import(uris)
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { importer.import(it) }
    val photos = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    Box(modifier) {
        NavHost(
            navController = nav,
            startDestination = ScannerHomeRoute,
            enterTransition = { enter() },
            exitTransition = { exit() },
            popEnterTransition = { popEnter() },
            popExitTransition = { popExit() },
            predictivePopEnterTransition = { predictivePopEnter() },
            predictivePopExitTransition = { edge -> predictivePopExit(edge) }
        ) {
            scannerHomeScreen(
                contentPadding = contentPadding,
                callbacks = ScannerHomeCallbacks(
                    onScan = { nav.navigateToCapture() },
                    onImportPhotos = photos,
                    onImportFiles = { pickFiles.launch(arrayOf("application/pdf", "image/*")) },
                    onOpenSettings = nav::navigateToSettings,
                    onOpenDocument = { id -> nav.navigateToReview(id) }
                )
            )
            converterHubScreen(
                contentPadding = contentPadding,
                onToolSelected = { tool -> if (tool == ToolId.SCAN_TO_PDF) nav.navigateToCapture() else nav.navigateToTool(tool) },
                onOpenSettings = nav::navigateToSettings
            )
            captureScreen(
                onClose = nav::popBackStack,
                onReview = { id ->
                    val addingToExisting = nav.currentBackStackEntry?.takeIf { it.isCamera() }?.toRoute<CaptureRoute>()?.sessionId != null
                    if (addingToExisting) nav.popBackStack() else nav.navigateToReview(id, popCurrent = true)
                },
                onPickFromGallery = photos
            )
            scanEditScreens(
                ScanEditCallbacks(
                    onBack = { nav.popBackStack() },
                    onOpenCrop = nav::navigateToCrop,
                    onOpenPages = nav::navigateToPages,
                    onOpenEditor = { id -> nav.navigateToReview(id) },
                    onExport = { id -> appState.requestExport(id, quickShare = false) },
                    onShare = { id -> appState.requestExport(id, quickShare = true) },
                    onAddFromCamera = { id -> nav.navigateToCapture(sessionId = id) },
                    onAddFromPhotos = { id ->
                        addTarget.value = id
                        photos()
                    },
                    onRetake = { id, page -> nav.navigateToCapture(sessionId = id, replacePageId = page) }
                )
            )
            toolScreen(onBack = nav::popBackStack)
            settingsScreen(
                versionName = versionName,
                onBack = nav::popBackStack,
                onOpenGallery = if (BuildConfig.DEBUG) nav::navigateToGallery else null
            )
            galleryScreens(nav)
        }

        AnimatedVisibility(importing != null, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(remember { MutableInteractionSource() }, indication = null) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ProgressRing(progress = null, size = 96.dp, strokeWidth = 8.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(importing.orEmpty(), style = MaterialTheme.typography.titleMedium, color = Color.White)
                }
            }
        }
    }
}
