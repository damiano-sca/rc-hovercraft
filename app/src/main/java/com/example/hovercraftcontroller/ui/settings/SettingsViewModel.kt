package com.example.hovercraftcontroller.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hovercraftcontroller.HovercraftApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository =
        (application as HovercraftApplication).settingsRepository

    val uiState = repository.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState()
    )

    fun setCommandRate(rate: Int) {
        viewModelScope.launch {
            repository.setCommandRate(rate)
        }
    }

    fun setSensitivity(value: Float) {
        viewModelScope.launch {
            repository.setSensitivity(value.coerceIn(0.5f, 1.5f))
        }
    }

    fun setDeadZone(value: Float) {
        viewModelScope.launch {
            repository.setDeadZone(value.coerceIn(0f, 0.2f))
        }
    }

    fun setInvertThrottle(enabled: Boolean) {
        viewModelScope.launch {
            repository.setInvertThrottle(enabled)
        }
    }

    fun setInvertRudder(enabled: Boolean) {
        viewModelScope.launch {
            repository.setInvertRudder(enabled)
        }
    }

    fun setDebugLogging(enabled: Boolean) {
        viewModelScope.launch {
            repository.setDebugLogging(enabled)
        }
    }
}
