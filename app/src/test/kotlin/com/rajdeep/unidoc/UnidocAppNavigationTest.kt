package com.rajdeep.unidoc

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.ui.UnidocApp
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@HiltAndroidTest
@Config(application = HiltTestApplication::class)
@RunWith(AndroidJUnit4::class)
class UnidocAppNavigationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Test
    fun startsOnScannerAndSwitchesTabs() {
        composeRule.setContent { UnidocTheme { UnidocApp() } }

        composeRule.onNodeWithText("Documents").assertIsDisplayed()
        composeRule.onNode(hasText("Scanner")).assertIsSelected()
        composeRule.onNode(hasText("Converter")).assertIsNotSelected()

        composeRule.onNodeWithText("Converter").performClick()
        composeRule.onNodeWithText("Uni Converter").assertIsDisplayed()
        composeRule.onNode(hasText("Converter")).assertIsSelected()

        composeRule.onNodeWithText("Scanner").performClick()
        composeRule.onNodeWithText("Documents").assertIsDisplayed()
        composeRule.onNode(hasText("Scanner")).assertIsSelected()
    }
}
