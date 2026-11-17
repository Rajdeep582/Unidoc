package com.rajdeep.unidoc.feature.converter.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.rajdeep.unidoc.core.model.PickedFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Resolves display name/size for picked content URIs and keeps read access across restarts. */
class FileInfoReader @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun read(uri: Uri): PickedFile {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        var name = uri.lastPathSegment ?: "file"
        var size = -1L
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf {
                    it >= 0
                }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
        return PickedFile(uri.toString(), name, size, context.contentResolver.getType(uri))
    }
}
