package com.rajdeep.unidoc.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajdeep.unidoc.core.datastore.SettingsRepository
import com.rajdeep.unidoc.core.model.CloudProbe
import com.rajdeep.unidoc.core.model.OcrLanguage
import com.rajdeep.unidoc.core.model.QualityPreset
import com.rajdeep.unidoc.core.model.ThemeMode
import com.rajdeep.unidoc.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository, private val probe: CloudProbe) : ViewModel() {
    private val _probeState = MutableStateFlow<ProbeState>(ProbeState.Idle)
    val probeState: StateFlow<ProbeState> = _probeState.asStateFlow()

    val settings: StateFlow<UserSettings?> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onAction(action: SettingsAction) {
        viewModelScope.launch {
            when (action) {
                is SettingsAction.Theme -> repository.setThemeMode(action.mode)
                is SettingsAction.DynamicColor -> repository.setDynamicColor(action.enabled)
                is SettingsAction.Haptics -> repository.setHapticsEnabled(action.enabled)
                is SettingsAction.Quality -> repository.setDefaultQuality(action.preset)
                is SettingsAction.AutoCapture -> repository.setAutoCapture(action.enabled)
                is SettingsAction.OcrLanguages -> repository.setOcrLanguages(action.languages)
                is SettingsAction.Cloud -> repository.setCloudEnabled(action.enabled)
                is SettingsAction.CloudServer -> {
                    repository.setCloudServerUrl(action.url.trim().trimEnd('/'))
                    repository.setCloudApiKey(action.key.trim())
                }
                is SettingsAction.TestCloud -> {
                    _probeState.value = ProbeState.Testing
                    _probeState.value = runCatching { probe.check(action.url, action.key) }
                        .fold({ ProbeState.Ok(it) }, { ProbeState.Failed(it.message ?: "Connection failed") })
                }
                SettingsAction.ResetProbe -> _probeState.value = ProbeState.Idle
            }
        }
    }
}

sealed interface SettingsAction {
    data class Theme(val mode: ThemeMode) : SettingsAction

    data class DynamicColor(val enabled: Boolean) : SettingsAction

    data class Haptics(val enabled: Boolean) : SettingsAction

    data class Quality(val preset: QualityPreset) : SettingsAction

    data class AutoCapture(val enabled: Boolean) : SettingsAction

    data class OcrLanguages(val languages: Set<OcrLanguage>) : SettingsAction

    data class Cloud(val enabled: Boolean) : SettingsAction

    data class CloudServer(val url: String, val key: String) : SettingsAction

    data class TestCloud(val url: String, val key: String) : SettingsAction

    data object ResetProbe : SettingsAction
}

sealed interface ProbeState {
    data object Idle : ProbeState

    data object Testing : ProbeState

    data class Ok(val message: String) : ProbeState

    data class Failed(val message: String) : ProbeState
}
