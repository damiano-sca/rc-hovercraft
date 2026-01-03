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
import kotlin.math.roundToInt

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
                            throttle = 0f,
                            rudder = settingsState.rudderCenter.toFloat()
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
                _uiState.update { current ->
                    val rudderValue = if (!current.isArmed) {
                        settings.rudderCenter.toFloat()
                    } else {
                        current.rudder
                    }
                    current.copy(
                        commandRateHz = settings.commandRateHz,
                        rudderCenter = settings.rudderCenter,
                        rudderMaxAngle = settings.rudderMaxAngle,
                        rudder = rudderValue
                    )
                }
                updateStreaming()
            }
        }
    }

    fun toggleArm() {
        val current = _uiState.value
        if (current.isArmed) {
            _uiState.update {
                it.copy(
                    isArmed = false,
                    throttle = 0f,
                    rudder = settingsState.rudderCenter.toFloat()
                )
            }
            sendDisarm()
            updateStreaming()
            return
        }
        if (current.connectionState is ConnectionState.Connected) {
            _uiState.update { it.copy(isArmed = true) }
            updateStreaming()
        }
    }

    fun disconnect() {
        bleRepository.disconnect()
    }

    fun updateThrottle(value: Float) {
        _uiState.update { it.copy(throttle = value.coerceIn(0f, 1f)) }
    }

    fun updateRudder(value: Float) {
        _uiState.update { it.copy(rudder = value) }
    }

    fun centerRudder() {
        _uiState.update { it.copy(rudder = settingsState.rudderCenter.toFloat()) }
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
                val rudderAngle = applyRudderAngle(state.rudder)
                val packet = BlePacket.buildCommand(
                    sequence = sequence,
                    throttle = throttle,
                    rudderAngle = rudderAngle,
                    arm = state.isArmed
                )
                bleRepository.sendCommand(packet)
                sequence = (sequence + 1) and 0xFF
                val rate = settingsState.commandRateHz.coerceAtLeast(1)
                delay((1_000L / rate).coerceAtLeast(5L))
            }
        }
    }

    private fun sendDisarm() {
        val packet = BlePacket.buildCommand(
            sequence = sequence,
            throttle = 0f,
            rudderAngle = 0,
            arm = false
        )
        bleRepository.sendCommand(packet)
        sequence = (sequence + 1) and 0xFF
    }

    private fun applyThrottle(value: Float): Float {
        var adjusted = value.coerceIn(0f, 1f)
        if (adjusted < settingsState.deadZone) {
            adjusted = 0f
        }
        return adjusted.coerceIn(0f, 1f)
    }

    private fun applyRudderAngle(value: Float): Int {
        val center = settingsState.rudderCenter.coerceIn(0, 180)
        val maxDelta = settingsState.rudderMaxAngle
            .coerceIn(0, minOf(center, 180 - center))
        if (maxDelta == 0) {
            return center
        }
        val minAngle = (center - maxDelta).toFloat()
        val maxAngle = (center + maxDelta).toFloat()
        val clamped = value.coerceIn(minAngle, maxAngle)
        var normalized = (clamped - center) / maxDelta.toFloat()
        if (abs(normalized) < settingsState.deadZone) {
            normalized = 0f
        }
        if (settingsState.invertRudder) {
            normalized = -normalized
        }
        val finalAngle = center + (normalized.coerceIn(-1f, 1f) * maxDelta)
        return finalAngle.roundToInt()
    }
}
