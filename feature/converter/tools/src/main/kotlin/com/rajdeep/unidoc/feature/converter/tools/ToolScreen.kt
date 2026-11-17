package com.rajdeep.unidoc.feature.converter.tools

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rajdeep.unidoc.core.model.PickedFile
import com.rajdeep.unidoc.core.model.ToolId
import com.rajdeep.unidoc.core.model.ToolInput
import com.rajdeep.unidoc.core.model.ToolOutput
import com.rajdeep.unidoc.core.ui.catalog.presentation
import com.rajdeep.unidoc.core.ui.components.ErrorState
import com.rajdeep.unidoc.core.ui.components.GradientButton
import com.rajdeep.unidoc.core.ui.components.IconBadge
import com.rajdeep.unidoc.core.ui.components.ProgressRing
import com.rajdeep.unidoc.core.ui.components.UnidocCard
import com.rajdeep.unidoc.core.ui.components.UnidocTopBar
import com.rajdeep.unidoc.core.ui.format.formatFileSize
import com.rajdeep.unidoc.core.ui.motion.UnidocMotion
import com.rajdeep.unidoc.core.ui.motion.rememberHaptics
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.feature.converter.tools.components.FileDropZone
import com.rajdeep.unidoc.feature.converter.tools.components.FileRows
import com.rajdeep.unidoc.feature.converter.tools.components.OptionField
import com.rajdeep.unidoc.feature.converter.tools.components.OutputPreview
import com.rajdeep.unidoc.feature.converter.tools.components.PageEditorSection
import com.rajdeep.unidoc.feature.converter.tools.components.canPreviewOutput
import com.rajdeep.unidoc.feature.converter.tools.preview.ProvidePdfPreviews
import kotlin.math.roundToInt

@Composable
fun ToolRouteScreen(onBack: () -> Unit, viewModel: ToolViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val mimes = state.tool.input.mimeTypes.toTypedArray()
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.onAction(ToolAction.FilesPicked(uris))
    }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onAction(ToolAction.FilesPicked(listOf(uri)))
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        if (uri != null) viewModel.onAction(ToolAction.SaveTo(uri, isFolder = false))
    }
    val saveFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onAction(ToolAction.SaveTo(uri, isFolder = true))
    }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
    }
    ProvidePdfPreviews(viewModel.previews) {
        ToolScreen(
            state = state,
            onAction = viewModel::onAction,
            onBack = onBack,
            onPick = { if (state.tool.isBatch) pickMany.launch(mimes) else pickOne.launch(mimes) },
            onOpen = { out ->
                context.startSafely(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(out.uri), out.mimeType))
            },
            onShare = { outs ->
                val intent = if (outs.size == 1) {
                    Intent(Intent.ACTION_SEND).setType(outs[0].mimeType).putExtra(Intent.EXTRA_STREAM, Uri.parse(outs[0].uri))
                } else {
                    val types = outs.map { it.mimeType }.distinct()
                    Intent(Intent.ACTION_SEND_MULTIPLE)
                        .setType(if (types.size == 1) types[0] else "*/*")
                        .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(outs.map { Uri.parse(it.uri) }))
                }
                context.startSafely(Intent.createChooser(intent, outs.first().name))
            },
            onSave = { outs -> if (outs.size == 1) saveFile.launch(outs[0].name) else saveFolder.launch(null) }
        )
    }
}

private fun android.content.Context.startSafely(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        android.widget.Toast.makeText(this, "No app can open this file", android.widget.Toast.LENGTH_SHORT).show()
    }
}

private fun ToolInput.acceptedLabel(): String = when (this) {
    ToolInput.PDF -> "PDF files"
    ToolInput.IMAGE -> "JPG, PNG, WebP, HEIC"
    ToolInput.WORD -> "DOC, DOCX, ODT, RTF"
    ToolInput.POWERPOINT -> "PPT, PPTX, ODP"
    ToolInput.EXCEL -> "XLS, XLSX, ODS, CSV"
    ToolInput.HTML -> "HTML files"
    ToolInput.TEXT -> "TXT files"
    ToolInput.NONE -> ""
}

