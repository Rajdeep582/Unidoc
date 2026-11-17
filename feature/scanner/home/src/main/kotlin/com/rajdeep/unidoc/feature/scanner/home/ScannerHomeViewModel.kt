package com.rajdeep.unidoc.feature.scanner.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajdeep.unidoc.core.data.LibraryRepository
import com.rajdeep.unidoc.core.model.ExportOptions
import com.rajdeep.unidoc.core.model.LibraryLayout
import com.rajdeep.unidoc.core.model.SortOrder
import com.rajdeep.unidoc.core.pdf.DocumentExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HomeEffect {
    data class Share(val files: List<File>) : HomeEffect

    data class Open(val id: String) : HomeEffect

    data class Message(val text: String) : HomeEffect
}

private data class HomeLocal(
    val query: String,
    val sort: SortOrder = SortOrder.RECENT,
    val layout: LibraryLayout,
    val selectedFolderId: Long? = null,
    val selectedIds: Set<String> = emptySet(),
    val busy: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ScannerHomeViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val library: LibraryRepository,
    private val exporter: DocumentExporter
) : ViewModel() {

    private val local = MutableStateFlow(
        HomeLocal(
            query = savedState[KEY_QUERY] ?: "",
            layout = savedState.get<String>(KEY_LAYOUT)?.let(LibraryLayout::valueOf) ?: LibraryLayout.GRID
        )
    )
    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private val textMatches = local.map { it.query }.debounce(200).flatMapLatest { library.searchText(it) }

    val busy: StateFlow<String?> = local.map { it.busy }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<ScannerHomeUiState> = combine(library.documents, library.folders, textMatches, local) {
            docs,
            folders,
            matches,
            l
        ->
        ScannerHomeUiState(
            status = LibraryStatus.Ready,
            documents = docs,
            folders = folders,
            query = l.query,
            sort = l.sort,
            layout = l.layout,
            selectedFolderId = l.selectedFolderId?.takeIf { id -> folders.any { it.id == id } },
            selectedIds = l.selectedIds.filter { id -> docs.any { it.id == id } }.toSet(),
            textMatches = matches
        )
    }
        .catch { emit(ScannerHomeUiState(status = LibraryStatus.Error(it.message ?: "Library unavailable"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScannerHomeUiState())

    fun onAction(action: ScannerHomeAction) {
        when (action) {
            is ScannerHomeAction.QueryChanged -> {
                savedState[KEY_QUERY] = action.query
                local.update { it.copy(query = action.query) }
            }
            is ScannerHomeAction.SortChanged -> local.update { it.copy(sort = action.sort) }
            ScannerHomeAction.ToggleLayout -> local.update {
                val next = if (it.layout == LibraryLayout.GRID) LibraryLayout.LIST else LibraryLayout.GRID
                savedState[KEY_LAYOUT] = next.name
                it.copy(layout = next)
            }
            is ScannerHomeAction.FolderSelected -> local.update { it.copy(selectedFolderId = action.folderId) }
            is ScannerHomeAction.ToggleSelection -> local.update {
                val ids = it.selectedIds
                it.copy(selectedIds = if (action.documentId in ids) ids - action.documentId else ids + action.documentId)
            }
            ScannerHomeAction.SelectAll -> local.update { it.copy(selectedIds = uiState.value.visibleDocuments.map { d -> d.id }.toSet()) }
            ScannerHomeAction.ClearSelection -> local.update { it.copy(selectedIds = emptySet()) }
            ScannerHomeAction.Retry -> Unit
        }
    }

    fun share(ids: Set<String>) = work("Preparing ${ids.size} PDF${if (ids.size > 1) "s" else ""}…") {
        val titles = uiState.value.documents.associate { it.id to it.title }
        val files = ids.flatMap { id -> exporter.export(id, titles[id].orEmpty(), ExportOptions()) }
        _effects.send(HomeEffect.Share(files))
    }

    fun merge(ids: Set<String>) = work("Merging…") {
        val ordered = uiState.value.visibleDocuments.map { it.id }.filter { it in ids }.reversed()
        val id = library.merge(ordered)
        _effects.send(HomeEffect.Open(id))
    }

    fun delete(ids: Set<String>) = work(null) { library.delete(ids) }

    fun move(ids: Set<String>, folderId: Long?) = work(null) {
        library.move(ids, folderId)
        _effects.send(HomeEffect.Message("Moved ${ids.size} ${if (ids.size == 1) "document" else "documents"}"))
    }

    fun moveToNewFolder(ids: Set<String>, name: String) = work(null) {
        val folder = library.createFolder(name)
        library.move(ids, folder)
    }

    fun renameFolder(id: Long, name: String) = work(null) { library.renameFolder(id, name) }

    fun deleteFolder(id: Long) = work(null) { library.deleteFolder(id) }

    private fun work(busyLabel: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            local.update { it.copy(busy = busyLabel, selectedIds = emptySet()) }
            runCatching { block() }.onFailure { _effects.send(HomeEffect.Message(it.message ?: "Something went wrong")) }
            local.update { it.copy(busy = null) }
        }
    }

    private companion object {
        const val KEY_QUERY = "query"
        const val KEY_LAYOUT = "layout"
    }
}
