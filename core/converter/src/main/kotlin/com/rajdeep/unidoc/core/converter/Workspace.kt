package com.rajdeep.unidoc.core.converter

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.rajdeep.unidoc.core.model.PickedFile
import com.rajdeep.unidoc.core.model.ToolOutput
import java.io.File

/**
 * Per-job scratch space under cache/exports/conv-<id>/ (shared with the app FileProvider):
 * inputs are copied in (random access for PDF parsing), outputs written to out/.
 */
class Workspace(private val context: Context, val id: String) {
    val root: File = File(context.cacheDir, "exports/conv-$id").apply { mkdirs() }
    val inDir: File = File(root, "in").apply { mkdirs() }
    val outDir: File = File(root, "out").apply { mkdirs() }

    fun copyIn(file: PickedFile, index: Int): File {
        val safe = sanitize(file.name).ifEmpty { "input" }
        val dest = File(inDir, "${index}_$safe")
        val uri = Uri.parse(file.uri)
        if (uri.scheme == "file") {
            File(uri.path!!).copyTo(dest, overwrite = true)
        } else {
            context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                ?: error("Can't read ${file.name}")
        }
        return dest
    }

    fun out(name: String): File = File(outDir, sanitize(name))

    fun toOutputs(files: List<File>): List<ToolOutput> = files.map { f ->
        ToolOutput(
            uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", f).toString(),
            name = f.name,
            sizeBytes = f.length(),
            mimeType = mimeOf(f)
        )
    }

    companion object {
        fun sanitize(name: String) = name.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ").trim().take(120)

        fun baseName(name: String) = name.substringBeforeLast('.').ifBlank { name }

        fun mimeOf(f: File): String = when (f.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "application/octet-stream"
        }

        fun displayName(context: Context, uri: Uri): String? = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }
}
