package com.rajdeep.unidoc.feature.converter.tools.preview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.rajdeep.unidoc.core.common.di.Dispatcher
import com.rajdeep.unidoc.core.common.di.UnidocDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Page count and size (points, after /Rotate as the platform renderer reports) of a PDF. */
data class PdfInfo(val pageCount: Int, val pageSizes: List<Pair<Int, Int>>) {
    fun aspect(page: Int): Float = pageSizes.getOrNull(page)?.let { (w, h) -> w.toFloat() / h } ?: 0.7071f
}

/**
 * Renders PDF page previews for the converter (inputs and outputs) with the platform renderer.
 * One renderer per document is kept open (PdfRenderer allows one page at a time, so access is
 * serialized); bitmaps are cached by memory size.
 */
@Singleton
class PdfPreviewRenderer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Dispatcher(UnidocDispatchers.IO) private val io: CoroutineDispatcher
) {
    private class Doc(val fd: ParcelFileDescriptor, val renderer: PdfRenderer, val info: PdfInfo, val lock: Mutex = Mutex())

    private val docs = object : LruCache<String, Doc>(4) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: Doc, newValue: Doc?) {
            closeQuietly(oldValue)
        }
    }
    private val openLocks = ConcurrentHashMap<String, Mutex>()
    private val bitmaps = object : LruCache<String, ImageBitmap>((Runtime.getRuntime().maxMemory() / 10).toInt()) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    suspend fun info(uri: String): PdfInfo? = withContext(io) { open(uri)?.info }

    /** Renders [page] to [widthPx] wide (height from the page aspect); null when the file can't be rendered. */
    suspend fun page(uri: String, page: Int, widthPx: Int): ImageBitmap? {
        val width = widthPx.coerceIn(32, 2400)
        val key = "$uri#$page@$width"
        bitmaps.get(key)?.let { return it }
        return withContext(io) {
            val doc = open(uri) ?: return@withContext null
            if (page !in 0 until doc.info.pageCount) return@withContext null
            doc.lock.withLock {
                runCatching {
                    doc.renderer.openPage(page).use { p ->
                        val height = (width.toFloat() * p.height / p.width).roundToInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp.asImageBitmap()
                    }
                }.getOrNull()
            }?.also { bitmaps.put(key, it) }
        }
    }

    /** Drops cached state for [uri] (e.g. after the file changed). */
    fun forget(uri: String) {
        docs.remove(uri)
        bitmaps.snapshot().keys.filter { it.startsWith("$uri#") }.forEach(bitmaps::remove)
    }

    private suspend fun open(uri: String): Doc? {
        docs.get(uri)?.let { return it }
        return openLocks.getOrPut(uri) { Mutex() }.withLock {
            docs.get(uri) ?: runCatching { openFresh(uri) }.getOrNull()?.also { docs.put(uri, it) }
        }
    }

    private fun openFresh(uri: String): Doc {
        val parsed = Uri.parse(uri)
        val fd = if (parsed.scheme == "file") {
            ParcelFileDescriptor.open(File(parsed.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            // Some providers return non-seekable pipes; copy those to cache first.
            val direct = context.contentResolver.openFileDescriptor(parsed, "r") ?: error("Unreadable")
            if (runCatching { android.system.Os.lseek(direct.fileDescriptor, 0, android.system.OsConstants.SEEK_SET) }.isSuccess) {
                direct
            } else {
                direct.close()
                val copy = File(context.cacheDir, "preview_${uri.hashCode()}.pdf")
                context.contentResolver.openInputStream(parsed)!!.use { input -> copy.outputStream().use { input.copyTo(it) } }
                ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
        return try {
            val renderer = PdfRenderer(fd)
            val sizes = (0 until renderer.pageCount).map { i -> renderer.openPage(i).use { it.width to it.height } }
            Doc(fd, renderer, PdfInfo(renderer.pageCount, sizes))
        } catch (e: Exception) {
            fd.close()
            throw e
        }
    }

    private fun closeQuietly(doc: Doc) {
        runCatching { doc.renderer.close() }
        runCatching { doc.fd.close() }
    }
}
