package com.rajdeep.unidoc.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.rajdeep.unidoc.core.model.OcrLanguage
import com.rajdeep.unidoc.core.model.QualityPreset
import com.rajdeep.unidoc.core.model.ThemeMode
import com.rajdeep.unidoc.core.model.UserSettings
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DataStoreSettingsRepository @Inject constructor(private val dataStore: DataStore<Preferences>) : SettingsRepository {

    override val settings: Flow<UserSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::toSettings)
        .distinctUntilChanged()

    override suspend fun setThemeMode(themeMode: ThemeMode) = put(Keys.THEME_MODE, themeMode.name)

    override suspend fun setDynamicColor(enabled: Boolean) = put(Keys.DYNAMIC_COLOR, enabled)

    override suspend fun setHapticsEnabled(enabled: Boolean) = put(Keys.HAPTICS, enabled)

    override suspend fun setDefaultQuality(quality: QualityPreset) = put(Keys.QUALITY, quality.name)

    override suspend fun setAutoCapture(enabled: Boolean) = put(Keys.AUTO_CAPTURE, enabled)

    override suspend fun setOcrLanguages(languages: Set<OcrLanguage>) =
        put(Keys.OCR_LANGUAGES, (languages + OcrLanguage.ENGLISH).map { it.name }.toSet())

    override suspend fun setCloudEnabled(enabled: Boolean) = put(Keys.CLOUD_ENABLED, enabled)

    override suspend fun setCloudServerUrl(url: String) = put(Keys.CLOUD_URL, url.trim().trimEnd('/'))

    override suspend fun setCloudApiKey(key: String) = put(Keys.CLOUD_KEY, key.trim())

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private fun toSettings(prefs: Preferences): UserSettings {
        val d = UserSettings.Default
        return UserSettings(
            themeMode = prefs[Keys.THEME_MODE].toEnum(d.themeMode),
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: d.dynamicColor,
            hapticsEnabled = prefs[Keys.HAPTICS] ?: d.hapticsEnabled,
            defaultQuality = prefs[Keys.QUALITY].toEnum(d.defaultQuality),
            autoCapture = prefs[Keys.AUTO_CAPTURE] ?: d.autoCapture,
            ocrLanguages = prefs[Keys.OCR_LANGUAGES]
                ?.mapNotNull { name -> OcrLanguage.entries.firstOrNull { it.name == name } }
                ?.filter { com.rajdeep.unidoc.core.model.Features.CLOUD || it.engine != com.rajdeep.unidoc.core.model.OcrEngine.CLOUD }
                ?.toSet()
                ?.plus(OcrLanguage.ENGLISH)
                ?: d.ocrLanguages,
            cloudEnabled = prefs[Keys.CLOUD_ENABLED] ?: d.cloudEnabled,
            cloudServerUrl = prefs[Keys.CLOUD_URL] ?: d.cloudServerUrl,
            cloudApiKey = prefs[Keys.CLOUD_KEY] ?: d.cloudApiKey
        )
    }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { stored -> enumValues<E>().firstOrNull { it.name == stored } } ?: default

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val QUALITY = stringPreferencesKey("default_quality")
        val AUTO_CAPTURE = booleanPreferencesKey("auto_capture")
        val OCR_LANGUAGES = stringSetPreferencesKey("ocr_languages")
        val CLOUD_ENABLED = booleanPreferencesKey("cloud_enabled")
        val CLOUD_URL = stringPreferencesKey("cloud_server_url")
        val CLOUD_KEY = stringPreferencesKey("cloud_api_key")
    }
}
