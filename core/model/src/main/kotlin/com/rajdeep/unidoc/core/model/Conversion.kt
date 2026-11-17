package com.rajdeep.unidoc.core.model

import kotlinx.coroutines.flow.Flow

/** A file chosen as converter input (content URI as string so it survives saved state). */
data class PickedFile(val uri: String, val name: String, val sizeBytes: Long, val mimeType: String?)

data class ToolRequest(val tool: ToolId, val inputs: List<PickedFile>, val options: Map<String, String>)

/** A produced file; [uri] is a shareable content:// URI. */
data class ToolOutput(val uri: String, val name: String, val sizeBytes: Long, val mimeType: String, val fileCount: Int = 1)

sealed interface ToolProgress {
    /** [fraction] null = indeterminate. [stage] is user-facing ("Uploading", "Converting page 3 of 12"). */
    data class Running(val fraction: Float?, val stage: String) : ToolProgress

    data class Completed(
        val outputs: List<ToolOutput>,
        val inputBytes: Long,
        /** Optional note, e.g. "Converted on-device (Cloud unreachable)". */
        val note: String? = null
    ) : ToolProgress

    data class Failed(val message: String, val retryable: Boolean = true) : ToolProgress
}

/** Executes a converter tool. Cancellation = cancel the collecting coroutine. */
fun interface ToolRunner {
    fun run(request: ToolRequest): Flow<ToolProgress>
}

/** Checks a Unidoc Cloud server; returns its version or throws with a user-facing message. */
fun interface CloudProbe {
    suspend fun check(url: String, key: String): String
}
