package com.rajdeep.unidoc.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE sessionId = :id")
    suspend fun document(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE sessionId = :id")
    fun observeDocument(id: String): Flow<DocumentEntity?>

    @Upsert
    suspend fun upsert(doc: DocumentEntity)

    @Query("UPDATE documents SET title = :title, updatedAt = :now WHERE sessionId = :id")
    suspend fun rename(id: String, title: String, now: Long)

    @Query("UPDATE documents SET folderId = :folderId WHERE sessionId IN (:ids)")
    suspend fun move(ids: List<String>, folderId: Long?)

    @Query("DELETE FROM documents WHERE sessionId IN (:ids)")
    suspend fun deleteDocuments(ids: List<String>)

    @Query(
        "SELECT f.id AS id, f.name AS name, COUNT(d.sessionId) AS documentCount FROM folders f " +
            "LEFT JOIN documents d ON d.folderId = f.id GROUP BY f.id ORDER BY f.name COLLATE NOCASE"
    )
    fun observeFolders(): Flow<List<FolderWithCount>>

    @Upsert
    suspend fun upsertFolder(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun renameFolder(id: Long, name: String)

    @Transaction
    suspend fun deleteFolder(id: Long) {
        clearFolder(id)
        deleteFolderRow(id)
    }

    @Query("UPDATE documents SET folderId = NULL WHERE folderId = :id")
    suspend fun clearFolder(id: Long)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderRow(id: Long)

    // ---- full-text search ----

    @Query("SELECT DISTINCT sessionId FROM page_text WHERE page_text MATCH :query")
    fun search(query: String): Flow<List<String>>

    @Query("SELECT pageKey FROM page_text WHERE sessionId = :sessionId")
    suspend fun indexedPages(sessionId: String): List<String>

    @Query("INSERT INTO page_text(sessionId, pageKey, text) VALUES (:sessionId, :pageKey, :text)")
    suspend fun insertText(sessionId: String, pageKey: String, text: String)

    @Query("DELETE FROM page_text WHERE sessionId = :sessionId AND pageKey NOT IN (:keep)")
    suspend fun pruneText(sessionId: String, keep: List<String>)

    @Query("DELETE FROM page_text WHERE sessionId IN (:ids)")
    suspend fun deleteText(ids: List<String>)

    @Query("SELECT text FROM page_text WHERE sessionId = :sessionId")
    suspend fun textOf(sessionId: String): List<String>
}
