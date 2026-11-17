package com.rajdeep.unidoc.feature.scanner.capture

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajdeep.unidoc.core.scan.ScanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Imports photos or files (images / one PDF) into a new scan session. Activity-scoped. */
@HiltViewModel
class ImportViewModel @Inject constructor(@param:ApplicationContext private val context: Context, private val repository: ScanRepository) :
    ViewModel() {
    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _results = Channel<Result<String>>(Channel.BUFFERED)
    val results = _results.receiveAsFlow()

    /** Appends picked images to an existing document. */
    fun addTo(sessionId: String, uris: List<Uri>) {
        val images = uris.filter { context.contentResolver.getType(it)?.startsWith("image/") == true }
        if (images.isEmpty() || _busy.value != null) return
        _busy.value = "Adding ${images.size} ${if (images.size == 1) "page" else "pages"}…"
        viewModelScope.launch {
            val result = runCatching { repository.addImages(sessionId, images) }
            _busy.value = null
            result.onFailure { _results.send(Result.failure(it)) }
        }
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty() || _busy.value != null) return
        val pdf = uris.firstOrNull { context.contentResolver.getType(it) == "application/pdf" }
        val images = uris.filter { context.contentResolver.getType(it)?.startsWith("image/") == true }
        _busy.value = if (pdf != null) "Opening PDF…" else "Importing ${images.size} ${if (images.size == 1) "image" else "images"}…"
        viewModelScope.launch {
            val result = runCatching {
                when {
                    pdf != null -> repository.importPdf(pdf)
                    images.isNotEmpty() -> repository.importImages(images)
                    else -> error("Unsupported file type")
                }
            }
            _busy.value = null
            _results.send(result)
        }
    }
}
