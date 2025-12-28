package com.example.hovercraftcontroller.ui.control

data class ControlUiState(
    val isArmed: Boolean = false,
    val throttle: Float = 0f,
    val rudder: Float = 0f,
    val rssi: Int = -62,
    val commandRateHz: Int = 60,
    val connectionLabel: String = "Connected",
    val isStopped: Boolean = false
)
