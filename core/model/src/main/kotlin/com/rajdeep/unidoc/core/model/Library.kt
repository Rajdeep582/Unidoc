package com.rajdeep.unidoc.core.model

/** A scanned or imported document in the library. */
data class ScanDocument(
    /** Equals the scan session id that stores the pages. */
    val id: String,
    val title: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val folderId: Long? = null,
    val hasOcrText: Boolean = false,
    val thumbnailPath: String? = null
)

data class Folder(val id: Long, val name: String, val documentCount: Int)

enum class SortOrder {
    RECENT,
    OLDEST,
    NAME_ASC,
    NAME_DESC,
    SIZE_DESC
}

enum class LibraryLayout {
    GRID,
    LIST
}
