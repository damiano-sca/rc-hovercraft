package com.example.hovercraftcontroller.ui.settings

data class SettingsUiState(
    val commandRateHz: Int = 60,
    val sensitivity: Float = 1.0f,
    val deadZone: Float = 0.05f,
    val invertThrottle: Boolean = false,
    val invertTurn: Boolean = false,
    val debugLogging: Boolean = false
)
