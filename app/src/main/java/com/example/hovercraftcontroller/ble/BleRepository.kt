package com.example.hovercraftcontroller.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayDeque
import java.util.UUID
import kotlin.math.roundToInt

/**
 * BLE data source that handles scanning, connecting, and GATT telemetry.
 *
 * Exposes connection and telemetry state as flows for UI consumption.
 */
class BleRepository(private val context: Context) {
    // Android Bluetooth handles.
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    // Coroutine scope for background BLE work.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Deduped scan results keyed by device address.
    private val discoveredDevices = LinkedHashMap<String, BleDevice>()

    // Scan status for observers.
    private val _scanStatus = MutableStateFlow(ScanStatus.Idle)
    val scanStatus = _scanStatus.asStateFlow()

    // List of discovered peripherals sorted by RSSI.
    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices = _devices.asStateFlow()

    // Connection lifecycle state.
    private val _connectionState =
        MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    // Latest user-facing error message.
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()

    // Latest RSSI reading from the active connection.
    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi = _rssi.asStateFlow()

    // Latest battery telemetry from GATT notifications.
    private val _batteryState = MutableStateFlow(BatteryState())
    val batteryState = _batteryState.asStateFlow()

    // Scan session handles.
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var scanJob: Job? = null

    // Active GATT connection handles.
    private var currentGatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var rssiJob: Job? = null
    // Serialize descriptor writes to avoid overlapping GATT operations.
    private val descriptorWriteQueue = ArrayDeque<DescriptorWrite>()
    private var isWritingDescriptor = false
    private var pendingRssiStart = false

    // UUIDs for services and characteristics.
    private val voltageServiceUuid = UUID.fromString(BleConfig.VOLTAGE_SERVICE_UUID)
    private val voltageCharUuid = UUID.fromString(BleConfig.VOLTAGE_CHAR_UUID)
    private val cccdUuid = UUID.fromString(BleConfig.CCCD_UUID)

    /**
     * Start a time-boxed BLE scan and publish discovered devices.
     */
    @SuppressLint("MissingPermission")
    fun startScan() {
        Log.d(TAG, "Start scan")
        val adapter = bluetoothAdapter
        if (adapter == null) {
            setError("Bluetooth is not available on this device.")
            return
        }
        if (!adapter.isEnabled) {
            setError("Bluetooth is disabled. Enable it to scan.")
            return
        }
        if (!hasBluetoothScanPermission()) {
            setError("Bluetooth scan permission is missing.")
            return
        }
        if (_scanStatus.value == ScanStatus.Scanning) {
            return
        }

        discoveredDevices.clear()
        _devices.value = emptyList()
        _scanStatus.value = ScanStatus.Scanning
        _lastError.value = null

        val bleScanner = adapter.bluetoothLeScanner
        if (bleScanner == null) {
            setError("BLE scanner is not available.")
            _scanStatus.value = ScanStatus.Idle
            return
        }
        scanner = bleScanner
        val callback = createScanCallback()
        scanCallback = callback

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val filters = buildFilters()

        try {
            bleScanner.startScan(filters, settings, callback)
        } catch (e: IllegalStateException) {
            setError("Unable to start scan: ${e.message}")
            _scanStatus.value = ScanStatus.Idle
            return
        } catch (e: SecurityException) {
            setError("Bluetooth scan permission is missing.")
            _scanStatus.value = ScanStatus.Idle
            return
        }

        scanJob?.cancel()
        scanJob = scope.launch {
            delay(SCAN_DURATION_MS)
            stopScan()
        }
    }

    /**
     * Stop an active BLE scan and reset the scan status.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (_scanStatus.value != ScanStatus.Scanning) {
            return
        }
        Log.d(TAG, "Stop scan")
        scanJob?.cancel()
        scanJob = null
        scanner?.let { bleScanner ->
            scanCallback?.let { callback ->
                bleScanner.stopScan(callback)
            }
        }
        scanCallback = null
        _scanStatus.value = ScanStatus.Idle
    }

    /**
     * Connect to a peripheral by address and discover services.
     */
    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        Log.d(TAG, "Connect to $address")
        val adapter = bluetoothAdapter
        if (adapter == null) {
            setError("Bluetooth is not available on this device.")
            return
        }
        if (!hasBluetoothConnectPermission()) {
            setError("Bluetooth connect permission is missing.")
            return
        }
        stopScan()
        disconnect()
        _batteryState.value = BatteryState()

