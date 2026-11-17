package com.rajdeep.unidoc.feature.converter.tools.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.rajdeep.unidoc.core.ui.components.SwitchRow
import com.rajdeep.unidoc.core.ui.components.UnidocCard
import com.rajdeep.unidoc.core.ui.components.UnidocChip
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.feature.converter.tools.ToolOption
import kotlin.math.roundToInt

@Composable
fun OptionField(option: ToolOption, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    when (option) {
        is ToolOption.Choice -> Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)) {
            Label(option.label)
            if (option.choices.size <= 6) {
                // Every choice visible (wraps) — no option hidden past the screen edge.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceS)
                ) {
                    option.choices.forEach { (key, label) -> UnidocChip(label, key == value, { onChange(key) }) }
                }
            } else {
                // Long lists (languages) scroll; bleed to the screen edge so it reads as scrollable.
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                    contentPadding = PaddingValues(horizontal = Dimens.Gutter),
                    modifier = Modifier.layout { measurable, constraints ->
                        val extra = Dimens.Gutter.roundToPx() * 2
                        val p = measurable.measure(
                            constraints.copy(
                                maxWidth = constraints.maxWidth + extra,
                                minWidth =
                                constraints.minWidth + extra
                            )
                        )
                        layout(p.width - extra, p.height) { p.place(-extra / 2, 0) }
                    }
                ) {
                    items(option.choices) { (key, label) -> UnidocChip(label, key == value, { onChange(key) }) }
                }
            }
        }
        is ToolOption.Toggle -> UnidocCard(modifier.fillMaxWidth()) {
            SwitchRow(
                title = option.label,
                subtitle = option.description,
                checked = value.toBoolean(),
                onCheckedChange = { onChange(it.toString()) }
            )
        }
        is ToolOption.Slider -> Column(modifier) {
            val v = value.toFloatOrNull() ?: option.defaultValue
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(option.label, Modifier.weight(1f))
                val shown = if (option.range.endInclusive <=
                    1f
                ) {
                    "${(v * 100).roundToInt()}%"
                } else {
                    if (option.unit == "%") "${v.roundToInt()}%" else "${v.roundToInt()} ${option.unit}".trim()
                }
                Text(shown, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            // Whole numbers for counts/sizes/percentages; fractions only for 0..1 ranges.
            Slider(value = v, onValueChange = {
                onChange(if (option.range.endInclusive > 1f) it.roundToInt().toString() else "%.2f".format(java.util.Locale.US, it))
            }, valueRange = option.range, steps = option.steps)
        }
        is ToolOption.Text -> OutlinedTextField(
            value = value,
            onValueChange = onChange,
            label = { Text(option.label + if (option.required) " *" else "") },
            placeholder = { Text(option.placeholder, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
            singleLine = !option.multiline,
            visualTransformation = if (option.password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (option.password) KeyboardType.Password else KeyboardType.Text
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = modifier.fillMaxWidth().then(
                if (option.multiline) Modifier.heightIn(min = 140.dp) else Modifier
            )
        )
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 2.dp)
    )
}
