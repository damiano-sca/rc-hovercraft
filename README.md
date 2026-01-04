# Hovercraft Controller

Android companion app + ESP32 firmware for a small hovercraft / remote vehicle.

## What this is

- Android app that provides a landscape control surface (throttle, rudder) using Jetpack Compose.
- ESP32-based firmware (Arduino) that exposes a BLE GATT service and accepts compact 8-byte control packets.

## Features

- BLE scanning, connect/disconnect, RSSI display.
- Arm/disarm safety, failsafe timeout, configurable command rate and settings (in-app).
- Firmware drives ESC (throttle) and a rudder servo via PWM.

## Quick start

Prerequisites:

- Android Studio (Giraffe+ recommended)
- Android SDK (minSdk 26)
- A physical Android device with Bluetooth LE
- Arduino IDE or PlatformIO (for flashing the ESP32-S3)

Build & run Android app:

1. Open the project in Android Studio.
2. Let Gradle sync finish.
3. Connect an Android device and run the `app` configuration.

Or build from command line:

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Flash firmware (ESP32-S3):

1. Open `firmware/hovercraft_controller/hovercraft_controller.ino` in Arduino IDE.
2. Select board: **ESP32S3 Dev Module** and correct port.
3. Adjust pin constants if required, then upload.

## BLE Details / Protocol

- GATT Service UUID: `0000FEED-0000-1000-8000-00805F9B34FB`
- Command Characteristic UUID (write): `0000BEEF-0000-1000-8000-00805F9B34FB`

Packet format: fixed 8 bytes

- Byte 0: 0xA5 (start)
- Byte 1: sequence number
- Byte 2: throttle (0..100)
- Byte 3: rudder (-100..100) encoded as unsigned byte
- Byte 4: flags (bit0 = ARM, bit1 = STOP)
- Byte 5: reserved (0)
- Byte 6: CRC8 (poly 0x07 over bytes 0..5)
- Byte 7: 0x5A (end)

The firmware enforces failsafe (defaults to disarmed and zero throttle after ~250 ms without packets).

## Important files

- App entry / UI: [app/src/main/java/com/example/hovercraftcontroller/ui/control/ControlScreen.kt](app/src/main/java/com/example/hovercraftcontroller/ui/control/ControlScreen.kt#L1)
- BLE configuration and packet helpers: [app/src/main/java/com/example/hovercraftcontroller/ble](app/src/main/java/com/example/hovercraftcontroller/ble)
- ViewModels and settings: [app/src/main/java/com/example/hovercraftcontroller/ui](app/src/main/java/com/example/hovercraftcontroller/ui)
- Firmware sketch: [firmware/hovercraft_controller/hovercraft_controller.ino](firmware/hovercraft_controller/hovercraft_controller.ino#L1)
- Project setup notes: [SETUP.md](SETUP.md#L1)

## Troubleshooting

- No devices found: check service UUID and optional name filter in `app/src/main/java/com/example/hovercraftcontroller/ble/BleConfig.kt`.
- Connect fails: verify Android BLE permissions and that the ESP32 is advertising the expected service.
- Commands ignored: ensure the command characteristic supports Write Without Response and the CRC/frame are correct.

## Customization

- Change PWM pins, servo ranges, or device name in the firmware sketch.
- Adjust command rate, dead zone, and axis inversion in app settings (persisted via DataStore).

## License & Contact

This repository does not include a license file. If you want one added, tell me which license to include.