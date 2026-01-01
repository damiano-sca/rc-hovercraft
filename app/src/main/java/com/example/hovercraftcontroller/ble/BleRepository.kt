package com.example.hovercraftcontroller.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class BleRepository(private val context: Context) {
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val discoveredDevices = LinkedHashMap<String, BleDevice>()

    private val _scanStatus = MutableStateFlow(ScanStatus.Idle)
    val scanStatus = _scanStatus.asStateFlow()

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _connectionState =
        MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError = _lastError.asStateFlow()

    private val _rssi = MutableStateFlow<Int?>(null)
    val rssi = _rssi.asStateFlow()

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var scanJob: Job? = null

    private var currentGatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var rssiJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startScan() {
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

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (_scanStatus.value != ScanStatus.Scanning) {
            return
        }
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

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
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

    @SuppressLint("MissingPermission")
    fun disconnect() {
        rssiJob?.cancel()
        rssiJob = null
        currentGatt?.disconnect()
        currentGatt?.close()
        currentGatt = null
        commandCharacteristic = null
        _rssi.value = null
        _connectionState.value = ConnectionState.Disconnected
    }

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

    fun setError(message: String) {
        _lastError.value = message
    }

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

    private fun buildFilters(): List<ScanFilter> {
        return emptyList()
    }

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
                    _connectionState.value = ConnectionState.Connected(gatt.device.address)
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    gatt.requestMtu(185)
                    gatt.discoverServices()
                    startRssiUpdates(gatt)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    disconnect()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != GATT_SUCCESS) {
                setError("Service discovery failed with status $status.")
                return
            }
            val serviceUuid = parseUuid(BleConfig.SERVICE_UUID) ?: return
            val commandUuid = parseUuid(BleConfig.COMMAND_CHAR_UUID) ?: return
            val service = gatt.getService(serviceUuid)
            val characteristic = service?.getCharacteristic(commandUuid)
            if (characteristic == null) {
                setError("Command characteristic not found.")
            }
            commandCharacteristic = characteristic
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == GATT_SUCCESS) {
                _rssi.value = rssi
            }
        }
    }

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

    private fun parseUuid(raw: String): UUID? {
        return try {
            UUID.fromString(raw)
        } catch (e: IllegalArgumentException) {
            setError("Invalid UUID: $raw")
            null
        }
    }

    private companion object {
        const val SCAN_DURATION_MS = 12_000L
        const val RSSI_INTERVAL_MS = 1_000L
    }
}
