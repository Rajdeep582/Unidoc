package com.rajdeep.unidoc.core.converter.cloud

import com.rajdeep.unidoc.core.datastore.SettingsRepository
import com.rajdeep.unidoc.core.model.CloudProbe
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response

@Serializable
data class CloudHealth(val status: String, val version: String = "", val features: List<String> = emptyList())

class CloudUnavailableException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Raised when the server answered but refused the job (bad input, unsupported) — no point falling back. */
class CloudRejectedException(message: String) : IOException(message)

/**
 * Client for the self-hosted Unidoc Cloud (Hugging Face Space). One synchronous endpoint per tool:
 * `POST /v1/convert/{tool}` multipart `files` + `options` (JSON) → the result file (zip when many).
 * Free Spaces sleep after 48 h idle; [wake] polls /health until the container is up.
 */
@Singleton
class CloudClient @Inject constructor(private val settings: SettingsRepository) : CloudProbe {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.MINUTES)
        .writeTimeout(3, TimeUnit.MINUTES)
        .callTimeout(10, TimeUnit.MINUTES)
        .build()

    data class Config(val baseUrl: String, val apiKey: String)

    suspend fun config(): Config? {
        val s = settings.settings.first()
        return if (s.cloudEnabled && s.cloudServerUrl.startsWith("https://")) Config(s.cloudServerUrl.trimEnd('/'), s.cloudApiKey) else null
    }

    suspend fun health(config: Config): CloudHealth = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("${config.baseUrl}/health").header(KEY_HEADER, config.apiKey).get().build()
        http.newCall(req).await().use { res ->
            if (res.code == 401) throw CloudRejectedException("Cloud key rejected — check Settings › Unidoc Cloud")
            if (!res.isSuccessful) throw CloudUnavailableException("Server answered ${res.code}")
            json.decodeFromString(CloudHealth.serializer(), res.body.string())
        }
    }

    override suspend fun check(url: String, key: String): String {
        val base = url.trim().trimEnd('/')
        require(base.startsWith("https://")) { "Address must start with https://" }
        val h = try {
            wake(Config(base, key.trim())) {}
        } catch (e: CloudRejectedException) {
            throw IllegalStateException("Key rejected — copy UNIDOC_API_KEY from your Space secrets")
        } catch (e: CloudUnavailableException) {
            throw IllegalStateException("No answer from the server — check the address and that the Space is Running")
        }
        return "Connected · v${h.version} · ${h.features.size} tools"
    }

    /** Waits up to ~2.5 min for a sleeping Space to start. [onWaiting] gets elapsed seconds. */
    suspend fun wake(config: Config, onWaiting: (Int) -> Unit): CloudHealth {
        val start = System.currentTimeMillis()
        var last: Throwable? = null
        while (System.currentTimeMillis() - start < WAKE_TIMEOUT_MS) {
            try {
                return health(config)
            } catch (e: CloudRejectedException) {
                throw e
            } catch (e: IOException) {
                last = e
                onWaiting(((System.currentTimeMillis() - start) / 1000).toInt())
                delay(5_000)
            }
        }
        throw CloudUnavailableException("Unidoc Cloud didn't respond", last)
    }

    suspend fun convert(config: Config, tool: String, files: List<File>, options: Map<String, String>, out: File): File =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                files.forEach { f -> addFormDataPart("files", f.name, f.asRequestBody("application/octet-stream".toMediaType())) }
                addFormDataPart("options", json.encodeToString(MapSerializer(String.serializer(), String.serializer()), options))
            }.build()
            val req = Request.Builder().url("${config.baseUrl}/v1/convert/$tool").header(KEY_HEADER, config.apiKey).post(body).build()
            val res = try {
                http.newCall(req).await()
            } catch (e: IOException) {
                throw CloudUnavailableException("Couldn't reach Unidoc Cloud", e)
            }
            res.use {
                when {
                    res.code == 401 -> throw CloudRejectedException("Cloud key rejected — check Settings › Unidoc Cloud")
                    res.code in 400..499 -> throw CloudRejectedException(
                        res.body.string().take(300).ifBlank {
                            "Cloud refused the file (${res.code})"
                        }
                    )
                    !res.isSuccessful -> throw CloudUnavailableException("Cloud error ${res.code}")
                }
                val name = res.header("X-Unidoc-Filename") ?: out.name
                val target = File(out.parentFile, name)
                target.outputStream().use { o -> res.body.byteStream().use { it.copyTo(o) } }
                target
            }
        }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) = cont.resumeWithException(e)

                override fun onResponse(call: Call, response: Response) = cont.resume(response)
            }
        )
    }

    companion object {
        const val KEY_HEADER = "X-Unidoc-Key"
        private const val WAKE_TIMEOUT_MS = 150_000L
    }
}
