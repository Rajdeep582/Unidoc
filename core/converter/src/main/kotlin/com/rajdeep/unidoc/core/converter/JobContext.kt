package com.rajdeep.unidoc.core.converter

import android.content.Context
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Everything a tool implementation needs for one run. */
class JobContext(
    val android: Context,
    val workspace: Workspace,
    /** Local copies of the inputs, in user order. */
    val inputs: List<File>,
    /** Original display names (same order as [inputs]). */
    val names: List<String>,
    val options: Map<String, String>,
    private val report: (Float?, String) -> Unit
) {
    val firstBase: String get() = names.firstOrNull()?.let(Workspace::baseName) ?: "Unidoc"

    fun opt(key: String, default: String = ""): String = options[key]?.takeIf { it.isNotBlank() } ?: default

    fun flag(key: String, default: Boolean = false): Boolean = options[key]?.toBooleanStrictOrNull() ?: default

    fun number(key: String, default: Float): Float = options[key]?.toFloatOrNull() ?: default

    suspend fun progress(fraction: Float?, stage: String) {
        currentCoroutineContext().ensureActive()
        report(fraction, stage)
    }

    /** Visual placement "page,x,y,w" (fractions of the displayed page, top-left origin), if the user placed it. */
    val placement: FloatArray?
        get() = options["place"]?.split(',')?.mapNotNull { it.trim().toFloatOrNull() }?.takeIf { it.size == 4 }?.toFloatArray()

    /** Non-suspending progress update (for callbacks). */
    fun status(fraction: Float?, stage: String) = report(fraction, stage)

    fun out(name: String): File = workspace.out(name)
}
