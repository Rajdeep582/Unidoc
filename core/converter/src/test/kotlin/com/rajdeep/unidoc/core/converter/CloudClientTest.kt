package com.rajdeep.unidoc.core.converter

import com.rajdeep.unidoc.core.converter.cloud.CloudClient
import com.rajdeep.unidoc.core.converter.cloud.CloudRejectedException
import com.rajdeep.unidoc.core.converter.cloud.CloudUnavailableException
import com.rajdeep.unidoc.core.datastore.SettingsRepository
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CloudClientTest {
    @get:Rule
    val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private val unusedSettings = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SettingsRepository::class.java)) { _, _, _ ->
        error("not used")
    } as SettingsRepository
    private val client = CloudClient(unusedSettings)
    private lateinit var config: CloudClient.Config

    @Before
    fun start() {
        server.start()
        config = CloudClient.Config(server.url("/").toString().trimEnd('/'), "k3y")
    }

    @After
    fun stop() = server.close()

    @Test
    fun convertUploadsMultipartAndSavesNamedResult() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(
                200
            ).addHeader("X-Unidoc-Filename", "report.docx").body(Buffer().write(byteArrayOf(80, 75, 3, 4))).build()
        )
        val input = tmp.newFile("report.pdf").apply { writeText("%PDF-1.4") }
        val out = client.convert(config, "pdf2docx", listOf(input), mapOf("ocr" to "true"), File(tmp.root, "x.docx"))
        assertEquals("report.docx", out.name)
        assertEquals(4L, out.length())
        val req = server.takeRequest()
        assertEquals("/v1/convert/pdf2docx", req.url.encodedPath)
        assertEquals("k3y", req.headers["X-Unidoc-Key"])
        val body = req.body!!.utf8()
        assertTrue(body.contains("name=\"files\"; filename=\"report.pdf\""))
        assertTrue(body.contains("{\"ocr\":\"true\"}"))
    }

    @Test
    fun errorsAreClassified() {
        server.enqueue(MockResponse.Builder().code(401).build())
        server.enqueue(MockResponse.Builder().code(422).body("{\"detail\":\"Not a PDF file\"}").build())
        server.enqueue(MockResponse.Builder().code(503).build())
        val input = tmp.newFile("a.pdf")
        val out = File(tmp.root, "o.pdf")
        assertThrows(CloudRejectedException::class.java) { runBlocking { client.convert(config, "ocr", listOf(input), emptyMap(), out) } }
        val e =
            assertThrows(CloudRejectedException::class.java) {
                runBlocking { client.convert(config, "ocr", listOf(input), emptyMap(), out) }
            }
        assertTrue(e.message!!.contains("Not a PDF"))
        assertThrows(CloudUnavailableException::class.java) {
            runBlocking { client.convert(config, "ocr", listOf(input), emptyMap(), out) }
        }
    }

    @Test
    fun healthParses() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(200).body("{\"status\":\"ok\",\"version\":\"1.0.0\",\"features\":[\"office\",\"ocr\"]}").build()
        )
        val h = client.health(config)
        assertEquals("1.0.0", h.version)
        assertEquals(listOf("office", "ocr"), h.features)
        val e = assertThrows(IllegalArgumentException::class.java) { runBlocking { client.check("http://insecure", "") } }
        assertTrue(e.message!!.contains("https"))
    }
}
