package com.rajdeep.unidoc.feature.scanner.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajdeep.unidoc.core.datastore.SettingsRepository
import com.rajdeep.unidoc.core.model.ExportFormat
import com.rajdeep.unidoc.core.model.ExportOptions
import com.rajdeep.unidoc.core.pdf.DocumentExporter
import com.rajdeep.unidoc.core.scan.ScanRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ExportPhase {
    data object Editing : ExportPhase

    data class Running(val fraction: Float, val stage: String) : ExportPhase

    data class Done(val files: List<File>) : ExportPhase {
        val totalBytes: Long get() = files.sumOf { it.length() }
    }

    data class Failed(val message: String) : ExportPhase
}

@HiltViewModel(assistedFactory = ExportViewModel.Factory::class)
class ExportViewModel @AssistedInject constructor(
    @Assisted private val sessionId: String,
    private val exporter: DocumentExporter,
    private val scans: ScanRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(sessionId: String): ExportViewModel
    }

    private val _state = MutableStateFlow(ExportUiState(fileName = scans.observe(sessionId).value?.title.orEmpty()))
    val state: StateFlow<ExportUiState> = _state.asStateFlow()
    private val _phase = MutableStateFlow<ExportPhase>(ExportPhase.Editing)
    val phase: StateFlow<ExportPhase> = _phase.asStateFlow()

    private var estimateJob: Job? = null
    private var exportJob: Job? = null

    init {
        exporter.clearOldExports()
        viewModelScope.launch {
            val quality = settings.settings.first().defaultQuality
            _state.update { it.copy(options = it.options.copy(quality = quality)) }
            estimate()
        }
    }

    fun onFileName(name: String) = _state.update { it.copy(fileName = name) }

    fun onOptions(options: ExportOptions) {
        val searchableChanged = options.searchable != _state.value.options.searchable
        _state.update { it.copy(options = options) }
        if (searchableChanged) estimate()
    }

    private fun estimate() {
        estimateJob?.cancel()
        estimateJob = viewModelScope.launch {
            delay(150)
            _state.update { it.copy(estimates = emptyMap()) }
            val sizes = runCatching { exporter.estimatePdfSizes(sessionId, _state.value.options.searchable) }.getOrDefault(emptyMap())
            _state.update { it.copy(estimates = sizes) }
        }
    }

    fun export() {
        val s = _state.value
        exportJob?.cancel()
        _phase.value = ExportPhase.Running(0f, "Preparing")
        exportJob = viewModelScope.launch {
            _phase.value = runCatching {
                exporter.export(sessionId, s.fileName, s.options) { p ->
                    _phase.value = ExportPhase.Running(if (p.total == 0) 0f else p.done.toFloat() / p.total, p.stage)
                }
            }.fold({ ExportPhase.Done(it) }, { ExportPhase.Failed(it.message ?: "Export failed") })
        }
    }

    /** Fast path used by "Share": Balanced PDF with text layer. */
    fun quickShare() {
        _state.update { it.copy(options = it.options.copy(format = ExportFormat.PDF, password = null)) }
        export()
    }

    fun cancel() {
        exportJob?.cancel()
        _phase.value = ExportPhase.Editing
    }

    fun backToOptions() {
        _phase.value = ExportPhase.Editing
    }
}
