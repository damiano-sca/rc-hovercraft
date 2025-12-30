# Android BLE Hovercraft Controller — Development Instructions (for Codex)

## Goal
Develop an Android application that connects via **Bluetooth Low Energy (BLE)** to an **ESP32-S3**-based hovercraft controller and streams control commands with **semi-instant response**.

Target performance:
- **50–100 Hz** command update rate
- **Low latency & low jitter**
- Stable BLE connection
- Safety-first UX (arming, stop, failsafe)

The app must be developed **independently from firmware**, using configurable UUIDs and a stable packet format.

---

## Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** MVVM (ViewModel + StateFlow)
- **BLE:** Android Bluetooth LE APIs
- **Min SDK:** 26+ recommended (23+ acceptable with permission handling)

---

## Core Architecture
Android App (BLE Central)
- Scan & Connect
- Control UI (Joystick + Buttons)
- BLE Write Loop (50–100 Hz)
- Optional Telemetry Notifications

ESP32-S3 role:
- BLE GATT Server
- Custom Service
- Command Characteristic (Write Without Response)
- Optional Telemetry Characteristic (Notify)

---

## Screens & UI Flow

### 1. Scan & Connect Screen
- Scan button
- Device list with RSSI
- Tap to connect
- Connection state indicator
- Disconnect / Rescan

### 2. Control Screen
- ARM / DISARM toggle (default DISARM)
- Joystick (throttle + yaw)
- Lift slider
- STOP button
- Status bar (RSSI, Hz, state)

### 3. Settings Screen
- Command rate selector
- Sensitivity & dead zone
- Axis inversion
- Debug logging toggle

---

## Permissions
### Android 12+
- BLUETOOTH_SCAN
- BLUETOOTH_CONNECT

### Android 11 and below
- ACCESS_FINE_LOCATION

---

## BLE Implementation

### Scanning
- BluetoothLeScanner
- Filter by Service UUID or device name
- Stop after 10–15s

### Connection & GATT
- connectGatt(TRANSPORT_LE)
- requestConnectionPriority(HIGH)
- requestMtu(185)
- discoverServices()

UUID placeholders:
SERVICE_UUID
CMD_CHAR_UUID
TELEMETRY_UUID

---

## Command Streaming

### Send Loop
- Coroutine-based
- 50–100 Hz
- WRITE_TYPE_NO_RESPONSE
- Stop on disarm, stop, background, disconnect

### Packet Format (10 bytes)
- Start byte 0xA5
- seq
- throttle (-100..100)
- yaw (-100..100)
- lift (0..100)
- flags (ARM, STOP)
- reserved
- reserved
- crc8 (optional)
- end byte 0x5A

STOP sends neutral packet and disarms.

---

## Joystick
- Circular pad
- Normalized output (-1..1)
- Dead zone
- Spring-back
- Optional throttle-hold
- Haptic feedback

---

## Lifecycle
- Background → DISARM + stop loop
- Resume → require re-ARM

---

## Debug
- Connection state
- RSSI
- MTU
- Command rate
- Last packet (hex)
- Error logs
- Export logs

---

## Acceptance Criteria
- Reliable scan/connect
- Stable 50–100 Hz streaming
- Immediate STOP/DISARM
- Smooth UI