        val device = adapter.getRemoteDevice(address)
        _connectionState.value = ConnectionState.Connecting(address)
        try {
            currentGatt = device.connectGatt(
                context,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )
        } catch (e: SecurityException) {
            setError("Bluetooth connect permission is missing.")
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    /**
     * Disconnect from the current peripheral and clear cached state.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        Log.d(TAG, "Disconnect")
        rssiJob?.cancel()
        rssiJob = null
        descriptorWriteQueue.clear()
        isWritingDescriptor = false
        pendingRssiStart = false
        currentGatt?.disconnect()
        currentGatt?.close()
        currentGatt = null
        commandCharacteristic = null
        _rssi.value = null
        _batteryState.value = BatteryState()
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Send a command packet to the control characteristic.
     */
    @SuppressLint("MissingPermission")
    fun sendCommand(packet: ByteArray): Boolean {
        val gatt = currentGatt ?: return false
        val characteristic = commandCharacteristic ?: return false
        if (!hasBluetoothConnectPermission()) {
            setError("Bluetooth connect permission is missing.")
            return false
        }
        return try {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            characteristic.value = packet
            gatt.writeCharacteristic(characteristic)
        } catch (e: SecurityException) {
            setError("Bluetooth connect permission is missing.")
            false
        }
    }

    /**
     * Record an error for observers and log it.
     */
    fun setError(message: String) {
        Log.w(TAG, message)
        _lastError.value = message
    }

    /**
     * Build a scan callback that updates the in-memory device list.
     */
    private fun createScanCallback(): ScanCallback {
        return object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                addOrUpdateDevice(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { addOrUpdateDevice(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                setError("Scan failed with code $errorCode.")
                _scanStatus.value = ScanStatus.Idle
            }
        }
    }

    /**
     * Insert or update a device entry and keep the list sorted by RSSI.
     */
    @SuppressLint("MissingPermission")
    private fun addOrUpdateDevice(result: ScanResult) {
        if (!hasBluetoothScanPermission()) {
            setError("Bluetooth scan permission is missing.")
            return
        }
        if (!hasBluetoothConnectPermission()) {
            setError("Bluetooth connect permission is missing.")
            return
        }
        val device = result.device ?: return
        val name = result.scanRecord?.deviceName ?: device.name ?: "Unknown"
        val bleDevice = BleDevice(
            name = name,
            address = device.address,
            rssi = result.rssi
        )
        discoveredDevices[device.address] = bleDevice
        val sortedDevices = discoveredDevices.values.sortedByDescending { it.rssi }
        _devices.value = sortedDevices
    }

    /**
     * Provide scan filters; empty list means no filtering.
     */
    private fun buildFilters(): List<ScanFilter> {
        return emptyList()
    }

    // Handles GATT lifecycle and characteristic updates.
    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!hasBluetoothConnectPermission()) {
                setError("Bluetooth connect permission is missing.")
                disconnect()
                return
            }
            if (status != GATT_SUCCESS) {
                setError("Connection failed with status $status.")
                disconnect()
                return
            }
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "GATT connected")
                    _connectionState.value = ConnectionState.Connected(gatt.device.address)
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    gatt.requestMtu(185)
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "GATT disconnected")
                    disconnect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != GATT_SUCCESS) {
                setError("Service discovery failed with status $status.")
                return
            }
            Log.d(TAG, "Services discovered")
            val serviceUuid = parseUuid(BleConfig.SERVICE_UUID) ?: return
            val commandUuid = parseUuid(BleConfig.COMMAND_CHAR_UUID) ?: return
            val service = gatt.getService(serviceUuid)
            val characteristic = service?.getCharacteristic(commandUuid)
            if (characteristic == null) {
                setError("Command characteristic not found.")
            }
            commandCharacteristic = characteristic

            val voltageCharacteristic = gatt.getService(voltageServiceUuid)
                ?.getCharacteristic(voltageCharUuid)
            if (voltageCharacteristic != null) {
                enableNotifications(gatt, voltageCharacteristic)
            } else {
                Log.d(TAG, "Voltage characteristic not found.")
            }
            pendingRssiStart = true
            maybeStartRssiUpdates(gatt)
        }

        // API < 33 callback; kept for backwards compatibility.
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != GATT_SUCCESS) {
                return
            }
            handleCharacteristicUpdate(characteristic, characteristic.value)
        }

        // API 33+ callback; provides the value directly.
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status != GATT_SUCCESS) {
                return
            }
            handleCharacteristicUpdate(characteristic, value)
        }

        // API < 33 callback; value read from characteristic.
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleCharacteristicUpdate(characteristic, characteristic.value)
        }

        // API 33+ callback; provides the value directly.
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicUpdate(characteristic, value)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == GATT_SUCCESS) {
                _rssi.value = rssi
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != GATT_SUCCESS) {
                Log.w(TAG, "Descriptor write failed for ${descriptor.uuid} (status $status)")
            }
            isWritingDescriptor = false
            writeNextDescriptor()
        }
    }

    /**
     * Periodically read RSSI while connected.
     */
    @SuppressLint("MissingPermission")
    private fun startRssiUpdates(gatt: BluetoothGatt) {
        rssiJob?.cancel()
        rssiJob = scope.launch {
            while (connectionState.value is ConnectionState.Connected) {
                if (hasBluetoothConnectPermission()) {
                    gatt.readRemoteRssi()
                }
                delay(RSSI_INTERVAL_MS)
            }
        }
    }

    private fun maybeStartRssiUpdates(gatt: BluetoothGatt) {
        if (!pendingRssiStart) {
            return
        }
        if (isWritingDescriptor || descriptorWriteQueue.isNotEmpty()) {
            return
        }
        pendingRssiStart = false
        startRssiUpdates(gatt)
    }

    private fun enqueueDescriptorWrite(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor
    ) {
        descriptorWriteQueue.addLast(DescriptorWrite(gatt, descriptor))
        if (!isWritingDescriptor) {
            writeNextDescriptor()
        }
    }

    private fun writeNextDescriptor() {
        val next = descriptorWriteQueue.pollFirst()
        if (next == null) {
            isWritingDescriptor = false
            currentGatt?.let { maybeStartRssiUpdates(it) }
            return
        }
        isWritingDescriptor = true
        val started = next.gatt.writeDescriptor(next.descriptor)
        if (!started) {
            isWritingDescriptor = false
            writeNextDescriptor()
        }
    }

    /**
     * Read a characteristic value once.
     */
    @SuppressLint("MissingPermission")
    private fun readCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        if (!hasBluetoothConnectPermission()) {
            setError("Bluetooth connect permission is missing.")
            return
        }
        try {
            gatt.readCharacteristic(characteristic)
        } catch (e: SecurityException) {
            setError("Bluetooth connect permission is missing.")
        }
    }

    /**
     * Enable notifications for a characteristic by writing the CCCD.
     */
    @SuppressLint("MissingPermission")
    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        if (!hasBluetoothConnectPermission()) {
            setError("Bluetooth connect permission is missing.")
            return
        }
        try {
            val enabled = gatt.setCharacteristicNotification(characteristic, true)
            if (!enabled) {
                Log.w(TAG, "Failed to enable notifications for ${characteristic.uuid}")
            }
            val descriptor = characteristic.getDescriptor(cccdUuid)
            if (descriptor == null) {
                Log.w(TAG, "CCCD descriptor missing for ${characteristic.uuid}")
                return
            }
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            enqueueDescriptorWrite(gatt, descriptor)
        } catch (e: SecurityException) {
            setError("Bluetooth connect permission is missing.")
        }
    }

    private data class DescriptorWrite(
        val gatt: BluetoothGatt,
        val descriptor: BluetoothGattDescriptor
    )

    /**
     * Route incoming characteristic updates to the appropriate handler.
     */
    private fun handleCharacteristicUpdate(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray?
    ) {
        val payload = value ?: return
        Log.d(
            TAG,
            "GATT update ${characteristic.uuid}: ${payload.size} bytes [${toHex(payload)}]"
        )
        when (characteristic.uuid) {
            voltageCharUuid -> updateBatteryVoltage(payload)
        }
    }

    /**
     * Update the battery voltage from a two-byte little-endian payload.
     */
    private fun updateBatteryVoltage(value: ByteArray) {
        if (value.size < 2) {
            return
        }
        val voltageMv = if (value.size >= 4) {
            val voltageV = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).float
            (voltageV * 1000f).roundToInt()
        } else {
            ((value[1].toInt() and 0xFF) shl 8) or (value[0].toInt() and 0xFF)
        }
        val percent = batteryPercentFromVoltage(voltageMv)
        Log.d(TAG, "Battery voltage update: ${voltageMv}mV (${percent}%)")
        val current = _batteryState.value
        _batteryState.value = current.copy(
            voltageMv = voltageMv,
            percent = percent,
            timestampMs = System.currentTimeMillis()
        )
    }

    /**
     * Check scan-related permissions across API levels.
     */
    private fun hasBluetoothScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check connect-related permissions across API levels.
     */
    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Parse a UUID and emit an error when invalid.
     */
    private fun parseUuid(raw: String): UUID? {
        return try {
            UUID.fromString(raw)
        } catch (e: IllegalArgumentException) {
            setError("Invalid UUID: $raw")
            null
        }
    }

    private fun toHex(bytes: ByteArray): String {
        return bytes.joinToString(separator = " ") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    private fun batteryPercentFromVoltage(voltageMv: Int): Int {
        if (voltageMv <= 0) {
            return 0
        }
        val voltage = voltageMv / 1000.0
        if (BATTERY_FULL_V <= BATTERY_EMPTY_V) {
            return 0
        }
        val percent = when {
            voltage <= BATTERY_EMPTY_V -> 0.0
            voltage >= BATTERY_FULL_V -> 100.0
            else -> (voltage - BATTERY_EMPTY_V) * 100.0 / (BATTERY_FULL_V - BATTERY_EMPTY_V)
        }
        return percent.roundToInt().coerceIn(0, 100)
    }

    private companion object {
        // Logging and timing constants.
        const val TAG = "HovercraftBLE"
        const val SCAN_DURATION_MS = 12_000L
        const val RSSI_INTERVAL_MS = 1_000L
        // Keep in sync with firmware battery thresholds.
        const val BATTERY_FULL_V = 8.40
        const val BATTERY_EMPTY_V = 6.60
    }
}
