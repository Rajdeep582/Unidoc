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
import com.github.takahirom.roborazzi.captureRoboImage
import com.rajdeep.unidoc.core.model.PickedFile
import com.rajdeep.unidoc.core.model.ToolId
import com.rajdeep.unidoc.core.model.UserSettings
import com.rajdeep.unidoc.core.ui.brand.BrandBackdrop
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.feature.converter.hub.ConverterHubScreen
import com.rajdeep.unidoc.feature.converter.tools.ToolOptions
import com.rajdeep.unidoc.feature.converter.tools.ToolScreen
import com.rajdeep.unidoc.feature.converter.tools.ToolUiState
import com.rajdeep.unidoc.feature.settings.SettingsScreen
import com.rajdeep.unidoc.gallery.GalleryItem
import com.rajdeep.unidoc.gallery.GalleryItemScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every screen on a narrow phone (360 dp) with 130 % system font: where wrapping, overflow and
 * alignment problems show. Images go to screenshots/stress/ for review.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi", sdk = [35])
class StressScreenshotTest(private val screen: String) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params(): List<Array<Any>> = (
            GalleryItem.entries.map { it.name } + listOf("HUB", "SETTINGS") +
                ToolId.entries.filter { it != ToolId.SCAN_TO_PDF }.map { "TOOL_${it.name}" }
            )
            .map { arrayOf<Any>(it) }
    }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun capture() {
        RuntimeEnvironment.setFontScale(1.3f)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            UnidocTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when {
                        screen == "HUB" -> {
                            BrandBackdrop(animated = false)
                            ConverterHubScreen(PaddingValues(bottom = 24.dp), {}, {})
                        }
                        screen == "SETTINGS" -> SettingsScreen(UserSettings.Default, {}, "0.2.0", {}, {})
                        screen.startsWith("TOOL_") && runCatching { ToolId.valueOf(screen.removePrefix("TOOL_")) }.isSuccess -> {
                            val tool = ToolId.valueOf(screen.removePrefix("TOOL_"))
                            val files = if (tool.minFiles == 0) {
                                emptyList()
                            } else {
                                List(tool.minFiles.coerceAtLeast(1)) { i ->
                                    PickedFile(
                                        "content://x/$i",
                                        "Quarterly financial statement 2026 final v$i.pdf",
                                        2_480_000L + i,
                                        "application/pdf"
                                    )
                                }
                            }
                            ToolScreen(ToolUiState(tool, files = files, options = ToolOptions.defaults(tool)), {}, {}, {})
                        }
                        else -> GalleryItemScreen(GalleryItem.valueOf(screen)) {}
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(1_200)
        composeRule.onRoot().captureRoboImage("src/test/screenshots/stress/${screen.lowercase()}.png")
    }
}
