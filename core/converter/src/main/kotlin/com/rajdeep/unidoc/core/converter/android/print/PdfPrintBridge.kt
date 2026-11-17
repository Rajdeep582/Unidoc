// Lives in android.print because LayoutResultCallback/WriteResultCallback constructors are not
// public in the SDK stubs (they are public at runtime); this is the standard way to drive a
// PrintDocumentAdapter to a file without the system print UI.
package android.print

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object PdfPrintBridge {
    suspend fun write(adapter: PrintDocumentAdapter, attributes: PrintAttributes, out: File) {
        val cancel = CancellationSignal()
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { cancel.cancel() }
            adapter.onStart()
            adapter.onLayout(
                null,
                attributes,
                cancel,
                object : PrintDocumentAdapter.LayoutResultCallback() {
                    override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
                        val fd = ParcelFileDescriptor.open(
                            out,
                            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE
                        )
                        adapter.onWrite(
                            arrayOf(PageRange.ALL_PAGES),
                            fd,
                            cancel,
                            object : PrintDocumentAdapter.WriteResultCallback() {
                                override fun onWriteFinished(pages: Array<out PageRange>) {
                                    runCatching { fd.close() }
                                    adapter.onFinish()
                                    if (cont.isActive) cont.resume(Unit)
                                }

                                override fun onWriteFailed(error: CharSequence?) {
                                    runCatching { fd.close() }
                                    adapter.onFinish()
                                    if (cont.isActive) {
                                        cont.resumeWithException(
                                            IllegalStateException(error?.toString() ?: "Printing failed")
                                        )
                                    }
                                }

                                override fun onWriteCancelled() {
                                    runCatching { fd.close() }
                                    adapter.onFinish()
                                }
                            }
                        )
                    }

                    override fun onLayoutFailed(error: CharSequence?) {
                        adapter.onFinish()
                        if (cont.isActive) cont.resumeWithException(IllegalStateException(error?.toString() ?: "Layout failed"))
                    }
                },
                null
            )
        }
    }
}
