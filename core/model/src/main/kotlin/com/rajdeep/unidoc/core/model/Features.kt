package com.rajdeep.unidoc.core.model

/** Release feature switches. */
object Features {
    /**
     * Unidoc Cloud (server-side conversions) is off in this release: every tool runs on the phone and no
     * file ever leaves the device. To enable later, deploy server/ (for example a free Hugging Face Space),
     * set this to true, ship the server address with the app and update the privacy policy.
     */
    const val CLOUD = false
}
