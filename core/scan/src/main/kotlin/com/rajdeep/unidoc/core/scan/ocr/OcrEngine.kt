package com.rajdeep.unidoc.core.scan.ocr

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.rajdeep.unidoc.core.model.OcrEngine as Engine
import com.rajdeep.unidoc.core.model.OcrLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable

@Serializable
data class OcrLine(
    val text: String,
    /** Normalized box: left, top, right, bottom in 0..1. */
    val box: List<Float>,
    val confidence: Float? = null
)

@Serializable
data class OcrResult(val text: String, val lines: List<OcrLine>, val languages: List<String>, val imageWidth: Int, val imageHeight: Int)

/**
 * On-device OCR with bundled ML Kit models: Latin (English) and Devanagari (Hindi, Marathi,
 * Nepali, Sanskrit; also reads Latin). Cloud languages are handled by Unidoc Cloud (Phase 5).
 */
@Singleton
class OcrEngine @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val latin: TextRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val devanagari: TextRecognizer by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }

    suspend fun recognize(image: File, languages: Set<OcrLanguage>): OcrResult {
        val useDevanagari = languages.any { it.engine == Engine.ON_DEVICE && it != OcrLanguage.ENGLISH }
        val recognizer = if (useDevanagari) devanagari else latin
        val input = InputImage.fromFilePath(context, Uri.fromFile(image))
        val text = recognizer.process(input).await()
        val w = input.width.toFloat()
        val h = input.height.toFloat()
        val lines = text.textBlocks.flatMap { block ->
            block.lines.map { line ->
                val b = line.boundingBox
                OcrLine(
                    text = line.text,
                    box = if (b != null) listOf(b.left / w, b.top / h, b.right / w, b.bottom / h) else emptyList(),
                    confidence = line.confidence
                )
            }
        }
        return OcrResult(
            text = text.textBlocks.joinToString("\n\n") { it.text },
            lines = lines,
            languages = languages.filter { it.engine == Engine.ON_DEVICE }.map { it.tag },
            imageWidth = input.width,
            imageHeight = input.height
        )
    }
}

internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
