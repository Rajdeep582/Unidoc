package com.rajdeep.unidoc.feature.scanner.editor.review

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rajdeep.unidoc.feature.scanner.editor.CropScreen

@Composable
fun CropRouteScreen(onDone: () -> Unit, viewModel: CropViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    CropScreen(
        corners = state.corners,
        onCornersChange = viewModel::onCornersChange,
        onAutoDetect = viewModel::autoDetect,
        onNoCrop = viewModel::noCrop,
        onDone = viewModel::apply,
        onBack = onDone,
        imageAspectRatio = state.aspect,
        image = state.image,
        applying = state.saving,
        placeholder = {}
    )
}
