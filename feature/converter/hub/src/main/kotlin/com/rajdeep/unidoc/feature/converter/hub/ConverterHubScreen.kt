package com.rajdeep.unidoc.feature.converter.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rajdeep.unidoc.core.model.ToolCategory
import com.rajdeep.unidoc.core.model.ToolId
import com.rajdeep.unidoc.core.ui.catalog.presentation
import com.rajdeep.unidoc.core.ui.components.FitText
import com.rajdeep.unidoc.core.ui.components.IconBadge
import com.rajdeep.unidoc.core.ui.components.IconButtonSurface
import com.rajdeep.unidoc.core.ui.components.ScreenHeader
import com.rajdeep.unidoc.core.ui.components.SearchField
import com.rajdeep.unidoc.core.ui.components.SectionHeader
import com.rajdeep.unidoc.core.ui.components.UnidocCard
import com.rajdeep.unidoc.core.ui.components.UnidocChip
import com.rajdeep.unidoc.core.ui.theme.Dimens

/** Pure filter used by the hub (and its tests). */
fun filterTools(query: String, category: ToolCategory?): Map<ToolCategory, List<ToolId>> {
    val q = query.trim()
    return ToolId.entries
        .filter { category == null || it.category == category }
        .filter { tool ->
            val p = tool.presentation()
            q.isEmpty() ||
                p.title.contains(q, true) ||
                p.description.contains(q, true) ||
                tool.category.presentation().title.contains(q, true)
        }
        .groupBy { it.category }
}

@Composable
fun ConverterHubScreen(
    contentPadding: PaddingValues,
    onToolSelected: (ToolId) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<ToolCategory?>(null) }
    val grouped = filterTools(query, category)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = Dimens.Gutter,
            end = Dimens.Gutter,
            bottom = contentPadding.calculateBottomPadding() + Dimens.SpaceXl
        ),
        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceM),
        modifier = modifier.fillMaxSize()
    ) {
        full("header") {
            ScreenHeader(
                title = "Uni Converter",
                subtitle = "${ToolId.entries.size} PDF, Office and image tools",
                horizontalPadding = 0.dp,
                modifier = Modifier.statusBarsPadding()
            ) { IconButtonSurface(Icons.Rounded.Settings, "Settings", onOpenSettings) }
        }
        full("search") { SearchField(query, { query = it }, "Search tools — merge, word, sign…") }
        full("chips") {
            // Bleed to the screen edges so the category row reads as scrollable, like the library folders.
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceS),
                contentPadding = PaddingValues(horizontal = Dimens.Gutter),
                modifier = Modifier
                    .layout { measurable, constraints ->
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
                    .padding(vertical = Dimens.SpaceXs)
            ) {
                item { UnidocChip("All", category == null, { category = null }) }
                items(ToolCategory.entries) { c ->
                    UnidocChip(c.presentation().title, category == c, { category = if (category == c) null else c })
                }
            }
        }
        if (grouped.isEmpty()) {
            full("empty") {
                Column(
                    Modifier.fillMaxWidth().padding(Dimens.SpaceXxl),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No tools match “$query”", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Try “pdf”, “image” or “protect”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        grouped.forEach { (cat, tools) ->
            full("h_${cat.name}") {
                SectionHeader(cat.presentation().title, Modifier.padding(top = Dimens.SpaceS), horizontalPadding = 0.dp)
            }
            tools.forEach { tool ->
                item(key = tool.name) { ToolTile(tool, { onToolSelected(tool) }, Modifier.animateItem()) }
            }
        }
    }
}

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
fun ToolTile(tool: ToolId, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = tool.presentation()
    UnidocCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.fillMaxWidth().heightIn(min = 128.dp).padding(Dimens.SpaceL)) {
            IconBadge(p.icon, accent = tool.category.presentation().accent, size = 42.dp)
            Spacer(Modifier.height(Dimens.SpaceM))
            FitText(
                p.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                minSize = 12.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                p.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
