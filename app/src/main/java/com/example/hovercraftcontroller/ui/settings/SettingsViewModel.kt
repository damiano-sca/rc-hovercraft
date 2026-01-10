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

    fun setDeadZone(value: Float) {
        viewModelScope.launch {
            repository.setDeadZone(value.coerceIn(0f, 0.2f))
        }
    }

    fun setInvertRudder(enabled: Boolean) {
        viewModelScope.launch {
            repository.setInvertRudder(enabled)
        }
    }

    fun setRudderCenter(value: Int) {
        viewModelScope.launch {
            repository.setRudderCenter(value)
        }
    }

    fun setRudderMaxAngle(value: Int) {
        viewModelScope.launch {
            repository.setRudderMaxAngle(value)
        }
    }

    fun resetRudderDefaults() {
        viewModelScope.launch {
            repository.resetRudderDefaults()
        }
    }

}
