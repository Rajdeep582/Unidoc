package com.rajdeep.unidoc.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.rajdeep.unidoc.core.model.OcrLanguage
import com.rajdeep.unidoc.core.model.ThemeMode
import com.rajdeep.unidoc.core.model.UserSettings
import java.io.File
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreSettingsRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun repository(scope: TestScope): DataStoreSettingsRepository {
        val file = File(tmp.newFolder(), "settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { file }
        )
        return DataStoreSettingsRepository(dataStore)
    }

    @Test
    fun emitsDefaultsWhenEmpty() = runTest(UnconfinedTestDispatcher()) {
        repository(this).settings.test {
            assertEquals(UserSettings.Default, awaitItem())
        }
    }

    @Test
    fun persistsUpdates() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        repo.settings.test {
            assertEquals(UserSettings.Default, awaitItem())
            repo.setThemeMode(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem().themeMode)
            repo.setDynamicColor(true)
            assertEquals(true, awaitItem().dynamicColor)
            repo.setHapticsEnabled(false)
            assertEquals(
                UserSettings(themeMode = ThemeMode.DARK, dynamicColor = true, hapticsEnabled = false),
                awaitItem()
            )
        }
    }

    @Test
    fun ocrLanguagesAlwaysKeepEnglishAndUrlIsNormalized() = runTest(UnconfinedTestDispatcher()) {
        val repo = repository(this)
        repo.settings.test {
            awaitItem()
            repo.setOcrLanguages(setOf(OcrLanguage.BENGALI))
            assertEquals(setOf(OcrLanguage.ENGLISH, OcrLanguage.BENGALI), awaitItem().ocrLanguages)
            repo.setCloudServerUrl("  https://example.hf.space/  ")
            assertEquals("https://example.hf.space", awaitItem().cloudServerUrl)
        }
    }
}
