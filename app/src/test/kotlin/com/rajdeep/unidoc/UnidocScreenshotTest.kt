package com.rajdeep.unidoc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.rajdeep.unidoc.core.ui.brand.BrandLogo
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.ui.UnidocApp
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@HiltAndroidTest
@Config(application = HiltTestApplication::class, qualifiers = RobolectricDeviceQualifiers.Pixel7)
class UnidocScreenshotTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    private fun captureApp(dark: Boolean, name: String, openConverter: Boolean = false) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { UnidocTheme(darkTheme = dark) { UnidocApp() } }
        composeRule.mainClock.advanceTimeBy(1_500)
        if (openConverter) {
            composeRule.onNodeWithText("Converter").performClick()
            composeRule.mainClock.advanceTimeBy(1_500)
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test fun scannerLight() = captureApp(dark = false, name = "scanner_light")

    @Test fun scannerDark() = captureApp(dark = true, name = "scanner_dark")

    @Test fun converterLight() = captureApp(dark = false, name = "converter_light", openConverter = true)

    @Test fun converterDark() = captureApp(dark = true, name = "converter_dark", openConverter = true)

    @Test
    fun brandIcons() {
        composeRule.setContent {
            UnidocTheme {
                Row(
                    modifier = Modifier.background(Color(0xFF202124)).padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    LauncherIcon(CircleShape)
                    LauncherIcon(RoundedCornerShape(28.dp))
                    BrandLogo(size = 96.dp)
                    Image(painterResource(R.drawable.splash_mark), null, Modifier.size(96.dp))
                }
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/screenshots/brand_icons.png")
    }
}

@androidx.compose.runtime.Composable
private fun LauncherIcon(shape: Shape) {
    Box(Modifier.size(96.dp).clip(shape)) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.size(96.dp))
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(96.dp))
    }
}
