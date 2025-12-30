package com.example.hovercraftcontroller.ui.control

import com.example.hovercraftcontroller.ble.ConnectionState

data class ControlUiState(
    val isArmed: Boolean = false,
    val throttle: Float = 0f,
    val rudder: Float = 0f,
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val rssi: Int? = null,
    val commandRateHz: Int = 60,
    val isStopped: Boolean = false
)
