package com.rajdeep.unidoc.feature.scanner.editor.review

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rajdeep.unidoc.core.ui.components.ErrorState
import com.rajdeep.unidoc.core.ui.components.SignaturePadSheet
import com.rajdeep.unidoc.core.ui.components.UnidocBottomSheet
import com.rajdeep.unidoc.core.ui.components.UnidocChip
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.feature.scanner.editor.EditorScreen
import com.rajdeep.unidoc.feature.scanner.editor.EditorTool

@Composable
fun ReviewRouteScreen(
    onClose: () -> Unit,
    onOpenCrop: (String, String) -> Unit,
    onOpenPages: (String) -> Unit,
    onExport: (String) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { e ->
            when (e) {
                is ReviewEffect.OpenCrop -> onOpenCrop(e.sessionId, e.pageKey)
                is ReviewEffect.OpenPages -> onOpenPages(e.sessionId)
                is ReviewEffect.Export -> onExport(e.sessionId)
                is ReviewEffect.Message -> Toast.makeText(context, e.text, Toast.LENGTH_SHORT).show()
                ReviewEffect.Close -> onClose()
            }
        }
    }

    val editor = state.editor
    when {
        state.missing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ErrorState("Scan not found", "This document was removed.", retryLabel = "Close", onRetry = onClose)
        }
        editor == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> EditorScreen(
            state = editor,
            onAction = viewModel::onAction,
            pageContent = { page ->
                val markup = editor.activeTool == EditorTool.ANNOTATE && page.key == editor.page?.key
                ScanPageImage(
                    page = page,
                    modifier = Modifier.fillMaxWidth(),
                    markupTool = if (markup) editor.annotateTool else null,
                    markupColor = viewModel.annotationColor,
                    markupStroke = editor.strokeWidth,
                    onAnnotations = { viewModel.setAnnotations(page.key, it) },
                    onTextRequest = viewModel::requestText,
                    onSignatureRequest = viewModel::requestSignature
                )
            }
        )
    }

    state.ocr?.let { OcrSheet(it, viewModel::dismissOcr) }
    when (val d = state.dialog) {
        is ReviewDialog.AddText -> TextEntryDialog(onConfirm = viewModel::addText, onDismiss = viewModel::dismissDialog)
        is ReviewDialog.Signature -> SignaturePadSheet(onUse = viewModel::useSignature, onDismiss = viewModel::dismissDialog)
        ReviewDialog.Watermark -> WatermarkDialog(onApply = viewModel::applyWatermark, onDismiss = viewModel::dismissDialog)
        null -> Unit
    }
}

@Composable
private fun TextEntryDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Add text") },
        text = {
            OutlinedTextField(text, {
                text = it
            }, placeholder = { Text("Type here") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Place") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun WatermarkDialog(onApply: (String, Float) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("CONFIDENTIAL") }
    var opacity by remember { mutableFloatStateOf(0.18f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Watermark all pages") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)) {
                OutlinedTextField(text, {
                    text = it
                }, label = { Text("Text") }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                Text("Opacity ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                Slider(opacity, { opacity = it }, valueRange = 0.05f..0.6f)
            }
        },
        confirmButton = { TextButton(onClick = { onApply(text, opacity) }) { Text("Apply") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onApply("", 0f) }) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
internal fun OcrSheet(state: OcrSheetState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var pendingText by remember { mutableStateOf("") }
    val saveTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(pendingText.toByteArray()) } }
                .onSuccess { Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() }
        }
    }
    UnidocBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = Dimens.Gutter).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)
        ) {
            Text("Recognized text", style = MaterialTheme.typography.headlineSmall)
            when (state) {
                OcrSheetState.Running -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
                ) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text("Reading the page…", style = MaterialTheme.typography.bodyLarge)
                }
                is OcrSheetState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
                is OcrSheetState.Ready -> {
                    val text = state.result.text
                    if (text.isBlank()) {
                        Text("No text found.", style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Box(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                            SelectionContainer { Text(text, style = MaterialTheme.typography.bodyLarge) }
                        }
                        // Wraps instead of cutting the last action to "S…" on narrow phones / large fonts.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
                        ) {
                            UnidocChip("Copy", false, {
                                context.getSystemService(
                                    ClipboardManager::class.java
                                ).setPrimaryClip(ClipData.newPlainText("Scanned text", text))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }, leadingIcon = Icons.Rounded.ContentCopy)
                            UnidocChip("Share", false, {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                                        null
                                    )
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }, leadingIcon = Icons.Rounded.Share)
                            UnidocChip("Save .txt", false, {
                                pendingText = text
                                saveTxt.launch("scan-text.txt")
                            }, leadingIcon = Icons.Rounded.Description)
                        }
                    }
                }
            }
        }
    }
}
