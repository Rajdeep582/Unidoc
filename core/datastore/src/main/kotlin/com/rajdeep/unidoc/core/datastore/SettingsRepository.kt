package com.rajdeep.unidoc.core.datastore

import com.rajdeep.unidoc.core.model.OcrLanguage
import com.rajdeep.unidoc.core.model.QualityPreset
import com.rajdeep.unidoc.core.model.ThemeMode
import com.rajdeep.unidoc.core.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<UserSettings>

    suspend fun setThemeMode(themeMode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setHapticsEnabled(enabled: Boolean)

    suspend fun setDefaultQuality(quality: QualityPreset)

    suspend fun setAutoCapture(enabled: Boolean)

    /** English is always kept: it is the fallback for mixed-script pages. */
    suspend fun setOcrLanguages(languages: Set<OcrLanguage>)

    suspend fun setCloudEnabled(enabled: Boolean)

    suspend fun setCloudServerUrl(url: String)

    suspend fun setCloudApiKey(key: String)
}
