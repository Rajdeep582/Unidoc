package com.rajdeep.unidoc.core.scan

import org.opencv.android.OpenCVLoader

/** Loads the bundled OpenCV native library once per process. */
object OpenCv {
    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (!loaded) {
                check(OpenCVLoader.initLocal()) { "OpenCV native library failed to load" }
                loaded = true
            }
        }
    }
}
