package com.rajdeep.unidoc

import android.graphics.Color
import android.os.Bundle
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.rajdeep.unidoc.core.model.UserSettings
import com.rajdeep.unidoc.core.ui.motion.LocalHapticsEnabled
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.ui.UnidocApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        var uiState: MainActivityUiState by mutableStateOf(MainActivityUiState.Loading)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { uiState = it }
            }
        }

        // Hold the splash until settings are read, so the first frame already has the right theme.
        splashScreen.setKeepOnScreenCondition { uiState is MainActivityUiState.Loading }
        splashScreen.setOnExitAnimationListener { provider ->
            val easing = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
            runCatching {
                provider.iconView.animate()
                    .scaleX(1.15f)
                    .scaleY(1.15f)
                    .alpha(0f)
                    .setDuration(EXIT_DURATION_MS)
                    .setInterpolator(easing)
                    .start()
            }
            provider.view.animate()
                .alpha(0f)
                .setStartDelay(EXIT_DELAY_MS)
                .setDuration(EXIT_DURATION_MS)
                .setInterpolator(easing)
                .withEndAction { provider.remove() }
                .start()
        }

        enableEdgeToEdge()

        setContent {
            val settings = (uiState as? MainActivityUiState.Ready)?.settings ?: UserSettings.Default
            val darkTheme = settings.themeMode.isDark(isSystemInDarkTheme())

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_NAV_SCRIM, DARK_NAV_SCRIM) { darkTheme }
                )
                onDispose { }
            }

            UnidocTheme(darkTheme = darkTheme, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(LocalHapticsEnabled provides settings.hapticsEnabled) {
                    UnidocApp(versionName = BuildConfig.VERSION_NAME)
                }
            }
        }
    }

    private companion object {
        const val EXIT_DURATION_MS = 280L
        const val EXIT_DELAY_MS = 60L
        val LIGHT_NAV_SCRIM = Color.argb(0x00, 0xF7, 0xF7, 0xFB)
        val DARK_NAV_SCRIM = Color.argb(0x00, 0x0B, 0x0C, 0x14)
    }
}