@Composable
fun ToolScreen(
    state: ToolUiState,
    onAction: (ToolAction) -> Unit,
    onBack: () -> Unit,
    onPick: () -> Unit,
    onOpen: (ToolOutput) -> Unit = {},
    onSave: (List<ToolOutput>) -> Unit = {},
    onShare: (List<ToolOutput>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val p = state.tool.presentation()
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        UnidocTopBar(title = p.title, onBack = onBack)
        AnimatedContent(
            targetState = state.step::class,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.96f)) togetherWith fadeOut() },
            label = "toolStep",
            modifier = Modifier.weight(1f)
        ) { stepClass ->
            when (stepClass) {
                ToolStep.Input::class -> InputStep(state, onAction, onPick)
                ToolStep.Processing::class -> (state.step as? ToolStep.Processing)?.let {
                    ProcessingStep(it, state.tool) { onAction(ToolAction.Cancel) }
                }
                ToolStep.Done::class -> (state.step as? ToolStep.Done)?.let {
                    DoneStep(it, onOpen, onShare, onSave) { onAction(ToolAction.Reset) }
                }
                else -> (state.step as? ToolStep.Error)?.let { err ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ErrorState(
                            title = "Couldn't finish",
                            body = err.message,
                            retryLabel = "Back to options",
                            onRetry = { onAction(ToolAction.BackToInput) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InputStep(state: ToolUiState, onAction: (ToolAction) -> Unit, onPick: () -> Unit) {
    val tool = state.tool
    val p = tool.presentation()
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL)
            ) {
                IconBadge(p.icon, accent = tool.category.presentation().accent, size = 56.dp, solid = true)
                Column(Modifier.weight(1f)) {
                    Text(p.description, style = MaterialTheme.typography.bodyLarge)
                }
            }
            if (state.needsFiles) {
                if (state.files.isEmpty()) {
                    FileDropZone(tool.input.acceptedLabel(), tool.isBatch, onPick)
                } else {
                    FileRows(
                        files = state.files,
                        reorderable = tool == ToolId.MERGE_PDF || tool == ToolId.IMAGE_TO_PDF,
                        canAddMore = state.canAddMore,
                        onRemove = { onAction(ToolAction.RemoveFile(it.uri)) },
                        onMove = { from, to -> onAction(ToolAction.MoveFile(from, to)) },
                        onAddMore = onPick
                    )
                }
            }
            PageEditorSection(state, onAction)
            state.visibleSpec.forEach { option ->
                OptionField(option, state.options[option.key] ?: option.default, {
                    onAction(ToolAction.OptionChanged(option.key, it))
                })
            }
            Spacer(Modifier.height(Dimens.SpaceL))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .navigationBarsPadding()
                .padding(horizontal = Dimens.Gutter, vertical = Dimens.SpaceM),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            state.blocker?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Dimens.SpaceS)
                )
            }
            GradientButton(
                text = p.title,
                icon = Icons.Rounded.PlayArrow,
                onClick = { onAction(ToolAction.Run) },
                enabled = state.blocker == null,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ProcessingStep(step: ToolStep.Processing, tool: ToolId, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(Dimens.SpaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ProgressRing(progress = step.fraction, size = 180.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (step.fraction != null) {
                    Text("${(step.fraction * 100).roundToInt()}%", style = MaterialTheme.typography.displaySmall)
                } else {
                    IconBadge(tool.presentation().icon, accent = tool.category.presentation().accent, size = 56.dp)
                }
            }
        }
        Spacer(Modifier.height(Dimens.SpaceXl))
        AnimatedContent(step.stage, label = "processingStage") {
            Text(it, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(Dimens.SpaceS))
        Text(
            "You can leave this screen — work continues in the background.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Dimens.SpaceXl))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

@Composable
private fun DoneStep(
    step: ToolStep.Done,
    onOpen: (ToolOutput) -> Unit,
    onShare: (List<ToolOutput>) -> Unit,
    onSave: (List<ToolOutput>) -> Unit,
    onAgain: () -> Unit
) {
    val colors = UnidocTheme.colors
    val haptics = rememberHaptics()
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        appeared = true
        haptics.confirm()
    }
    val pop by animateFloatAsState(if (appeared) 1f else 0.4f, UnidocMotion.bouncy(), label = "donePop")
    val outBytes = step.outputs.sumOf { it.sizeBytes }
    val saved = if (step.inputBytes > 0) ((1 - outBytes.toDouble() / step.inputBytes) * 100).roundToInt() else 0
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dimens.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Dimens.SpaceXl))
        Box(contentAlignment = Alignment.BottomEnd) {
            val first = step.outputs.firstOrNull()
            if (first != null && canPreviewOutput(first)) {
                OutputPreview(
                    first,
                    Modifier.graphicsLayer {
                        scaleX = pop
                        scaleY = pop
                    }
                )
            } else {
                Spacer(Modifier.size(96.dp))
            }
            Box(
                Modifier
                    .padding(4.dp)
                    .size(if (first != null && canPreviewOutput(first)) 44.dp else 96.dp)
                    .graphicsLayer {
                        scaleX = pop
                        scaleY = pop
                    }
                    .clip(CircleShape)
                    .background(colors.brandBrush),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Check,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(if (first != null && canPreviewOutput(first)) 24.dp else 48.dp)
                )
            }
        }
        Spacer(Modifier.height(Dimens.SpaceXl))
        Text("Done", style = MaterialTheme.typography.headlineMedium)
        if (saved > 0) {
            Text(
                "${formatFileSize(step.inputBytes)} → ${formatFileSize(outBytes)} · $saved% smaller",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.success
            )
        }
        Spacer(Modifier.height(Dimens.SpaceXl))
        step.outputs.forEach { out ->
            UnidocCard(Modifier.fillMaxWidth().padding(bottom = Dimens.SpaceS)) {
                Row(Modifier.padding(Dimens.SpaceL), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(out.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            formatFileSize(out.sizeBytes) + if (out.fileCount > 1) " · ${out.fileCount} files" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = { onOpen(out) }) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Open")
                    }
                }
            }
        }
        Spacer(Modifier.height(Dimens.SpaceL))
        if (step.outputs.isNotEmpty()) {
            GradientButton(
                if (step.outputs.size > 1) "Share all" else "Share",
                { onShare(step.outputs) },
                icon = Icons.Rounded.IosShare,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = { onSave(step.outputs) }, modifier = Modifier.fillMaxWidth().padding(top = Dimens.SpaceS)) {
                Icon(Icons.Rounded.SaveAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text(if (step.outputs.size > 1) "Save to folder…" else "Save to…")
            }
        }
        TextButton(onClick = onAgain, modifier = Modifier.padding(top = Dimens.SpaceS)) {
            Icon(Icons.Rounded.Replay, null, Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Process another")
        }
    }
}

object SampleTool {
    private val files = listOf(
        PickedFile("content://a", "Semester 5 marksheet.pdf", 1_820_000, "application/pdf"),
        PickedFile("content://b", "Internship certificate.pdf", 640_000, "application/pdf"),
        PickedFile("content://c", "Project report final.pdf", 4_300_000, "application/pdf")
    )
    val mergeInput = ToolUiState(ToolId.MERGE_PDF, files = files)
    val compressEmpty = ToolUiState(ToolId.COMPRESS_PDF)
    val processing =
        ToolUiState(
            ToolId.COMPRESS_PDF,
            files = files.take(1),
            step = ToolStep.Processing(0.62f, "Optimizing images · page 9 of 14")
        )
    val done = ToolUiState(
        ToolId.COMPRESS_PDF,
        files = files.take(1),
        step = ToolStep.Done(
            listOf(ToolOutput("content://out", "Semester 5 marksheet (compressed).pdf", 412_000, "application/pdf")),
            1_820_000
        )
    )
    val error =
        ToolUiState(
            ToolId.WORD_TO_PDF,
            step = ToolStep.Error(
                "The converter is starting up. Please try again in a minute.",
                true
            )
        )
}
