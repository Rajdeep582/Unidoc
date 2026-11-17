package com.rajdeep.unidoc.feature.scanner.editor.review

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rajdeep.unidoc.core.data.LibraryRepository
import com.rajdeep.unidoc.core.model.Folder
import com.rajdeep.unidoc.core.model.ScanDocument
import com.rajdeep.unidoc.core.scan.ScanRepository
import com.rajdeep.unidoc.core.scan.ocr.OcrLine
import com.rajdeep.unidoc.core.scan.ocr.OcrResult
import com.rajdeep.unidoc.core.ui.components.ConfirmDialog
import com.rajdeep.unidoc.core.ui.components.NavigationRow
import com.rajdeep.unidoc.core.ui.components.UnidocBottomSheet
import com.rajdeep.unidoc.feature.scanner.editor.DocumentCallbacks
import com.rajdeep.unidoc.feature.scanner.editor.DocumentScreen
import com.rajdeep.unidoc.feature.scanner.editor.EditorPage
import com.rajdeep.unidoc.feature.scanner.editor.navigation.DocumentRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DocumentUiState(val document: ScanDocument, val pages: List<EditorPage>)

@OptIn(FlowPreview::class)
@HiltViewModel
class DocumentViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val scans: ScanRepository,
    private val library: LibraryRepository
) : ViewModel() {
    val sessionId: String = savedState.toRoute<DocumentRoute>().sessionId

    val folders: StateFlow<List<Folder>> = library.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val ocr = MutableStateFlow<OcrSheetState?>(null)

    val state: StateFlow<DocumentUiState?> = combine(scans.observe(sessionId), library.observeDocument(sessionId)) { session, doc ->
        session ?: return@combine null
        DocumentUiState(
            document = ScanDocument(
                id = session.id,
                title = session.title,
                pageCount = session.pages.size,
                sizeBytes = doc?.sizeBytes ?: 0,
                createdAtMillis = session.createdAtMillis,
                updatedAtMillis = doc?.updatedAtMillis ?: session.createdAtMillis,
                folderId = doc?.folderId,
                hasOcrText = doc?.hasOcrText == true
            ),
            pages = session.pages.map { p ->
                val path = p.processed?.let { scans.file(session.id, it).absolutePath }
                EditorPage(
                    id = p.id.hashCode().toLong(),
                    key = p.id,
                    imagePath = path,
                    processing = p.isRendering,
                    aspect = path?.let { f ->
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(f, o)
                        if (o.outWidth > 0) o.outWidth.toFloat() / o.outHeight else null
                    },
                    annotations = p.annotations
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { scans.observe(sessionId).filterNotNull().debounce(600).collect { runCatching { library.sync(it) } } }
    }

    fun rename(title: String) = viewModelScope.launch { library.rename(sessionId, title) }

    fun move(folderId: Long?) = viewModelScope.launch { library.move(listOf(sessionId), folderId) }

    fun delete() = viewModelScope.launch { library.delete(listOf(sessionId)) }

    /** Recognizes every page (cached per page) and shows the combined text. */
    fun allText() {
        val pages = scans.observe(sessionId).value?.pages ?: return
        ocr.value = OcrSheetState.Running
        viewModelScope.launch {
            ocr.value = runCatching {
                val results = pages.map { scans.recognizeText(sessionId, it.id) }
                val text = results.mapIndexed { i, r ->
                    if (pages.size >
                        1
                    ) {
                        "— Page ${i + 1} —\n${r.text}"
                    } else {
                        r.text
                    }
                }.joinToString("\n\n")
                OcrResult(text, results.flatMap { it.lines }.map { OcrLine(it.text, emptyList()) }, emptyList(), 0, 0)
            }.fold({ OcrSheetState.Ready(it) }, { OcrSheetState.Failed(it.message ?: "Text recognition failed") })
        }
    }
}

@Composable
fun DocumentRouteScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onShare: (String) -> Unit,
    onExport: (String) -> Unit,
    viewModel: DocumentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val ocr by viewModel.ocr.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var renaming by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val s = state ?: return

    DocumentScreen(
        document = s.document,
        callbacks = DocumentCallbacks(
            onBack = onBack,
            onShare = { onShare(viewModel.sessionId) },
            onExport = { onExport(viewModel.sessionId) },
            onEdit = { onEdit(viewModel.sessionId) },
            onText = viewModel::allText,
            onRename = { renaming = true },
            onMore = { more = true }
        ),
        pageContent = { index -> s.pages.getOrNull(index)?.let { ScanPageImage(it, Modifier.fillMaxWidth()) } }
    )

    ocr?.let { OcrSheet(it) { viewModel.ocr.value = null } }
    if (renaming) {
        var title by rememberSaveable { mutableStateOf(s.document.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Rename") },
            text = {
                OutlinedTextField(title, {
                    title = it
                }, singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(title)
                    renaming = false
                }, enabled = title.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } }
        )
    }
    if (more) {
        UnidocBottomSheet(onDismissRequest = { more = false }) {
            NavigationRow("No folder", {
                viewModel.move(null)
                more = false
            }, icon = Icons.Rounded.FolderOff, value = if (s.document.folderId == null) "Current" else null)
            folders.forEach { f ->
                NavigationRow("Move to ${f.name}", {
                    viewModel.move(f.id)
                    more = false
                    Toast.makeText(context, "Moved to ${f.name}", Toast.LENGTH_SHORT).show()
                }, icon = Icons.Rounded.Folder, value = if (s.document.folderId == f.id) "Current" else null)
            }
            NavigationRow("Delete document", {
                more = false
                confirmDelete = true
            }, icon = Icons.Rounded.DeleteOutline)
            androidx.compose.foundation.layout.Spacer(Modifier.fillMaxWidth().padding(bottom = 24.dp))
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete “${s.document.title}”?",
            message = "Pages and edits are removed from Unidoc. Files you exported stay where you saved them.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
                onBack()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
