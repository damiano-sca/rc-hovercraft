package com.example.hovercraftcontroller.ble

/**
 * Snapshot of a discovered BLE peripheral for list display.
 */
data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

/**
 * Battery telemetry gathered over BLE.
 */
data class BatteryState(
    val percent: Int? = null,
    val voltageMv: Int? = null,
    val timestampMs: Long? = null
) {
    /**
     * Convenience conversion of millivolts to volts.
     */
    val voltageV: Double?
        get() = voltageMv?.let { it / 1000.0 }
}

/**
 * Current BLE scanning state.
 */
enum class ScanStatus {
    Idle,
    Scanning
}

/**
 * High-level connection state for the active peripheral.
 */
sealed interface ConnectionState {
    /**
     * No active GATT connection.
     */
    data object Disconnected : ConnectionState

    /**
     * Attempting to connect to the given device address.
     */
    data class Connecting(val address: String) : ConnectionState

    /**
     * Connected to the given device address.
     */
    data class Connected(val address: String) : ConnectionState
}
