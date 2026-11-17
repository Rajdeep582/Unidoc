package com.rajdeep.unidoc.core.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.rajdeep.unidoc.core.common.di.Dispatcher
import com.rajdeep.unidoc.core.common.di.UnidocDispatchers
import com.rajdeep.unidoc.core.datastore.SettingsRepository
import com.rajdeep.unidoc.core.imaging.Quad
import com.rajdeep.unidoc.core.model.ScanFilter
import com.rajdeep.unidoc.core.model.ScanMode
import com.rajdeep.unidoc.core.scan.annotation.PageAnnotation
import com.rajdeep.unidoc.core.scan.annotation.WatermarkAnnotation
import com.rajdeep.unidoc.core.scan.ocr.OcrEngine
import com.rajdeep.unidoc.core.scan.ocr.OcrResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Facade for capture, import, page editing and OCR over durable scan sessions. */
@Singleton
class ScanRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: ScanSessionStore,
    private val processor: PageProcessor,
    private val ocr: OcrEngine,
    private val settings: SettingsRepository,
    @param:Dispatcher(UnidocDispatchers.Default) private val cpu: CoroutineDispatcher,
    @param:Dispatcher(UnidocDispatchers.IO) private val io: CoroutineDispatcher
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val json = Json { ignoreUnknownKeys = true }

    /** Heavy image work is limited to two pages at a time to bound memory. */
    private val imageWork = cpu.limitedParallelism(2)

    private fun lock(id: String) = locks.getOrPut(id) { Mutex() }

    fun observe(sessionId: String): StateFlow<ScanSession?> = store.observe(sessionId)

    fun file(sessionId: String, name: String): File = store.file(sessionId, name)

    fun createSession(mode: ScanMode): ScanSession {
        val stamp = SimpleDateFormat("d MMM yyyy, HH.mm", Locale.getDefault()).format(Date())
        val title = when (mode) {
            ScanMode.ID_CARD -> "ID card $stamp"
            ScanMode.RECEIPT -> "Receipt $stamp"
            ScanMode.WHITEBOARD -> "Whiteboard $stamp"
            ScanMode.BOOK -> "Book $stamp"
            else -> "Scan $stamp"
        }
        return store.create(ScanSession(store.newId(), System.currentTimeMillis(), mode, title))
    }

    private fun defaultFilter(mode: ScanMode): ScanFilter = when (mode) {
        ScanMode.RECEIPT -> ScanFilter.BLACK_WHITE
        ScanMode.WHITEBOARD -> ScanFilter.WHITEBOARD
        ScanMode.ID_CARD -> ScanFilter.ENHANCED
        else -> ScanFilter.AUTO
    }

    /**
     * Adds a captured photo (moved into the session). For ID cards, a capture while the last page
     * has no back side becomes that back side. Book captures yield two pages (left, right).
     * Returns ids of pages that were added or changed.
     */
    suspend fun addCapture(sessionId: String, photo: File): List<String> = withContext(io) {
        lock(sessionId).withLock {
            val session = store.get(sessionId) ?: error("Session missing")
            val name = "o_${store.newId()}.jpg"
            check(photo.renameTo(store.file(sessionId, name)) || photo.copyTo(store.file(sessionId, name)).exists())
            photo.delete()
            val last = session.pages.lastOrNull()
            val changed: List<SessionPage> = when {
                session.mode == ScanMode.ID_CARD && last != null && last.backOriginal == null ->
                    listOf(last.copy(backOriginal = name))
                session.mode == ScanMode.BOOK -> listOf(
                    SessionPage(store.newId(), name, filter = defaultFilter(session.mode), region = PageRegion.LEFT),
                    SessionPage(store.newId(), name, filter = defaultFilter(session.mode), region = PageRegion.RIGHT)
                )
                else -> listOf(SessionPage(store.newId(), name, filter = defaultFilter(session.mode)))
            }
            store.update(sessionId) { s ->
                val ids = changed.map { it.id }.toSet()
                val replaced = s.pages.map { p -> changed.firstOrNull { it.id == p.id } ?: p }
                s.copy(pages = replaced + changed.filter { c -> s.pages.none { it.id == c.id } }.filter { it.id in ids })
            }
            changed.map { it.id }
        }.also { ids -> ids.forEach { render(sessionId, it) } }
    }

    /** Imports gallery images; each is auto-cropped. Returns the new session id. */
    suspend fun importImages(uris: List<Uri>): String = withContext(io) {
        val session = createSession(ScanMode.DOCUMENT)
        val pages = uris.map { uri ->
            val name = "o_${store.newId()}.jpg"
            context.contentResolver.openInputStream(uri)?.use { MatIO.copyStream(it, store.file(session.id, name)) }
                ?: error("Can't open ${uri.lastPathSegment}")
            SessionPage(store.newId(), name, filter = ScanFilter.AUTO)
        }
        store.update(session.id) { it.copy(title = displayName(uris.first()) ?: it.title, pages = pages) }
        pages.forEach { render(session.id, it.id) }
        session.id
    }

    /** Imports a PDF: every page rendered at ~200 dpi, kept uncropped with the Original filter. */
    suspend fun importPdf(uri: Uri): String = withContext(io) {
        val session = createSession(ScanMode.DOCUMENT)
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Can't open PDF")
        val pages = pfd.use { fd ->
            PdfRenderer(fd).use { renderer ->
                (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        val scale = 200f / 72f
                        val w = (page.width * scale).roundToInt().coerceAtMost(MatIO.MAX_ORIGINAL_SIDE)
                        val h = (page.height * w / page.width.toFloat()).roundToInt()
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val name = "o_${store.newId()}.jpg"
                        store.file(session.id, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }
                        bmp.recycle()
                        SessionPage(store.newId(), name, corners = emptyList(), filter = ScanFilter.ORIGINAL, warp = false)
                    }
                }
            }
        }
        require(pages.isNotEmpty()) { "The PDF has no pages" }
        store.update(session.id) { it.copy(title = displayName(uri)?.removeSuffix(".pdf") ?: it.title, pages = pages) }
        pages.forEach { render(session.id, it.id) }
        session.id
    }

    suspend fun setFilter(sessionId: String, pageId: String, filter: ScanFilter) = edit(sessionId, pageId) { it.copy(filter = filter) }

    suspend fun applyFilterToAll(sessionId: String, filter: ScanFilter) {
        val ids = store.get(sessionId)?.pages?.filter { it.filter != filter }?.map { it.id }.orEmpty()
        ids.forEach { edit(sessionId, it) { p -> p.copy(filter = filter) } }
    }

    suspend fun rotate(sessionId: String, pageId: String, delta: Int) =
        edit(sessionId, pageId) { it.copy(rotation = ((it.rotation + delta) % 360 + 360) % 360) }

    /** [quad] normalized; null re-runs detection; [Quad.full] = no crop. */
    suspend fun setCorners(sessionId: String, pageId: String, quad: Quad?) = withContext(NonCancellable) {
        edit(sessionId, pageId) { it.copy(corners = quad?.toList(), bends = null, warp = true) }
        // Book spreads: both halves come from one photo, so they share the page outline.
        val session = store.get(sessionId) ?: return@withContext
        val page = session.pages.firstOrNull { it.id == pageId } ?: return@withContext
        if (page.region != PageRegion.FULL) {
            session.pages.filter { it.id != pageId && it.original == page.original }.forEach { twin ->
                edit(sessionId, twin.id) { it.copy(corners = quad?.toList(), bends = null, warp = true) }
            }
        }
    }

    suspend fun detectCorners(sessionId: String, pageId: String): Quad? = withContext(imageWork) {
        val page = store.get(sessionId)?.pages?.firstOrNull { it.id == pageId } ?: return@withContext null
        processor.detectCorners(sessionId, page.original)
    }

    suspend fun setAdjustments(sessionId: String, pageId: String, brightness: Float, contrast: Float, saturation: Float, sharpness: Float) =
        edit(sessionId, pageId) { it.copy(brightness = brightness, contrast = contrast, saturation = saturation, sharpness = sharpness) }

    suspend fun setTilt(sessionId: String, pageId: String, degrees: Float) = edit(sessionId, pageId) {
        it.copy(tilt = degrees.coerceIn(-45f, 45f))
    }

    suspend fun toggleFlip(sessionId: String, pageId: String) = edit(sessionId, pageId) { it.copy(flipHorizontal = !it.flipHorizontal) }

    /** Markup is an overlay: saved without re-rendering the page. */
    suspend fun setAnnotations(sessionId: String, pageId: String, annotations: List<PageAnnotation>) =
        edit(sessionId, pageId, render = false) { it.copy(annotations = annotations) }

    /** Adds (or with null text removes) the same watermark on every page. */
    suspend fun setWatermark(sessionId: String, watermark: WatermarkAnnotation?) = lock(sessionId).withLock {
        store.update(sessionId) { s ->
            s.copy(
                pages = s.pages.map { p ->
                    p.copy(
                        annotations =
                        p.annotations.filterNot { it is WatermarkAnnotation } + listOfNotNull(watermark)
                    )
                }
            )
        }
    }

    suspend fun reorder(sessionId: String, orderedIds: List<String>) = lock(sessionId).withLock {
        store.update(sessionId) { s ->
            val byId = s.pages.associateBy { it.id }
            s.copy(pages = orderedIds.mapNotNull { byId[it] } + s.pages.filter { it.id !in orderedIds })
        }
    }

    /** Duplicates a page right after itself; files are shared until either copy is re-rendered. */
    suspend fun duplicate(sessionId: String, pageId: String): String? = lock(sessionId).withLock {
        var newId: String? = null
        store.update(sessionId) { s ->
            val i = s.pages.indexOfFirst { it.id == pageId }
            if (i < 0) return@update s
            val copy = s.pages[i].copy(id = store.newId(), ocr = null)
            newId = copy.id
            s.copy(pages = s.pages.toMutableList().apply { add(i + 1, copy) })
        }
        newId
    }

    /** Retake: swaps a page's photo, keeping its filter and adjustments; corners re-detected. */
    suspend fun replaceOriginal(sessionId: String, pageId: String, photo: File) {
        val name = "o_${store.newId()}.jpg"
        withContext(io) {
            if (!photo.renameTo(store.file(sessionId, name))) photo.copyTo(store.file(sessionId, name), overwrite = true)
            photo.delete()
        }
        edit(sessionId, pageId) {
            it.copy(
                original = name,
                corners = null,
                bends = null,
                backOriginal = null,
                backCorners = null,
                ocr = null,
                annotations = emptyList()
            )
        }
    }

    /** Appends gallery images to an existing session. */
    suspend fun addImages(sessionId: String, uris: List<Uri>): List<String> = withContext(io) {
        val pages = uris.map { uri ->
            val name = "o_${store.newId()}.jpg"
            context.contentResolver.openInputStream(uri)?.use { MatIO.copyStream(it, store.file(sessionId, name)) }
                ?: error("Can't open ${uri.lastPathSegment}")
            SessionPage(store.newId(), name, filter = ScanFilter.AUTO)
        }
        lock(sessionId).withLock { store.update(sessionId) { it.copy(pages = it.pages + pages) } }
        pages.forEach { render(sessionId, it.id) }
        pages.map { it.id }
    }

    /**
     * Restores page edit state (undo/redo). Pages whose pixels inputs match the current render keep
     * their images; others are re-rendered.
     */
    suspend fun restorePages(sessionId: String, pages: List<SessionPage>) {
        val toRender = ArrayList<String>()
        lock(sessionId).withLock {
            store.update(sessionId) { s ->
                val current = s.pages.associateBy { it.id }
                s.copy(
                    pages = pages.map { target ->
                        val now = current[target.id]
                        when {
                            now == null -> target.also { toRender += it.id }
                            now.renderInputs == target.renderInputs -> now.copy(annotations = target.annotations)
                            else -> target.copy(
                                processed = now.processed,
                                thumbnail = now.thumbnail,
                                version = now.version,
                                editStamp = now.editStamp + 1,
                                renderedStamp = now.renderedStamp
                            ).also { toRender += it.id }
                        }
                    }
                )
            }
        }
        toRender.forEach { render(sessionId, it) }
    }

    /** New session containing all pages of [sessionIds] in order (files copied, no re-render). */
    suspend fun merge(sessionIds: List<String>, title: String): String = withContext(io) {
        val target = createSession(ScanMode.DOCUMENT)
        val pages = sessionIds.flatMap { id ->
            val src = store.get(id) ?: return@flatMap emptyList()
            src.pages.map { p ->
                listOfNotNull(p.original, p.backOriginal, p.processed, p.thumbnail).forEach { name ->
                    val from = store.file(id, name)
                    val to = store.file(target.id, name)
                    if (from.exists() && !to.exists()) from.copyTo(to)
                }
                p.copy(id = store.newId(), ocr = null)
            }
        }
        store.update(target.id) { it.copy(title = title, pages = pages) }
        target.id
    }

    suspend fun deletePage(sessionId: String, pageId: String) = lock(sessionId).withLock {
        store.update(sessionId) { s -> s.copy(pages = s.pages.filterNot { it.id == pageId }) }
        store.prune(sessionId)
    }

    suspend fun rename(sessionId: String, title: String) = lock(sessionId).withLock {
        store.update(sessionId) { it.copy(title = title.trim().ifEmpty { it.title }) }
    }

    fun discard(sessionId: String) = store.delete(sessionId)

    /** OCR for a page, cached per processed version. */
    suspend fun recognizeText(sessionId: String, pageId: String): OcrResult = withContext(io) {
        val page = store.get(sessionId)?.pages?.firstOrNull { it.id == pageId } ?: error("Page missing")
        val cacheName = "ocr_${page.processed?.substringBeforeLast('.') ?: "${page.id}_v${page.version}"}.json"
        val cache = store.file(sessionId, cacheName)
        if (page.ocr == cacheName && cache.exists()) {
            return@withContext json.decodeFromString(OcrResult.serializer(), cache.readText())
        }
        val processed = page.processed ?: error("Page not ready")
        val languages = settings.settings.first().ocrLanguages
        val result = ocr.recognize(store.file(sessionId, processed), languages)
        cache.writeText(json.encodeToString(OcrResult.serializer(), result))
        lock(sessionId).withLock {
            store.update(sessionId) { s -> s.copy(pages = s.pages.map { if (it.id == pageId) it.copy(ocr = cacheName) else it }) }
        }
        result
    }

    /**
     * Re-renders pages whose edit state is newer than their image (e.g. the app was killed mid-render).
     * Cheap no-op when everything is current.
     */
    suspend fun ensureRendered(sessionId: String) {
        val stale = store.get(sessionId)?.pages?.filter { it.isRendering }?.map { it.id }.orEmpty()
        stale.forEach { render(sessionId, it) }
    }

    /**
     * An edit always completes — state change and re-render — even if the screen that asked for it
     * closes meanwhile (e.g. crop "Apply" pops the screen at once). Otherwise the new corners are
     * stored but the old image stays on screen.
     */
    private suspend fun edit(sessionId: String, pageId: String, render: Boolean = true, change: (SessionPage) -> SessionPage) =
        withContext(NonCancellable) { editNow(sessionId, pageId, render, change) }

    private suspend fun editNow(sessionId: String, pageId: String, render: Boolean, change: (SessionPage) -> SessionPage) {
        lock(sessionId).withLock {
            store.update(sessionId) { s ->
                s.copy(
                    pages = s.pages.map { page ->
                        when {
                            page.id != pageId -> page
                            render -> change(page).copy(editStamp = page.editStamp + 1)
                            else -> change(page)
                        }
                    }
                )
            }
        }
        if (render) render(sessionId, pageId)
    }

    private suspend fun render(sessionId: String, pageId: String) = withContext(NonCancellable) {
        renderLock("$sessionId/$pageId").withLock { renderNow(sessionId, pageId) }
    }

    private val renderLocks = ConcurrentHashMap<String, Mutex>()

    private fun renderLock(key: String) = renderLocks.getOrPut(key) { Mutex() }

    private suspend fun renderNow(sessionId: String, pageId: String) = withContext(imageWork) {
        val session = store.get(sessionId) ?: return@withContext
        val page = session.pages.firstOrNull { it.id == pageId } ?: return@withContext
        if (!page.isRendering) return@withContext // already current (a queued duplicate request)
        val rendered = processor.render(session, page)
        lock(sessionId).withLock {
            store.update(sessionId) { s ->
                s.copy(
                    pages = s.pages.map { current ->
                        // Only apply if no newer edit happened meanwhile.
                        if (current.id == pageId && current.editStamp == page.editStamp && current.backOriginal == page.backOriginal) {
                            rendered
                        } else {
                            current
                        }
                    }
                )
            }
            store.prune(sessionId)
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.substringBeforeLast('.') else null
        }
    }.getOrNull()
}
