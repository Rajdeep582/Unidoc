package com.rajdeep.unidoc.core.converter.office

import android.annotation.SuppressLint
import android.content.Context
import android.print.PdfPrintBridge
import android.print.PrintAttributes
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** Renders HTML (string or URL) to a vector PDF using an off-screen WebView and the print pipeline. */
object HtmlPrinter {

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun print(
        context: Context,
        out: File,
        html: String? = null,
        url: String? = null,
        baseDir: File? = null,
        letter: Boolean = false,
        landscape: Boolean = false
    ) = withContext(Dispatchers.Main) {
        val web = WebView(context.applicationContext)
        try {
            web.settings.apply {
                javaScriptEnabled = url != null
                allowFileAccess = baseDir != null
                loadWithOverviewMode = true
                useWideViewPort = true
            }
            withTimeout(60_000) {
                suspendCancellableCoroutine { cont ->
                    web.webViewClient = object : WebViewClient() {
                        private var done = false

                        override fun onPageFinished(view: WebView, loadedUrl: String?) {
                            if (!done && view.progress >= 100) {
                                done = true
                                if (cont.isActive) cont.resume(Unit)
                            }
                        }

                        // Local documents never navigate away.
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = url == null
                    }
                    if (url != null) {
                        web.loadUrl(url)
                    } else {
                        web.loadDataWithBaseURL(baseDir?.toURI()?.toString(), html.orEmpty(), "text/html", "utf-8", null)
                    }
                }
            }
            // Give web fonts/images a beat to settle after onPageFinished.
            delay(if (url != null) 1200 else 250)
            val size = if (letter) PrintAttributes.MediaSize.NA_LETTER else PrintAttributes.MediaSize.ISO_A4
            val attrs = PrintAttributes.Builder()
                .setMediaSize(if (landscape) size.asLandscape() else size.asPortrait())
                .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
            PdfPrintBridge.write(web.createPrintDocumentAdapter(out.nameWithoutExtension), attrs, out)
        } finally {
            web.stopLoading()
            web.destroy()
        }
        require(out.length() > 0) { "The page produced an empty PDF" }
        out
    }
}
