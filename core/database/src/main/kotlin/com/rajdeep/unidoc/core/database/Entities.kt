package com.rajdeep.unidoc.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/** Library entry; [sessionId] points at the scan session folder holding the pages. */
@Entity(tableName = "documents", indices = [Index("folderId"), Index("updatedAt")])
data class DocumentEntity(
    @PrimaryKey val sessionId: String,
    val title: String,
    val folderId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val sizeBytes: Long,
    val thumbnailPath: String?,
    val hasOcr: Boolean
)

@Entity(tableName = "folders")
data class FolderEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val createdAt: Long)

/** Full-text index of recognized text, one row per page. */
@Fts4
@Entity(tableName = "page_text")
data class PageTextFts(
    @ColumnInfo(name = "sessionId") val sessionId: String,
    @ColumnInfo(name = "pageKey") val pageKey: String,
    @ColumnInfo(name = "text") val text: String
)

data class FolderWithCount(val id: Long, val name: String, val documentCount: Int)
