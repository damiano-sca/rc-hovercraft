package com.example.hovercraftcontroller.ui.settings

data class SettingsUiState(
    val commandRateHz: Int = 60,
    val deadZone: Float = 0.05f,
    val invertRudder: Boolean = false,
    val rudderCenter: Int = 90,
    val rudderMaxAngle: Int = 70
)
