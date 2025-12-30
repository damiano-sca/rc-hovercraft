package com.example.hovercraftcontroller.ui.scan

import com.example.hovercraftcontroller.ble.BleDevice
import com.example.hovercraftcontroller.ble.ConnectionState
import com.example.hovercraftcontroller.ble.ScanStatus

data class ScanConnectUiState(
    val scanStatus: ScanStatus = ScanStatus.Idle,
    val devices: List<BleDevice> = emptyList(),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val lastError: String? = null
)
