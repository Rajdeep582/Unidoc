package com.rajdeep.unidoc.feature.converter.hub.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.rajdeep.unidoc.core.model.ToolId
import com.rajdeep.unidoc.core.ui.brand.ScreenSurface
import com.rajdeep.unidoc.feature.converter.hub.ConverterHubScreen
import kotlinx.serialization.Serializable

@Serializable
data object ConverterHubRoute

fun NavController.navigateToConverterHub(navOptions: NavOptions? = null) = navigate(ConverterHubRoute, navOptions)

fun NavGraphBuilder.converterHubScreen(contentPadding: PaddingValues, onToolSelected: (ToolId) -> Unit, onOpenSettings: () -> Unit) {
    composable<ConverterHubRoute> {
        ScreenSurface {
            ConverterHubScreen(
                contentPadding = contentPadding,
                onToolSelected = onToolSelected,
                onOpenSettings = onOpenSettings
            )
        }
    }
}
