package com.example.hovercraftcontroller.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    fun setCommandRate(rate: Int) {
        _uiState.update { it.copy(commandRateHz = rate) }
    }

    fun setSensitivity(value: Float) {
        _uiState.update { it.copy(sensitivity = value.coerceIn(0.5f, 1.5f)) }
    }

    fun setDeadZone(value: Float) {
        _uiState.update { it.copy(deadZone = value.coerceIn(0f, 0.2f)) }
    }

    fun setInvertThrottle(enabled: Boolean) {
        _uiState.update { it.copy(invertThrottle = enabled) }
    }

    fun setInvertTurn(enabled: Boolean) {
        _uiState.update { it.copy(invertTurn = enabled) }
    }

    fun setDebugLogging(enabled: Boolean) {
        _uiState.update { it.copy(debugLogging = enabled) }
    }
}
