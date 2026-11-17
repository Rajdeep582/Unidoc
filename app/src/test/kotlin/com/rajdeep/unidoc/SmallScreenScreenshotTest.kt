package com.rajdeep.unidoc

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.ui.UnidocApp
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Narrow phone (360dp) with large system font — where text wrapping bugs show up. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@HiltAndroidTest
@Config(application = HiltTestApplication::class, qualifiers = "w360dp-h780dp-xxhdpi", sdk = [35])
class SmallScreenScreenshotTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private fun capture(name: String, fontScale: Float, converter: Boolean) {
        RuntimeEnvironment.setFontScale(fontScale)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { UnidocTheme { UnidocApp() } }
        composeRule.mainClock.advanceTimeBy(1_500)
        if (converter) {
            composeRule.onNodeWithText("Convert", substring = true).performClick()
            composeRule.mainClock.advanceTimeBy(1_500)
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/small_$name.png")
    }

    @Test fun scannerFont100() = capture("scanner_100", 1.0f, false)

    @Test fun scannerFont130() = capture("scanner_130", 1.3f, false)

    @Test fun converterFont100() = capture("converter_100", 1.0f, true)

    @Test fun converterFont130() = capture("converter_130", 1.3f, true)
}
