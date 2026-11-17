package com.rajdeep.unidoc.core.model

/**
 * App-wide user preferences persisted on device.
 *
 * Dynamic color is off by default so the Unidoc brand palette is used unless the user opts in.
 * Cloud processing is off until the user consents once.
 */
data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val hapticsEnabled: Boolean = true,
    val defaultQuality: QualityPreset = QualityPreset.BALANCED,
    val autoCapture: Boolean = true,
    val ocrLanguages: Set<OcrLanguage> = setOf(OcrLanguage.ENGLISH),
    val cloudEnabled: Boolean = false,
    val cloudServerUrl: String = DEFAULT_CLOUD_URL,
    /** Shared secret sent as X-Unidoc-Key; set as UNIDOC_API_KEY on the server. */
    val cloudApiKey: String = ""
) {
    companion object {
        const val DEFAULT_CLOUD_URL = ""
        val Default = UserSettings()
    }
}
