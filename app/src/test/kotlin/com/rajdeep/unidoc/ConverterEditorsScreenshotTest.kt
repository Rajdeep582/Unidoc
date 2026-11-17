package com.rajdeep.unidoc

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.rajdeep.unidoc.core.model.PickedFile
import com.rajdeep.unidoc.core.model.ToolId
import com.rajdeep.unidoc.core.scan.annotation.SavedSignature
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.feature.converter.tools.ToolOptions
import com.rajdeep.unidoc.feature.converter.tools.ToolScreen
import com.rajdeep.unidoc.feature.converter.tools.ToolUiState
import com.rajdeep.unidoc.feature.converter.tools.editor.ArrangePagesDialog
import com.rajdeep.unidoc.feature.converter.tools.editor.PageItem
import com.rajdeep.unidoc.feature.converter.tools.editor.Placement
import com.rajdeep.unidoc.feature.converter.tools.editor.PlacementDialog
import com.rajdeep.unidoc.feature.converter.tools.editor.Stamp
import com.rajdeep.unidoc.feature.converter.tools.preview.PdfInfo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel7, sdk = [35])
class ConverterEditorsScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val info = PdfInfo(6, List(6) { 595 to 842 })
    private val file = PickedFile("content://x/contract.pdf", "Rental agreement.pdf", 1_240_000, "application/pdf")
    private val signature = SavedSignature(
        listOf(
            listOf(0.05f, 0.7f, 0.15f, 0.2f, 0.25f, 0.75f, 0.35f, 0.3f, 0.45f, 0.65f),
            listOf(0.5f, 0.6f, 0.6f, 0.4f, 0.7f, 0.6f, 0.8f, 0.35f, 0.95f, 0.55f)
        ),
        3f
    )

    @Test
    fun signInput() {
        composeRule.setContent {
            UnidocTheme {
                ToolScreen(
                    state = ToolUiState(
                        ToolId.SIGN_PDF,
                        files = listOf(file),
                        options = ToolOptions.defaults(ToolId.SIGN_PDF) + ("place" to Placement(5, 0.6f, 0.8f, 0.3f).encode()),
                        pdfInfo = info,
                        signature = signature
                    ),
                    onAction = {},
                    onBack = {},
                    onPick = {}
                )
            }
        }
        captureScreenRoboImage("src/test/screenshots/tool_sign_input.png")
    }

    @Test
    fun organizeInput() {
        composeRule.setContent {
            UnidocTheme(darkTheme = true) {
                ToolScreen(
                    state = ToolUiState(
                        ToolId.ORGANIZE_PDF,
                        files = listOf(file),
                        options = mapOf("order" to "3, 1-2, 4-6", "rotate" to "3:90"),
                        pdfInfo = info
                    ),
                    onAction = {},
                    onBack = {},
                    onPick = {}
                )
            }
        }
        captureScreenRoboImage("src/test/screenshots/tool_organize_input_dark.png")
    }

    @Test
    fun arrangeDialog() {
        composeRule.setContent {
            UnidocTheme {
                ArrangePagesDialog(
                    file.uri,
                    info,
                    listOf(
                        PageItem(2, 90),
                        PageItem(0),
                        PageItem(1),
                        PageItem(3, 180),
                        PageItem(4),
                        PageItem(5)
                    ),
                    {
                    },
                    {}
                )
            }
        }
        captureScreenRoboImage("src/test/screenshots/tool_arrange_dialog.png")
    }

    @Test
    fun cropEightPoints() {
        composeRule.setContent {
            UnidocTheme {
                com.rajdeep.unidoc.feature.scanner.editor.CropScreen(
                    corners = com.rajdeep.unidoc.feature.scanner.editor.SampleCrop.corners,
                    onCornersChange = {},
                    onAutoDetect = {},
                    onNoCrop = {},
                    onDone = {},
                    onBack = {}
                )
            }
        }
        captureScreenRoboImage("src/test/screenshots/crop_eight_points.png")
    }

    @Test
    fun placementDialog() {
        composeRule.setContent {
            UnidocTheme {
                PlacementDialog(file.uri, info, Stamp.Signature(signature.strokes, signature.aspect), Placement(5, 0.55f, 0.78f, 0.32f), {
                }, {})
            }
        }
        captureScreenRoboImage("src/test/screenshots/tool_place_dialog.png")
    }
}
