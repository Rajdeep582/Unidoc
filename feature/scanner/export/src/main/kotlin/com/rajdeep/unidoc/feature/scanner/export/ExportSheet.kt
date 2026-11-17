package com.rajdeep.unidoc.feature.scanner.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TextSnippet
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajdeep.unidoc.core.model.ExportFormat
import com.rajdeep.unidoc.core.model.ExportOptions
import com.rajdeep.unidoc.core.model.PageMargin
import com.rajdeep.unidoc.core.model.PageOrientation
import com.rajdeep.unidoc.core.model.PageSize
import com.rajdeep.unidoc.core.model.QualityPreset
import com.rajdeep.unidoc.core.ui.components.GradientButton
import com.rajdeep.unidoc.core.ui.components.SegmentedControl
import com.rajdeep.unidoc.core.ui.components.SwitchRow
import com.rajdeep.unidoc.core.ui.components.UnidocBottomSheet
import com.rajdeep.unidoc.core.ui.components.UnidocCard
import com.rajdeep.unidoc.core.ui.components.UnidocChip
import com.rajdeep.unidoc.core.ui.format.formatFileSize
import com.rajdeep.unidoc.core.ui.theme.Dimens

data class ExportUiState(
    val fileName: String,
    val options: ExportOptions = ExportOptions(),
    /** Estimated output size per preset; null while computing. */
    val estimates: Map<QualityPreset, Long?> = emptyMap(),
    val originalBytes: Long = 0
)

@Composable
fun ExportSheet(
    state: ExportUiState,
    onFileNameChange: (String) -> Unit,
    onOptionsChange: (ExportOptions) -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit
) {
    UnidocBottomSheet(onDismissRequest = onDismiss) {
        ExportOptionsContent(state, onFileNameChange, onOptionsChange, onExport)
    }
}

@Composable
fun ExportOptionsContent(
    state: ExportUiState,
    onFileNameChange: (String) -> Unit,
    onOptionsChange: (ExportOptions) -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val o = state.options
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Dimens.Gutter),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)
    ) {
        Text("Export", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.fileName,
            onValueChange = onFileNameChange,
            label = { Text("File name") },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
        SegmentedControl(
            options = listOf("PDF", "JPG", "PNG"),
            selectedIndex = o.format.ordinal,
            onSelect = { onOptionsChange(o.copy(format = ExportFormat.entries[it])) }
        )
        Label("Quality")
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            QualityPreset.entries.forEach { preset ->
                QualityCard(
                    preset = preset,
                    estimate = state.estimates[preset],
                    selected = o.quality == preset,
                    onClick = { onOptionsChange(o.copy(quality = preset)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        AnimatedVisibility(
            o.format == ExportFormat.PDF,
            enter = fadeIn() + expandVertically(),
            exit =
            fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceL)) {
                Label("Page size")
                ChipRow(PageSize.entries, o.pageSize, { it.label() }) { onOptionsChange(o.copy(pageSize = it)) }
                Label("Orientation")
                ChipRow(PageOrientation.entries, o.orientation, {
                    it.name.lowercase().replaceFirstChar(Char::uppercase)
                }) {
                    onOptionsChange(o.copy(orientation = it))
                }
                Label("Margins")
                ChipRow(PageMargin.entries, o.margin, { it.name.lowercase().replaceFirstChar(Char::uppercase) }) {
                    onOptionsChange(o.copy(margin = it))
                }
                UnidocCard {
                    Column {
                        SwitchRow(
                            title = "Searchable text",
                            subtitle = "Adds an invisible OCR layer",
                            icon = Icons.AutoMirrored.Rounded.TextSnippet,
                            checked = o.searchable,
                            onCheckedChange = { onOptionsChange(o.copy(searchable = it)) }
                        )
                        SwitchRow(
                            title = "Password protect",
                            subtitle = "AES-256 encryption",
                            icon = Icons.Rounded.Lock,
                            checked = o.password != null,
                            onCheckedChange = { onOptionsChange(o.copy(password = if (it) "" else null)) }
                        )
                    }
                }
                AnimatedVisibility(o.password != null) {
                    OutlinedTextField(
                        value = o.password.orEmpty(),
                        onValueChange = { onOptionsChange(o.copy(password = it)) },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        val size = state.estimates[o.quality]
        GradientButton(
            text = if (size != null) "Export · ${formatFileSize(size)}" else "Export",
            icon = Icons.Rounded.Download,
            onClick = onExport,
            enabled = state.fileName.isNotBlank() && (o.password?.let { it.length >= 4 } ?: true),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Dimens.SpaceL))
    }
}

private fun PageSize.label() = when (this) {
    PageSize.AUTO -> "Fit"
    PageSize.A4 -> "A4"
    PageSize.LETTER -> "Letter"
    PageSize.LEGAL -> "Legal"
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun <T> ChipRow(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
        values.forEach { v -> UnidocChip(label(v), v == selected, { onSelect(v) }) }
    }
}

@Composable
private fun QualityCard(preset: QualityPreset, estimate: Long?, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val title = when (preset) {
        QualityPreset.HIGH -> "High"
        QualityPreset.BALANCED -> "Balanced"
        QualityPreset.SMALL -> "Small"
    }
    UnidocCard(modifier = modifier, onClick = onClick, selected = selected) {
        Column(Modifier.fillMaxWidth().padding(vertical = Dimens.SpaceM, horizontal = Dimens.SpaceS)) {
            com.rajdeep.unidoc.core.ui.components.FitText(
                title,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                minSize = 11.sp,
                modifier = Modifier.fillMaxWidth()
            )
            com.rajdeep.unidoc.core.ui.components.FitText(
                estimate?.let(::formatFileSize) ?: "…",
                style = MaterialTheme.typography.headlineSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                minSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "${preset.dpi} dpi",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

object SampleExport {
    val state = ExportUiState(
        fileName = "Offer letter — Northwind",
        estimates = mapOf(
            QualityPreset.HIGH to 1_240_000L,
            QualityPreset.BALANCED to 412_000L,
            QualityPreset.SMALL to 188_000L
        ),
        originalBytes = 7_200_000
    )
}
