package com.rajdeep.unidoc.feature.scanner.capture

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rajdeep.unidoc.core.model.ScanMode
import com.rajdeep.unidoc.core.ui.components.GradientButton
import com.rajdeep.unidoc.core.ui.components.IconBadge
import com.rajdeep.unidoc.core.ui.components.UnidocBottomSheet
import com.rajdeep.unidoc.core.ui.components.UnidocChip
import com.rajdeep.unidoc.core.ui.motion.rememberHaptics
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.feature.scanner.capture.camera.CameraController
import com.rajdeep.unidoc.feature.scanner.capture.camera.FrameAnalyzer

@Composable
fun CaptureRouteScreen(
    onClose: () -> Unit,
    onReview: (String) -> Unit,
    onPickFromGallery: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasCameraPermission()) }
    var askedOnce by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        // "Don't ask again" (or a second denial) means only Settings can grant it now.
        askedOnce = !it && context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false
    }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = context.hasCameraPermission() }

    LightSystemBarIcons()
    UnidocTheme(darkTheme = true) {
        if (granted) {
            LiveCamera(viewModel, onClose, onReview, onPickFromGallery)
        } else {
            PermissionScreen(
                permanentlyDenied = askedOnce,
                onRequest = { permission.launch(Manifest.permission.CAMERA) },
                onOpenSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                },
                onClose = onClose
            )
        }
    }
}

@Composable
private fun LiveCamera(viewModel: CaptureViewModel, onClose: () -> Unit, onReview: (String) -> Unit, onPickFromGallery: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val haptics = rememberHaptics()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val controller = remember { CameraController(context.applicationContext) }
    val surfaceRequest by controller.surfaceRequest.collectAsStateWithLifecycle()
    val currentMode by androidx.compose.runtime.rememberUpdatedState(state.mode)

    LaunchedEffect(controller) {
        val analyzer = FrameAnalyzer(
            qrMode = { currentMode == ScanMode.QR },
            onFrame = viewModel::onFrame,
            onBarcode = { viewModel.onBarcode(it.rawValue.orEmpty()) }
        )
        runCatching { controller.bind(owner, analyzer) }
            .onSuccess { viewModel.onCameraReady(controller.hasFlash) }
            .onFailure { viewModel.onPictureFailed("Camera unavailable: ${it.message}") }
    }
    DisposableEffect(controller) { onDispose { controller.release() } }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CaptureEffect.TakePicture -> runCatching { controller.takePicture(effect.file) }
                    .onSuccess {
                        haptics.confirm()
                        viewModel.onPictureTaken(it)
                    }
                    .onFailure { viewModel.onPictureFailed(it.message) }
                is CaptureEffect.ApplyFlash -> controller.setFlash(effect.mode)
                is CaptureEffect.OpenReview -> onReview(effect.sessionId)
                CaptureEffect.Close -> onClose()
            }
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
    }

    CaptureScreen(
        state = state,
        onAction = { action ->
            if (action == CaptureAction.OpenGallery) onPickFromGallery() else viewModel.onAction(action)
        },
        preview = {
            surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.matchParentSize()) }
                ?: ViewfinderPlaceholder()
        }
    )

    state.qrValue?.let { value -> QrResultSheet(value) { viewModel.onAction(CaptureAction.DismissQr) } }
}

@Composable
private fun QrResultSheet(value: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isLink = value.startsWith("http://", true) || value.startsWith("https://", true)
    UnidocBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = Dimens.Gutter).padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                IconBadge(Icons.Rounded.QrCode2)
                Text(if (isLink) "Link found" else "Code found", style = MaterialTheme.typography.headlineSmall)
            }
            SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge) }
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
                UnidocChip("Copy", false, {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("QR", value))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }, leadingIcon = Icons.Rounded.ContentCopy)
                UnidocChip("Share", false, {
                    context.startActivity(
                        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, value), null)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }, leadingIcon = Icons.Rounded.Share)
            }
            if (isLink) {
                GradientButton("Open link", {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }, icon = Icons.AutoMirrored.Rounded.OpenInNew, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PermissionScreen(permanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xFF11140D)), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(Dimens.SpaceXxl), horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(Icons.Rounded.CameraAlt, size = 72.dp, solid = true)
            Spacer(Modifier.height(Dimens.SpaceXl))
            Text("Camera access", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(Dimens.SpaceS))
            Text(
                "Unidoc uses the camera only while you scan. Photos stay on your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(Dimens.SpaceXl))
            if (permanentlyDenied) {
                GradientButton("Open settings", onOpenSettings, modifier = Modifier.fillMaxWidth())
            } else {
                GradientButton("Allow camera", onRequest, modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onClose, modifier = Modifier.padding(top = Dimens.SpaceS)) { Text("Not now") }
        }
    }
}

/** Camera is always dark: force light status/navigation icons while it is shown. */
@Composable
private fun LightSystemBarIcons() {
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window ?: return@DisposableEffect onDispose { }
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        val wasLightStatus = controller.isAppearanceLightStatusBars
        val wasLightNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = wasLightStatus
            controller.isAppearanceLightNavigationBars = wasLightNav
        }
    }
}

private fun Context.findActivity(): android.app.Activity? {
    var c: Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}

private fun Context.hasCameraPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
