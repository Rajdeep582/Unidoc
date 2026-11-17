package com.rajdeep.unidoc.feature.scanner.export

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rajdeep.unidoc.core.pdf.ExportFiles
import com.rajdeep.unidoc.core.ui.components.GradientButton
import com.rajdeep.unidoc.core.ui.components.IconBadge
import com.rajdeep.unidoc.core.ui.components.ProgressRing
import com.rajdeep.unidoc.core.ui.components.UnidocBottomSheet
import com.rajdeep.unidoc.core.ui.format.formatFileSize
import com.rajdeep.unidoc.core.ui.motion.rememberHaptics
import com.rajdeep.unidoc.core.ui.theme.Dimens
import java.io.File
import kotlin.math.roundToInt

/** Export bottom sheet bound to a document. [quickShare] skips options and shares a Balanced PDF. */
@Composable
fun ExportSheetRoute(sessionId: String, quickShare: Boolean = false, onDismiss: () -> Unit) {
    val vm = hiltViewModel<ExportViewModel, ExportViewModel.Factory>(key = "export-$sessionId-$quickShare") { it.create(sessionId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = rememberHaptics()

    LaunchedEffect(quickShare) { if (quickShare) vm.quickShare() }
    LaunchedEffect(phase) {
        if (phase is ExportPhase.Done) {
            haptics.confirm()
            if (quickShare) {
                context.shareFiles((phase as ExportPhase.Done).files)
                onDismiss()
            }
        }
    }

    val saveOne = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val files = (phase as? ExportPhase.Done)?.files ?: return@rememberLauncherForActivityResult
        if (uri != null) context.copyTo(files.first(), uri)
    }
    val saveMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val files = (phase as? ExportPhase.Done)?.files ?: return@rememberLauncherForActivityResult
        if (tree != null) context.copyAllToTree(files, tree)
    }

    UnidocBottomSheet(onDismissRequest = {
        vm.cancel()
        onDismiss()
    }) {
        AnimatedContent(phase::class, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "exportPhase") { cls ->
            when (cls) {
                ExportPhase.Editing::class -> ExportOptionsContent(state, vm::onFileName, vm::onOptions, vm::export)
                ExportPhase.Running::class -> (phase as? ExportPhase.Running)?.let { RunningContent(it, vm::cancel) }
                ExportPhase.Done::class -> (phase as? ExportPhase.Done)?.let { done ->
                    DoneContent(
                        done = done,
                        onShare = { context.shareFiles(done.files) },
                        onSave = {
                            if (done.files.size == 1) saveOne.launch(done.files.first().name) else saveMany.launch(null)
                        },
                        onOpen = { context.openFile(done.files.first()) },
                        onAgain = vm::backToOptions
                    )
                }
                else -> (phase as? ExportPhase.Failed)?.let { f ->
                    Column(
                        Modifier.fillMaxWidth().padding(Dimens.Gutter).padding(bottom = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Export failed", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(Dimens.SpaceS))
                        Text(f.message, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        TextButton(onClick = vm::backToOptions) { Text("Back to options") }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningContent(p: ExportPhase.Running, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Dimens.Gutter).padding(bottom = 32.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProgressRing(progress = p.fraction, size = 140.dp) {
            Text("${(p.fraction * 100).roundToInt()}%", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(Dimens.SpaceL))
        Text(p.stage, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onCancel, modifier = Modifier.padding(top = Dimens.SpaceS)) { Text("Cancel") }
    }
}

@Composable
private fun DoneContent(done: ExportPhase.Done, onShare: () -> Unit, onSave: () -> Unit, onOpen: () -> Unit, onAgain: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = Dimens.Gutter).padding(bottom = 32.dp).navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM)
    ) {
        IconBadge(Icons.Rounded.Check, size = 64.dp, solid = true)
        Text("Ready", style = MaterialTheme.typography.headlineSmall)
        Text(
            (if (done.files.size == 1) done.files.first().name else "${done.files.size} files") + " · " + formatFileSize(done.totalBytes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        GradientButton("Share", onShare, icon = Icons.Rounded.IosShare, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Save to…")
            }
            if (done.files.size == 1) {
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Open")
                }
            }
        }
        TextButton(onClick = onAgain) { Text("Change options") }
    }
}

fun Context.shareFiles(files: List<File>) = ExportFiles.share(this, files)

private fun Context.openFile(file: File) = ExportFiles.open(this, file)

private fun File.mime(): String = ExportFiles.mime(this)

private fun Context.copyTo(file: File, uri: Uri) {
    runCatching { contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } }
        .onSuccess { Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show() }
        .onFailure { Toast.makeText(this, "Couldn't save: ${it.message}", Toast.LENGTH_LONG).show() }
}

private fun Context.copyAllToTree(files: List<File>, tree: Uri) {
    runCatching {
        val dir = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, tree) ?: error("Folder unavailable")
        files.forEach { f ->
            val target = dir.createFile(f.mime(), f.name) ?: error("Can't create ${f.name}")
            contentResolver.openOutputStream(target.uri)?.use { out -> f.inputStream().use { it.copyTo(out) } }
        }
    }.onSuccess { Toast.makeText(this, "Saved ${files.size} files", Toast.LENGTH_SHORT).show() }
        .onFailure { Toast.makeText(this, "Couldn't save: ${it.message}", Toast.LENGTH_LONG).show() }
}
