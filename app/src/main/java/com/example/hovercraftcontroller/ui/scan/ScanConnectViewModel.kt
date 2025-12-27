package com.example.hovercraftcontroller.ui.scan

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScanConnectViewModel(application: Application) : AndroidViewModel(application) {
    private val bluetoothManager =
        application.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val discoveredDevices = LinkedHashMap<String, BleDevice>()

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var scanJob: Job? = null

    private var currentGatt: BluetoothGatt? = null

    private val _uiState = MutableStateFlow(ScanConnectUiState())
    val uiState = _uiState.asStateFlow()

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
        if (_uiState.value.scanStatus == ScanStatus.Scanning) {
            return
        }

        discoveredDevices.clear()
        _uiState.update {
            it.copy(
                scanStatus = ScanStatus.Scanning,
                devices = emptyList(),
                lastError = null
            )
        }

        val bleScanner = adapter.bluetoothLeScanner
        if (bleScanner == null) {
            setError("BLE scanner is not available.")
            _uiState.update { it.copy(scanStatus = ScanStatus.Idle) }
            return
        }
        scanner = bleScanner
        val callback = createScanCallback()
        scanCallback = callback

        try {
            bleScanner.startScan(callback)
        } catch (e: IllegalStateException) {
            setError("Unable to start scan: ${e.message}")
            _uiState.update { it.copy(scanStatus = ScanStatus.Idle) }
            return
        }

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            delay(SCAN_DURATION_MS)
            stopScan()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (_uiState.value.scanStatus != ScanStatus.Scanning) {
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
        _uiState.update { it.copy(scanStatus = ScanStatus.Idle) }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            setError("Bluetooth is not available on this device.")
            return
        }
        stopScan()
        currentGatt?.close()
        currentGatt = null

        val device = adapter.getRemoteDevice(address)
        _uiState.update { it.copy(connectionState = ConnectionState.Connecting(address)) }
        currentGatt = device.connectGatt(
            getApplication(),
            false,
            gattCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        currentGatt?.disconnect()
        currentGatt?.close()
        currentGatt = null
        _uiState.update { it.copy(connectionState = ConnectionState.Disconnected) }
    }

    fun onPermissionDenied() {
        setError("Bluetooth permissions are required to scan.")
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        disconnect()
    }

    private fun setError(message: String) {
        _uiState.update { it.copy(lastError = message) }
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
                _uiState.update { it.copy(scanStatus = ScanStatus.Idle) }
            }
        }
    }

    private fun addOrUpdateDevice(result: ScanResult) {
        val device = result.device ?: return
        val name = device.name ?: "Unknown"
        val bleDevice = BleDevice(
            name = name,
            address = device.address,
            rssi = result.rssi
        )
        discoveredDevices[device.address] = bleDevice
        val sortedDevices = discoveredDevices.values.sortedByDescending { it.rssi }
        _uiState.update { it.copy(devices = sortedDevices) }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _uiState.update {
                        it.copy(connectionState = ConnectionState.Connected(gatt.device.address))
                    }
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                    gatt.requestMtu(185)
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    currentGatt?.close()
                    currentGatt = null
                    _uiState.update { it.copy(connectionState = ConnectionState.Disconnected) }
                }
            }
        }
    }

    private companion object {
        const val SCAN_DURATION_MS = 12_000L
    }
}
