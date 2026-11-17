package com.rajdeep.unidoc.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.rajdeep.unidoc.core.ui.brand.ScreenSurface
import com.rajdeep.unidoc.feature.settings.SettingsRouteScreen
import kotlinx.serialization.Serializable

@Serializable
data object SettingsRoute

fun NavController.navigateToSettings() = navigate(SettingsRoute) { launchSingleTop = true }

fun NavGraphBuilder.settingsScreen(versionName: String, onBack: () -> Unit, onOpenGallery: (() -> Unit)?) {
    composable<SettingsRoute> { ScreenSurface { SettingsRouteScreen(versionName, onBack, onOpenGallery) } }
}
