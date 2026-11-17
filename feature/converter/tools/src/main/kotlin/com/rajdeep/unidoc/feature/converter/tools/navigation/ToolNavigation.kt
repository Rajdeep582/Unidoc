package com.rajdeep.unidoc.feature.converter.tools.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.rajdeep.unidoc.core.model.ToolId
import com.rajdeep.unidoc.core.ui.brand.ScreenSurface
import com.rajdeep.unidoc.feature.converter.tools.ToolRouteScreen
import kotlinx.serialization.Serializable

@Serializable
data class ToolRoute(val toolId: String)

fun NavController.navigateToTool(tool: ToolId) = navigate(ToolRoute(tool.name))

fun NavGraphBuilder.toolScreen(onBack: () -> Unit) {
    composable<ToolRoute> { ScreenSurface { ToolRouteScreen(onBack = onBack) } }
}
