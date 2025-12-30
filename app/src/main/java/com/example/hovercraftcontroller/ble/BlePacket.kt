package com.example.hovercraftcontroller.ble

import kotlin.math.roundToInt

object BlePacket {
    private const val START_BYTE: Byte = 0xA5.toByte()
    private const val END_BYTE: Byte = 0x5A.toByte()

    fun buildCommand(
        sequence: Int,
        throttle: Float,
        rudder: Float,
        lift: Float,
        arm: Boolean,
        stop: Boolean
    ): ByteArray {
        val payload = ByteArray(10)
        payload[0] = START_BYTE
        payload[1] = (sequence and 0xFF).toByte()
        payload[2] = scaleUnsigned(throttle)
        payload[3] = scaleSigned(rudder)
        payload[4] = scaleUnsigned(lift)
        payload[5] = buildFlags(arm = arm, stop = stop)
        payload[6] = 0
        payload[7] = 0
        payload[8] = 0
        payload[9] = END_BYTE
        return payload
    }

    private fun buildFlags(arm: Boolean, stop: Boolean): Byte {
        var flags = 0
        if (arm) flags = flags or 0x01
        if (stop) flags = flags or 0x02
        return flags.toByte()
    }

    private fun scaleSigned(value: Float): Byte {
        val clamped = value.coerceIn(-1f, 1f)
        val scaled = (clamped * 100f).roundToInt().coerceIn(-100, 100)
        return scaled.toByte()
    }

    private fun scaleUnsigned(value: Float): Byte {
        val clamped = value.coerceIn(0f, 1f)
        val scaled = (clamped * 100f).roundToInt().coerceIn(0, 100)
        return scaled.toByte()
    }
}
