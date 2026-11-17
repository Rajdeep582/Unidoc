package com.rajdeep.unidoc.feature.converter.tools

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rajdeep.unidoc.core.common.di.Dispatcher
import com.rajdeep.unidoc.core.common.di.UnidocDispatchers
import com.rajdeep.unidoc.core.model.PickedFile
import com.rajdeep.unidoc.core.model.ToolId
import com.rajdeep.unidoc.core.model.ToolInput
import com.rajdeep.unidoc.core.model.ToolOutput
import com.rajdeep.unidoc.core.model.ToolProgress
import com.rajdeep.unidoc.core.model.ToolRequest
import com.rajdeep.unidoc.core.scan.annotation.SignatureStore
import com.rajdeep.unidoc.feature.converter.tools.navigation.ToolRoute
import com.rajdeep.unidoc.feature.converter.tools.preview.PdfPreviewRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ToolViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val jobs: ConversionJobs,
    @param:ApplicationContext private val context: Context,
    private val fileInfo: FileInfoReader,
    val previews: PdfPreviewRenderer,
    private val signatures: SignatureStore,
    private val settings: com.rajdeep.unidoc.core.datastore.SettingsRepository,
    @param:Dispatcher(UnidocDispatchers.IO) private val io: CoroutineDispatcher
) : ViewModel() {

    private val tool: ToolId = ToolId.valueOf(savedState.toRoute<ToolRoute>().toolId)
    private val _uiState = MutableStateFlow(restore())
    val uiState: StateFlow<ToolUiState> = _uiState.asStateFlow()
    private var job: Job? = null
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { signatures.signature.collect { sig -> _uiState.update { it.copy(signature = sig) } } }
        viewModelScope.launch {
            settings.settings.collect { s ->
                val ready =
                    com.rajdeep.unidoc.core.model.Features.CLOUD && s.cloudEnabled && s.cloudServerUrl.startsWith("https://")
                _uiState.update { it.copy(cloudReady = ready) }
            }
        }
        refreshPdfInfo()
        // Re-attach to a job that kept running while this screen was gone.
        savedState.get<String>(KEY_JOB)?.let { id -> if (!attach(id)) savedState.remove<String>(KEY_JOB) }
    }

    fun onAction(action: ToolAction) {
        when (action) {
            is ToolAction.FilesPicked -> viewModelScope.launch {
                val picked = withContext(io) { action.uris.map(fileInfo::read) }
                updateAndSave { s ->
                    val merged = (s.files + picked).distinctBy { it.uri }.take(tool.maxFiles)
                    s.copy(files = merged)
                }
                refreshPdfInfo()
            }
            is ToolAction.RemoveFile -> {
                updateAndSave { s -> s.copy(files = s.files.filterNot { it.uri == action.uri }) }
                refreshPdfInfo()
            }
            is ToolAction.OptionsChanged -> updateAndSave { s -> s.copy(options = s.options + action.values) }
            is ToolAction.SaveSignature -> viewModelScope.launch(io) { signatures.save(action.strokes, action.aspect) }
            is ToolAction.MoveFile -> updateAndSave { s ->
                if (action.to !in s.files.indices) return@updateAndSave s
                s.copy(files = s.files.toMutableList().apply { add(action.to, removeAt(action.from)) })
            }
            is ToolAction.OptionChanged -> updateAndSave { s ->
                s.copy(
                    options =
                    s.options + (action.key to action.value)
                )
            }
            ToolAction.Run -> run()
            ToolAction.Cancel -> {
                job?.cancel()
                savedState.get<String>(KEY_JOB)?.let(jobs::cancel)
                savedState.remove<String>(KEY_JOB)
                _uiState.update { it.copy(step = ToolStep.Input) }
            }
            ToolAction.Reset -> {
                savedState.remove<String>(KEY_JOB)
                updateAndSave { ToolUiState(tool) }
            }
            ToolAction.BackToInput -> {
                savedState.remove<String>(KEY_JOB)
                _uiState.update { it.copy(step = ToolStep.Input) }
            }
            is ToolAction.SaveTo -> save(action.target, action.isFolder)
        }
    }

    private fun run() {
        val state = _uiState.value
        if (state.blocker != null) return
        val id = jobs.start(ToolRequest(tool, state.files, state.options))
        savedState[KEY_JOB] = id
        attach(id)
    }

    /** Loads page info for a single input PDF; page-specific options are reset when the file changes. */
    private fun refreshPdfInfo() {
        val file = _uiState.value.files.singleOrNull()?.takeIf { tool.input == ToolInput.PDF }
        if (file == null) {
            _uiState.update { it.copy(pdfInfo = null) }
            return
        }
        viewModelScope.launch {
            val info = previews.info(file.uri)
            val previous = savedState.get<String>(KEY_INFO_URI)
            savedState[KEY_INFO_URI] = file.uri
            if (previous != null && previous != file.uri) {
                updateAndSave { s ->
                    s.copy(
                        options =
                        s.options - PAGE_KEYS + PAGE_KEYS.associateWith { k -> ToolOptions.defaults(tool)[k] ?: "" }
                    )
                }
            }
            _uiState.update { if (it.files.singleOrNull()?.uri == file.uri) it.copy(pdfInfo = info) else it }
        }
    }

    private fun attach(id: String): Boolean {
        val flow = jobs.observe(id) ?: return false
        job?.cancel()
        job = viewModelScope.launch {
            flow.collect { progress ->
                _uiState.update {
                    it.copy(
                        step = when (progress) {
                            is ToolProgress.Running -> ToolStep.Processing(progress.fraction, progress.stage)
                            is ToolProgress.Completed -> ToolStep.Done(progress.outputs, progress.inputBytes, progress.note)
                            is ToolProgress.Failed -> ToolStep.Error(progress.message, progress.retryable)
                        }
                    )
                }
            }
        }
        return true
    }

    /** Copies outputs to a user-picked file (single output) or folder (several outputs). */
    private fun save(target: Uri, isFolder: Boolean) {
        val outputs = (uiState.value.step as? ToolStep.Done)?.outputs ?: return
        viewModelScope.launch {
            val result = runCatching {
                withContext(io) {
                    if (isFolder) {
                        val dir = DocumentFile.fromTreeUri(context, target) ?: error("Folder not available")
                        outputs.forEach { out ->
                            val dest = dir.createFile(out.mimeType, out.name) ?: error("Couldn't create ${out.name}")
                            copy(out, dest.uri)
                        }
                    } else {
                        copy(outputs.first(), target)
                    }
                }
            }
            _messages.send(
                result.fold(
                    { if (outputs.size > 1 && isFolder) "Saved ${outputs.size} files" else "Saved" },
                    { "Couldn't save: ${it.message}" }
                )
            )
        }
    }

    private fun copy(out: ToolOutput, dest: Uri) {
        val resolver = context.contentResolver
        resolver.openInputStream(Uri.parse(out.uri))!!.use { input ->
            (resolver.openOutputStream(dest, "wt") ?: error("Can't write there")).use { input.copyTo(it) }
        }
    }

    private fun updateAndSave(transform: (ToolUiState) -> ToolUiState) {
        _uiState.update(transform)
        val s = _uiState.value
        savedState[KEY_FILES] =
            ArrayList(
                s.files.map {
                    listOf(it.uri, it.name, it.sizeBytes.toString(), it.mimeType.orEmpty()).joinToString(SEP)
                }
            )
        savedState[KEY_OPTIONS] = HashMap(s.options)
    }

    private fun restore(): ToolUiState {
        val files = savedState.get<ArrayList<String>>(KEY_FILES).orEmpty().mapNotNull { row ->
            val p = row.split(SEP)
            if (p.size == 4) PickedFile(p[0], p[1], p[2].toLongOrNull() ?: -1, p[3].ifEmpty { null }) else null
        }
        val options = savedState.get<HashMap<String, String>>(KEY_OPTIONS) ?: ToolOptions.defaults(tool)
        return ToolUiState(tool = tool, files = files, options = ToolOptions.defaults(tool) + options)
    }

    private companion object {
        const val KEY_FILES = "files"
        const val KEY_OPTIONS = "options"
        const val KEY_JOB = "job"
        const val KEY_INFO_URI = "info_uri"
        val PAGE_KEYS = setOf("order", "rotate", "pages", KEY_PLACE)
        const val SEP = "\u001F"
    }
}
