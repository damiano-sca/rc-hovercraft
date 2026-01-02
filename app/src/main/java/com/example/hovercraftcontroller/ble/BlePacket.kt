package com.example.hovercraftcontroller.ble

import kotlin.math.roundToInt

object BlePacket {
    private const val START_BYTE: Byte = 0xA5.toByte()
    private const val END_BYTE: Byte = 0x5A.toByte()

    fun buildCommand(
        sequence: Int,
        throttle: Float,
        rudder: Float,
        arm: Boolean
    ): ByteArray {
        val payload = ByteArray(8)
        payload[0] = START_BYTE
        payload[1] = (sequence and 0xFF).toByte()
        payload[2] = scaleUnsigned(throttle)
        payload[3] = scaleRudderAngle(rudder)
        payload[4] = buildFlags(arm = arm)
        payload[5] = 0
        payload[6] = crc8(payload, 6)
        payload[7] = END_BYTE
        return payload
    }

    private fun buildFlags(arm: Boolean): Byte {
        var flags = 0
        if (arm) flags = flags or 0x01
        return flags.toByte()
    }

    private fun scaleRudderAngle(value: Float): Byte {
        val clamped = value.coerceIn(-1f, 1f)
        val angle = ((clamped + 1f) * 0.5f * 140f + 20f).roundToInt().coerceIn(20, 160)
        return angle.toByte()
    }

    private fun scaleUnsigned(value: Float): Byte {
        val clamped = value.coerceIn(0f, 1f)
        val scaled = (clamped * 100f).roundToInt().coerceIn(0, 100)
        return scaled.toByte()
    }

    private fun crc8(data: ByteArray, length: Int): Byte {
        var crc = 0
        for (i in 0 until length) {
            var value = data[i].toInt() and 0xFF
            crc = crc xor value
            repeat(8) {
                crc = if ((crc and 0x80) != 0) {
                    ((crc shl 1) xor 0x07) and 0xFF
                } else {
                    (crc shl 1) and 0xFF
                }
            }
        }
        return crc.toByte()
    }
}
