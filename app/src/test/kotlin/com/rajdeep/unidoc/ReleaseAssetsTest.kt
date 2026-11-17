package com.rajdeep.unidoc

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.rajdeep.unidoc.feature.settings.legal.LegalDocument
import com.rajdeep.unidoc.feature.settings.legal.LegalDocuments
import com.rajdeep.unidoc.feature.settings.legal.LegalInfo
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Generates the publishing assets from the app's own sources:
 *  - docs/legal/privacy-policy.html, terms-of-use.html, index.html (host these; stores need the URL)
 *  - docs/store/icon-512.png (store icon) and feature-graphic-1024x500.png
 * Run: ./gradlew :app:testDebugUnitTest --tests "*ReleaseAssetsTest*"
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReleaseAssetsTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    @Test
    fun legalPages() {
        val dir = File(root, "docs/legal").apply { mkdirs() }
        File(dir, "privacy-policy.html").writeText(html(LegalDocuments.privacyPolicy))
        File(dir, "terms-of-use.html").writeText(html(LegalDocuments.termsOfUse))
        File(dir, "index.html").writeText(
            page(
                "${LegalInfo.APP_NAME} — Legal",
                """
                <h1>${LegalInfo.APP_NAME}</h1>
                <p class="lead">Document scanner and file converter for Android. Your scans stay on your phone.</p>
                <ul>
                  <li><a href="privacy-policy.html">Privacy Policy</a></li>
                  <li><a href="terms-of-use.html">Terms of Use</a></li>
                </ul>
                <p>Support: <a href="mailto:${LegalInfo.SUPPORT_EMAIL}">${LegalInfo.SUPPORT_EMAIL}</a></p>
                """.trimIndent()
            )
        )
    }

    @Test
    fun storeGraphics() {
        val dir = File(root, "docs/store").apply { mkdirs() }
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        fun icon(size: Int): Bitmap {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            // Adaptive icon layers are 108dp with the visible 72dp in the middle: draw at 1.5× and centre.
            val layer = (size * 1.5f).toInt()
            val off = (size - layer) / 2
            listOf(R.drawable.ic_launcher_background, R.drawable.ic_launcher_foreground).forEach { id ->
                ContextCompat.getDrawable(ctx, id)!!.apply { setBounds(off, off, off + layer, off + layer) }.draw(c)
            }
            return bmp
        }
        File(dir, "icon-512.png").outputStream().use { icon(512).compress(Bitmap.CompressFormat.PNG, 100, it) }

        val w = 1024
        val h = 500
        val fg = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(fg)
        c.drawRect(
            0f,
            0f,
            w.toFloat(),
            h.toFloat(),
            Paint().apply {
                shader =
                    LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), 0xFF1C2416.toInt(), 0xFF3E5230.toInt(), Shader.TileMode.CLAMP)
            }
        )
        c.drawCircle(
            860f,
            60f,
            420f,
            Paint().apply {
                shader =
                    RadialGradient(860f, 60f, 420f, 0x55A7BF7E, 0x00A7BF7E, Shader.TileMode.CLAMP)
            }
        )
        val ic = icon(300)
        val clip = Path().apply { addRoundRect(70f, 100f, 370f, 400f, 72f, 72f, Path.Direction.CW) }
        c.save()
        c.clipPath(clip)
        c.drawBitmap(ic, 70f, 100f, null)
        c.restore()
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFF4F6EE.toInt()
            textSize = 92f
            typeface =
                Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFCBD5BA.toInt()
            textSize = 36f
            typeface = Typeface.SANS_SERIF
        }
        c.drawText(LegalInfo.APP_NAME, 430f, 235f, title)
        c.drawText("Scan · Convert · Private", 434f, 300f, sub)
        c.drawText("Works offline — your files stay on your phone", 434f, 350f, sub.apply { textSize = 28f })
        File(dir, "feature-graphic-1024x500.png").outputStream().use { fg.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun html(doc: LegalDocument): String = page(
        "${LegalInfo.APP_NAME} — ${doc.title}",
        buildString {
            append("<h1>${esc(doc.title)}</h1>\n<p class=\"meta\">${LegalInfo.APP_NAME} · Effective ${LegalInfo.EFFECTIVE_DATE}</p>\n")
            append("<p class=\"lead\">${link(esc(doc.intro))}</p>\n")
            doc.sections.forEach { s ->
                append("<h2>${esc(s.heading)}</h2>\n")
                s.paragraphs.forEach { append("<p>${link(esc(it))}</p>\n") }
            }
            append("<p class=\"meta\"><a href=\"index.html\">All legal documents</a></p>")
        }
    )

    private fun page(title: String, body: String) = """
        <!DOCTYPE html>
        <html lang="en"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>$title</title>
        <style>
          body{font:16px/1.65 system-ui,-apple-system,Segoe UI,Roboto,sans-serif;max-width:760px;margin:0 auto;padding:32px 20px;color:#1f2a17;background:#fbfcf7}
          h1{font-size:2rem;margin:0 0 4px}h2{font-size:1.15rem;margin:28px 0 6px;color:#3e5230}
          .meta{color:#6b7560;font-size:.9rem}.lead{font-size:1.05rem}a{color:#4f6a35}
          @media (prefers-color-scheme:dark){body{background:#141a10;color:#e6ecdc}h2{color:#a7bf7e}a{color:#b9d08f}.meta{color:#9aa58d}}
        </style></head><body>
        $body
        </body></html>
    """.trimIndent()

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun link(s: String) = s
        .replace(Regex("""https://[^\s,;)]+[^\s,;.)]""")) { "<a href=\"${it.value}\">${it.value}</a>" }
        .replace(LegalInfo.SUPPORT_EMAIL, "<a href=\"mailto:${LegalInfo.SUPPORT_EMAIL}\">${LegalInfo.SUPPORT_EMAIL}</a>")
}
