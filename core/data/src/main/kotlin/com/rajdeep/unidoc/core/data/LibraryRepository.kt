package com.rajdeep.unidoc.core.data

import com.rajdeep.unidoc.core.common.di.ApplicationScope
import com.rajdeep.unidoc.core.database.DocumentEntity
import com.rajdeep.unidoc.core.database.FolderEntity
import com.rajdeep.unidoc.core.database.LibraryDao
import com.rajdeep.unidoc.core.model.Folder
import com.rajdeep.unidoc.core.model.ScanDocument
import com.rajdeep.unidoc.core.scan.ScanRepository
import com.rajdeep.unidoc.core.scan.ScanSession
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * The document library. Each document is a scan session (pages + edit state on disk) indexed in
 * Room for listing, folders and full-text search over recognized text.
 */
@Singleton
class LibraryRepository @Inject constructor(
    private val dao: LibraryDao,
    private val scans: ScanRepository,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private val ocrPermits = Semaphore(1)
    private val indexJobs = ConcurrentHashMap<String, Job>()

    val documents: Flow<List<ScanDocument>> = dao.observeDocuments().map { list -> list.map { it.toModel() } }

    val folders: Flow<List<Folder>> = dao.observeFolders().map { list -> list.map { Folder(it.id, it.name, it.documentCount) } }

    fun observeDocument(id: String): Flow<ScanDocument?> = dao.observeDocument(id).map { it?.toModel() }

    /** Session ids whose recognized text matches every word of [query] (prefix match). */
    fun searchText(query: String): Flow<Set<String>> {
        val fts = toFtsQuery(query) ?: return flowOf(emptySet())
        return dao.search(fts).map { it.toSet() }
    }

    /** Creates or refreshes the library entry from the session on disk. */
    suspend fun sync(session: ScanSession) {
        if (session.pages.isEmpty()) return
        val existing = dao.document(session.id)
        val size = session.pages.sumOf { p -> p.processed?.let { scans.file(session.id, it).length() } ?: 0L }
        val thumb = session.pages.firstOrNull()?.thumbnail?.let { scans.file(session.id, it).absolutePath }
        val entity = DocumentEntity(
            sessionId = session.id,
            title = session.title,
            folderId = existing?.folderId,
            createdAt = existing?.createdAt ?: session.createdAtMillis,
            updatedAt = if (existing == null ||
                existing.title != session.title ||
                existing.pageCount != session.pages.size ||
                existing.thumbnailPath != thumb
            ) {
                System.currentTimeMillis()
            } else {
                existing.updatedAt
            },
            pageCount = session.pages.size,
            sizeBytes = size,
            thumbnailPath = thumb,
            hasOcr = existing?.hasOcr ?: false
        )
        if (entity != existing) dao.upsert(entity)
        scheduleIndexing(session)
    }

    /** Recognizes text of unindexed pages in the background (one page at a time). */
    private fun scheduleIndexing(session: ScanSession) {
        if (session.pages.any { it.isRendering }) return
        indexJobs[session.id]?.cancel()
        indexJobs[session.id] = scope.launch {
            val keys = session.pages.map { it.id }
            dao.pruneText(session.id, keys)
            val done = dao.indexedPages(session.id).toSet()
            for (page in session.pages) {
                if (page.id in done) continue
                ocrPermits.withPermit {
                    runCatching { scans.recognizeText(session.id, page.id) }
                        .onSuccess { dao.insertText(session.id, page.id, it.text) }
                }
            }
            dao.document(session.id)?.let { if (!it.hasOcr) dao.upsert(it.copy(hasOcr = true)) }
        }
    }

    suspend fun rename(id: String, title: String) {
        val clean = title.trim().ifEmpty { return }
        scans.rename(id, clean)
        dao.rename(id, clean, System.currentTimeMillis())
    }

    suspend fun move(ids: Collection<String>, folderId: Long?) = dao.move(ids.toList(), folderId)

    suspend fun delete(ids: Collection<String>) {
        ids.forEach { indexJobs.remove(it)?.cancel() }
        dao.deleteText(ids.toList())
        dao.deleteDocuments(ids.toList())
        ids.forEach(scans::discard)
    }

    /** Merges documents in the given order into a new document; originals are kept. */
    suspend fun merge(ids: List<String>): String {
        val titles = ids.mapNotNull { dao.document(it)?.title }
        val id = scans.merge(ids, title = titles.firstOrNull()?.let { "$it (merged)" } ?: "Merged document")
        scans.observe(id).value?.let { sync(it) }
        return id
    }

    suspend fun createFolder(name: String): Long =
        dao.upsertFolder(FolderEntity(name = name.trim(), createdAt = System.currentTimeMillis()))

    suspend fun renameFolder(id: Long, name: String) = dao.renameFolder(id, name.trim())

    suspend fun deleteFolder(id: Long) = dao.deleteFolder(id)

    suspend fun allText(id: String): String = dao.textOf(id).joinToString("\n\n")

    private fun DocumentEntity.toModel() = ScanDocument(
        id = sessionId,
        title = title,
        pageCount = pageCount,
        sizeBytes = sizeBytes,
        createdAtMillis = createdAt,
        updatedAtMillis = updatedAt,
        folderId = folderId,
        hasOcrText = hasOcr,
        thumbnailPath = thumbnailPath
    )

    companion object {
        /** "elec bill" → `elec* bill*`; strips FTS syntax characters. Null when nothing searchable. */
        fun toFtsQuery(query: String): String? {
            val words = query.lowercase().split(Regex("\\s+"))
                .map { w ->
                    w.filter {
                        it.isLetterOrDigit() ||
                            Character.getType(it).toByte().let { t ->
                                t == Character.NON_SPACING_MARK || t == Character.COMBINING_SPACING_MARK
                            }
                    }
                }
                .filter { it.isNotEmpty() }
            return if (words.isEmpty()) null else words.joinToString(" ") { "$it*" }
        }
    }
}
