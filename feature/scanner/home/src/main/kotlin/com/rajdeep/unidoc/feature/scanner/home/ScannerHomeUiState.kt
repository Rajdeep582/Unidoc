package com.rajdeep.unidoc.feature.scanner.home

import com.rajdeep.unidoc.core.model.Folder
import com.rajdeep.unidoc.core.model.LibraryLayout
import com.rajdeep.unidoc.core.model.ScanDocument
import com.rajdeep.unidoc.core.model.SortOrder

sealed interface LibraryStatus {
    data object Loading : LibraryStatus

    data object Ready : LibraryStatus

    data class Error(val message: String) : LibraryStatus
}

data class ScannerHomeUiState(
    val status: LibraryStatus = LibraryStatus.Loading,
    val documents: List<ScanDocument> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val query: String = "",
    val sort: SortOrder = SortOrder.RECENT,
    val layout: LibraryLayout = LibraryLayout.GRID,
    val selectedFolderId: Long? = null,
    val selectedIds: Set<String> = emptySet(),
    /** Documents whose recognized text matches [query]. */
    val textMatches: Set<String> = emptySet()
) {
    val selectionMode: Boolean get() = selectedIds.isNotEmpty()

    /** Documents after folder filter, search and sort. */
    val visibleDocuments: List<ScanDocument>
        get() {
            val q = query.trim()
            return documents
                .asSequence()
                .filter { selectedFolderId == null || it.folderId == selectedFolderId }
                .filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) || it.id in textMatches }
                .sortedWith(
                    when (sort) {
                        SortOrder.RECENT -> compareByDescending { it.updatedAtMillis }
                        SortOrder.OLDEST -> compareBy { it.updatedAtMillis }
                        SortOrder.NAME_ASC -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                        SortOrder.NAME_DESC -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title }
                        SortOrder.SIZE_DESC -> compareByDescending { it.sizeBytes }
                    }
                )
                .toList()
        }
}

sealed interface ScannerHomeAction {
    data class QueryChanged(val query: String) : ScannerHomeAction

    data class SortChanged(val sort: SortOrder) : ScannerHomeAction

    data object ToggleLayout : ScannerHomeAction

    data class FolderSelected(val folderId: Long?) : ScannerHomeAction

    data class ToggleSelection(val documentId: String) : ScannerHomeAction

    data object SelectAll : ScannerHomeAction

    data object ClearSelection : ScannerHomeAction

    data object Retry : ScannerHomeAction
}
