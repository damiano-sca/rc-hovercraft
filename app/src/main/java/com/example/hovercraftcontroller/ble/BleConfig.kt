package com.example.hovercraftcontroller.ble

/**
 * BLE UUIDs and naming conventions for the hovercraft peripheral.
 */
object BleConfig {
    /** Primary service UUID exposed by the hovercraft. */
    const val SERVICE_UUID = "0000FEED-0000-1000-8000-00805F9B34FB"

    /** Characteristic used to send control commands. */
    const val COMMAND_CHAR_UUID = "0000BEEF-0000-1000-8000-00805F9B34FB"

    /** Characteristic used to stream telemetry updates. */
    const val TELEMETRY_CHAR_UUID = "0000DEAD-0000-1000-8000-00805F9B34FB"

    /** Standard Battery Service UUID. */
    const val BATTERY_SERVICE_UUID = "0000180F-0000-1000-8000-00805F9B34FB"

    /** Standard Battery Level characteristic UUID. */
    const val BATTERY_LEVEL_CHAR_UUID = "00002A19-0000-1000-8000-00805F9B34FB"

    /** Service UUID that hosts the battery voltage characteristic. */
    const val VOLTAGE_SERVICE_UUID = SERVICE_UUID

    /** Characteristic that reports battery voltage in millivolts. */
    const val VOLTAGE_CHAR_UUID = "0000BABA-0000-1000-8000-00805F9B34FB"

    /** Client Characteristic Configuration Descriptor UUID. */
    const val CCCD_UUID = "00002902-0000-1000-8000-00805F9B34FB"

    /** Expected name prefix advertised by the hovercraft. */
    const val DEVICE_NAME_PREFIX = "Wobble Wagon"
}
