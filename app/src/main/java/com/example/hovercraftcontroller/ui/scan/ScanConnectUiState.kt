package com.example.hovercraftcontroller.ui.scan

data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

enum class ScanStatus {
    Idle,
    Scanning
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val address: String) : ConnectionState
    data class Connected(val address: String) : ConnectionState
}

data class ScanConnectUiState(
    val scanStatus: ScanStatus = ScanStatus.Idle,
    val devices: List<BleDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val lastError: String? = null
)
