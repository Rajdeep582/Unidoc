package com.rajdeep.unidoc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rajdeep.unidoc.core.datastore.SettingsRepository
import com.rajdeep.unidoc.core.model.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class MainActivityViewModel @Inject constructor(settingsRepository: SettingsRepository) : ViewModel() {
    val uiState: StateFlow<MainActivityUiState> = settingsRepository.settings
        .map<UserSettings, MainActivityUiState>(MainActivityUiState::Ready)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MainActivityUiState.Loading
        )
}

sealed interface MainActivityUiState {
    data object Loading : MainActivityUiState

    data class Ready(val settings: UserSettings) : MainActivityUiState
}
