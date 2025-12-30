package com.example.hovercraftcontroller.ui.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hovercraftcontroller.HovercraftApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ScanConnectViewModel(application: Application) : AndroidViewModel(application) {
    private val bleRepository =
        (application as HovercraftApplication).bleRepository

    val uiState = combine(
        bleRepository.scanStatus,
        bleRepository.devices,
        bleRepository.connectionState,
        bleRepository.lastError
    ) { scanStatus, devices, connectionState, lastError ->
        ScanConnectUiState(
            scanStatus = scanStatus,
            devices = devices,
            connectionState = connectionState,
            lastError = lastError
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ScanConnectUiState()
    )

    fun startScan() {
        bleRepository.startScan()
    }

    fun stopScan() {
        bleRepository.stopScan()
    }

    fun connect(address: String) {
        bleRepository.connect(address)
    }

    fun disconnect() {
        bleRepository.disconnect()
    }

    fun onPermissionDenied() {
        bleRepository.setError("Bluetooth permissions are required to scan.")
    }
}
