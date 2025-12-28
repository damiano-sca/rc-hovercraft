package com.example.hovercraftcontroller.ui.control

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class ControlViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ControlUiState())
    val uiState = _uiState.asStateFlow()

    fun toggleArm() {
        _uiState.update { state ->
            state.copy(isArmed = !state.isArmed, isStopped = false)
        }
    }

    fun stop() {
        _uiState.update { state ->
            state.copy(
                isArmed = false,
                isStopped = true,
                throttle = 0f,
                turn = 0f
            )
        }
    }

    fun updateThrottle(value: Float) {
        _uiState.update { it.copy(throttle = value.coerceIn(-1f, 1f)) }
    }

    fun updateTurn(value: Float) {
        _uiState.update { it.copy(turn = value.coerceIn(-1f, 1f)) }
    }
}
