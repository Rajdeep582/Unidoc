package com.rajdeep.unidoc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.rajdeep.unidoc.core.model.UserSettings
import com.rajdeep.unidoc.core.ui.brand.BrandBackdrop
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.feature.converter.hub.ConverterHubScreen
import com.rajdeep.unidoc.feature.settings.SettingsScreen
import com.rajdeep.unidoc.gallery.GalleryItem
import com.rajdeep.unidoc.gallery.GalleryItemScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7)
class GalleryScreenshotTest(private val screen: String, private val dark: Boolean) {

    companion object {
        private val darkToo = setOf("HOME_CONTENT", "EDITOR", "TOOL_MERGE", "HUB", "SETTINGS", "EXPORT")

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_dark={1}")
        fun params(): List<Array<Any>> {
            val names = GalleryItem.entries.map { it.name } + listOf("HUB", "SETTINGS")
            return names.map { arrayOf<Any>(it, false) } + names.filter { it in darkToo }.map { arrayOf<Any>(it, true) }
        }
    }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun capture() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            UnidocTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (screen) {
                        "HUB" -> {
                            BrandBackdrop(animated = false)
                            ConverterHubScreen(PaddingValues(bottom = 24.dp), {}, {})
                        }
                        "SETTINGS" -> SettingsScreen(UserSettings.Default, {}, "0.2.0", {}, {})
                        else -> GalleryItemScreen(GalleryItem.valueOf(screen)) {}
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_200)
        composeRule.onRoot().captureRoboImage(
            "src/test/screenshots/gallery/${screen.lowercase()}${if (dark) "_dark" else ""}.png"
        )
    }
}
