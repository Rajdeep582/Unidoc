package com.rajdeep.unidoc.core.scan.annotation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** The user's hand-drawn signature: strokes normalized to a box (x,y pairs in 0..1) with its aspect (w/h). */
data class SavedSignature(val strokes: List<List<Float>>, val aspect: Float)

/**
 * Single saved signature shared by the scanner markup and the converter's Sign PDF tool.
 * Stored as "<aspect>\n<json strokes>" in files/signature.json (format read by core:converter too).
 */
@Singleton
class SignatureStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val file get() = File(context.filesDir, "signature.json")
    private val serializer = ListSerializer(ListSerializer(Float.serializer()))
    private val _signature = MutableStateFlow(read())
    val signature: StateFlow<SavedSignature?> = _signature.asStateFlow()

    fun current(): SavedSignature? = _signature.value

    fun save(strokes: List<List<Float>>, aspect: Float) {
        runCatching {
            val tmp = File(file.parentFile, "signature.json.tmp")
            tmp.writeText("$aspect\n" + Json.encodeToString(serializer, strokes))
            tmp.renameTo(file)
        }
        _signature.value = SavedSignature(strokes, aspect)
    }

    fun clear() {
        file.delete()
        _signature.value = null
    }

    private fun read(): SavedSignature? = runCatching {
        val (aspect, body) = file.readText().split("\n", limit = 2)
        SavedSignature(Json.decodeFromString(serializer, body), aspect.toFloat())
    }.getOrNull()?.takeIf { it.strokes.isNotEmpty() }
}
