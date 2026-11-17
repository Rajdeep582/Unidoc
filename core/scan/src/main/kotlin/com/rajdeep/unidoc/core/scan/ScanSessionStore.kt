package com.rajdeep.unidoc.core.scan

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * Durable scan sessions: every capture and edit is written to disk immediately, so a session
 * survives process death and can be resumed by id.
 * Layout: files/scan_sessions/<id>/{session.json, o_*.jpg, p_*.jpg|png, t_*.jpg, ocr_*.json}
 */
@Singleton
class ScanSessionStore @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val root: File get() = File(context.filesDir, "scan_sessions").apply { mkdirs() }
    private val flows = HashMap<String, MutableStateFlow<ScanSession?>>()

    fun dir(sessionId: String): File = File(root, sessionId).apply { mkdirs() }

    fun file(sessionId: String, name: String): File = File(dir(sessionId), name)

    @Synchronized
    fun observe(sessionId: String): StateFlow<ScanSession?> = flows.getOrPut(sessionId) { MutableStateFlow(read(sessionId)) }.asStateFlow()

    @Synchronized
    fun create(session: ScanSession): ScanSession {
        write(session)
        return session
    }

    @Synchronized
    fun get(sessionId: String): ScanSession? = flows[sessionId]?.value ?: read(sessionId)

    @Synchronized
    fun update(sessionId: String, transform: (ScanSession) -> ScanSession): ScanSession {
        val current = get(sessionId) ?: error("Session $sessionId not found")
        val next = transform(current)
        write(next)
        return next
    }

    @Synchronized
    fun delete(sessionId: String) {
        File(root, sessionId).deleteRecursively()
        flows.remove(sessionId)?.value = null
    }

    /** Removes files no longer referenced by the session (old processed versions etc.). */
    @Synchronized
    fun prune(sessionId: String) {
        val session = get(sessionId) ?: return
        val keep = buildSet {
            add(SESSION_FILE)
            session.pages.forEach { p -> listOfNotNull(p.original, p.backOriginal, p.processed, p.thumbnail, p.ocr).forEach(::add) }
        }
        // Originals (o_*) are kept so deleted pages can be restored by undo; they go with the session.
        dir(sessionId).listFiles()
            ?.filter { it.name !in keep && (it.name.startsWith("p_") || it.name.startsWith("t_") || it.name.startsWith("ocr_")) }
            ?.forEach { it.delete() }
    }

    fun newId(): String = UUID.randomUUID().toString().take(12)

    private fun read(sessionId: String): ScanSession? {
        val f = File(File(root, sessionId), SESSION_FILE)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString(ScanSession.serializer(), f.readText()) }.getOrNull()
    }

    private fun write(session: ScanSession) {
        val dir = dir(session.id)
        val tmp = File(dir, "$SESSION_FILE.tmp")
        tmp.writeText(json.encodeToString(ScanSession.serializer(), session))
        check(tmp.renameTo(File(dir, SESSION_FILE))) { "Could not save session ${session.id}" }
        flows.getOrPut(session.id) { MutableStateFlow(null) }.value = session
    }

    private companion object {
        const val SESSION_FILE = "session.json"
    }
}
