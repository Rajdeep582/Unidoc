package com.rajdeep.unidoc.core.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Sharing/opening exported files through the app's FileProvider (authority `<package>.exports`). */
object ExportFiles {
    fun uriFor(context: Context, file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.exports", file)

    fun mime(file: File): String = when (file.extension.lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"
        "txt" -> "text/plain"
        else -> "image/jpeg"
    }

    fun share(context: Context, files: List<File>) {
        if (files.isEmpty()) return
        val uris = ArrayList(files.map { uriFor(context, it) })
        val type = files.map(::mime).distinct().singleOrNull() ?: "*/*"
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType(type).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        context.startActivity(
            Intent.createChooser(intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun open(context: Context, file: File) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uriFor(context, file), mime(file))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure { Toast.makeText(context, "No app can open this file", Toast.LENGTH_SHORT).show() }
    }
}
