package com.example.hovercraftcontroller.ui.control

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hovercraftcontroller.HovercraftApplication
import com.example.hovercraftcontroller.ble.BlePacket
import com.example.hovercraftcontroller.ble.ConnectionState
import com.example.hovercraftcontroller.ui.settings.SettingsUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

class ControlViewModel(application: Application) : AndroidViewModel(application) {
    private val bleRepository =
        (application as HovercraftApplication).bleRepository
    private val settingsRepository =
        (application as HovercraftApplication).settingsRepository

    private val _uiState = MutableStateFlow(ControlUiState())
    val uiState = _uiState.asStateFlow()

    private var settingsState = SettingsUiState()
    private var streamJob: Job? = null
    private var sequence = 0

    init {
        viewModelScope.launch {
            bleRepository.connectionState.collectLatest { state ->
                _uiState.update { current ->
                    if (state is ConnectionState.Disconnected) {
                        current.copy(
                            connectionState = state,
                            isArmed = false,
                            isStopped = false,
                            throttle = 0f,
                            rudder = 0f
                        )
                    } else {
                        current.copy(connectionState = state)
                    }
                }
                updateStreaming()
            }
        }
        viewModelScope.launch {
            bleRepository.rssi.collectLatest { rssi ->
                _uiState.update { it.copy(rssi = rssi) }
            }
        }
        viewModelScope.launch {
            settingsRepository.settingsFlow.collectLatest { settings ->
                settingsState = settings
                _uiState.update { it.copy(commandRateHz = settings.commandRateHz) }
                updateStreaming()
            }
        }
    }

    fun toggleArm() {
        val current = _uiState.value
        if (current.isArmed) {
            _uiState.update { it.copy(isArmed = false, isStopped = false) }
            sendNeutral()
            updateStreaming()
            return
        }
        if (current.connectionState is ConnectionState.Connected) {
            _uiState.update { it.copy(isArmed = true, isStopped = false) }
            updateStreaming()
        }
    }

    fun stop() {
        _uiState.update { state ->
            state.copy(
                isArmed = false,
                isStopped = true,
                throttle = 0f,
                rudder = 0f
            )
        }
        sendStop()
        updateStreaming()
    }

    fun updateThrottle(value: Float) {
        _uiState.update { it.copy(throttle = value.coerceIn(0f, 1f)) }
    }

    fun updateRudder(value: Float) {
        _uiState.update { it.copy(rudder = value.coerceIn(-1f, 1f)) }
    }

    private fun updateStreaming() {
        val shouldStream = _uiState.value.isArmed &&
            _uiState.value.connectionState is ConnectionState.Connected
        if (shouldStream) {
            if (streamJob?.isActive != true) {
                startStreaming()
            }
        } else {
            streamJob?.cancel()
            streamJob = null
        }
    }

    private fun startStreaming() {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            while (true) {
                val state = _uiState.value
                val throttle = applyThrottle(state.throttle)
                val rudder = applyRudder(state.rudder)
                val packet = BlePacket.buildCommand(
                    sequence = sequence,
                    throttle = throttle,
                    rudder = rudder,
                    lift = 0f,
                    arm = state.isArmed,
                    stop = false
                )
                bleRepository.sendCommand(packet)
                sequence = (sequence + 1) and 0xFF
                val rate = settingsState.commandRateHz.coerceAtLeast(1)
                delay((1_000L / rate).coerceAtLeast(5L))
            }
        }
    }

    private fun sendNeutral() {
        val packet = BlePacket.buildCommand(
            sequence = sequence,
            throttle = 0f,
            rudder = 0f,
            lift = 0f,
            arm = false,
            stop = false
        )
        bleRepository.sendCommand(packet)
        sequence = (sequence + 1) and 0xFF
    }

    private fun sendStop() {
        val packet = BlePacket.buildCommand(
            sequence = sequence,
            throttle = 0f,
            rudder = 0f,
            lift = 0f,
            arm = false,
            stop = true
        )
        bleRepository.sendCommand(packet)
        sequence = (sequence + 1) and 0xFF
    }

    private fun applyThrottle(value: Float): Float {
        var adjusted = value.coerceIn(0f, 1f)
        if (adjusted < settingsState.deadZone) {
            adjusted = 0f
        }
        adjusted *= settingsState.sensitivity
        if (settingsState.invertThrottle) {
            adjusted = 1f - adjusted
        }
        return adjusted.coerceIn(0f, 1f)
    }

    private fun applyRudder(value: Float): Float {
        var adjusted = value.coerceIn(-1f, 1f)
        if (abs(adjusted) < settingsState.deadZone) {
            adjusted = 0f
        }
        adjusted *= settingsState.sensitivity
        if (settingsState.invertRudder) {
            adjusted = -adjusted
        }
        return adjusted.coerceIn(-1f, 1f)
    }
}
