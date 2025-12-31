# Hovercraft Controller Setup Guide

## Requirements
- Android Studio (Giraffe+ recommended)
- Android SDK 26+
- Android device with BLE

## Project Setup
1) Open the project in Android Studio.
2) Let Gradle sync finish.
3) Connect a physical Android device (BLE required).

## Configure BLE UUIDs and Filters
Update these constants in `app/src/main/java/com/example/hovercraftcontroller/ble/BleConfig.kt`:
- `SERVICE_UUID`: GATT service UUID used by the ESP32.
- `COMMAND_CHAR_UUID`: Write characteristic UUID (Write Without Response).
- `TELEMETRY_CHAR_UUID`: Optional notify characteristic (not used yet).
- `DEVICE_NAME_PREFIX`: Optional name prefix filter (set to empty to disable).

## Firmware (ESP32-S3)
1) Open `firmware/hovercraft_controller/hovercraft_controller.ino` in Arduino IDE.
2) Select board: **ESP32S3 Dev Module**.
3) Update pins if needed:
   - `THROTTLE_PWM_PIN`
   - `RUDDER_PWM_PIN`
4) Flash the board.

## Build and Run
- Run the `app` configuration on your device.
- Grant Bluetooth permissions when prompted.

## How to Use
1) Scan
   - Tap Scan to discover devices.
   - Tap a device to connect.
2) Control
   - Tap Arm to enable command streaming.
   - Hold the throttle slider to send thrust (0 to 100).
   - Use the rudder slider for steering (-100 to 100).
   - Release a slider and it returns to 0.
   - Tap STOP to immediately disarm and send a stop packet.
3) Settings
   - Open Settings from the Control screen.
   - Adjust command rate, sensitivity, dead zone, axis inversion, and logging.

## Customization Notes
- Throttle is one-way (0..100). This is enforced in:
  - `app/src/main/java/com/example/hovercraftcontroller/ui/control/ControlViewModel.kt`
  - `app/src/main/java/com/example/hovercraftcontroller/ble/BlePacket.kt`
- Rudder remains bidirectional (-100..100).
- Lift is not used.
- Packet format (8 bytes):
  - Byte 0: 0xA5 (start)
  - Byte 1: seq
  - Byte 2: throttle (0..100)
  - Byte 3: rudder (-100..100)
  - Byte 4: flags (bit0 ARM, bit1 STOP)
  - Byte 5: reserved (0)
  - Byte 6: CRC8 (poly 0x07 over bytes 0..5)
  - Byte 7: 0x5A (end)
- Settings are persisted via DataStore in:
  - `app/src/main/java/com/example/hovercraftcontroller/data/SettingsRepository.kt`

## Troubleshooting
- If no devices appear, confirm the UUIDs and name filter.
- If connect fails, check Android BLE permissions and the ESP32 GATT server.
- If commands do nothing, confirm the characteristic supports Write Without Response.
