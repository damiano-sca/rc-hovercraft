package com.example.hovercraftcontroller.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hovercraftcontroller.ble.BleDevice
import com.example.hovercraftcontroller.ble.ConnectionState
import com.example.hovercraftcontroller.ble.ScanStatus

@Composable
fun ScanConnectRoute(
    onContinue: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: ScanConnectViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissions = remember { requiredPermissions() }
    val hasPermissions = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var pendingScan by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted && pendingScan) {
            viewModel.startScan()
        } else if (!granted) {
            viewModel.onPermissionDenied()
        }
        pendingScan = false
    }

    val requestPermissions: () -> Unit = {
        permissionLauncher.launch(permissions.toTypedArray())
    }

    val requestScan: () -> Unit = {
        if (hasPermissions) {
            viewModel.startScan()
        } else {
            pendingScan = true
            requestPermissions()
        }
    }

    ScanConnectScreen(
        state = state,
        hasPermissions = hasPermissions,
        onRequestPermissions = requestPermissions,
        onScanClick = requestScan,
        onStopScan = viewModel::stopScan,
        onConnect = viewModel::connect,
        onDisconnect = viewModel::disconnect,
        onContinue = onContinue,
        onOpenSettings = onOpenSettings
    )
}

@Composable
fun ScanConnectScreen(
    state: ScanConnectUiState,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onScanClick: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderSection(state = state, onOpenSettings = onOpenSettings)
            if (!hasPermissions) {
                PermissionsCard(onRequestPermissions = onRequestPermissions)
            }
            ControlRow(
                state = state,
                onScanClick = onScanClick,
                onStopScan = onStopScan,
                onDisconnect = onDisconnect
            )

            AnimatedVisibility(state.scanStatus == ScanStatus.Scanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            state.lastError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            DeviceList(
                modifier = Modifier.weight(1f, fill = true),
                state = state,
                onConnect = onConnect,
                onDisconnect = onDisconnect
            )

            if (state.connectionState is ConnectionState.Connected) {
            Box(modifier = Modifier.fillMaxWidth().padding(0.dp, 24.dp)) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .align(Alignment.Center),
                        onClick = onContinue
                    ) {
                        Text(text = "Rock & Roll")
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderSection(state: ScanConnectUiState, onOpenSettings: () -> Unit) {

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scan & Connect",
                style = MaterialTheme.typography.headlineMedium
            )
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings"
                )
            }
        }
        StatusRow(state = state)
    }
}

@Composable
private fun PermissionsCard(onRequestPermissions: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Bluetooth permissions required",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Grant Bluetooth Scan and Connect to discover devices.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onRequestPermissions) {
                Text(text = "Grant permissions")
            }
        }
    }
}

@Composable
private fun StatusRow(state: ScanConnectUiState) {
    val connectionLabel = when (val connection = state.connectionState) {
        is ConnectionState.Connected -> "Connected to ${connection.address}"
        is ConnectionState.Connecting -> "Connecting to ${connection.address}"
        ConnectionState.Disconnected -> "Disconnected"
    }

    val statusColor = when (state.connectionState) {
        is ConnectionState.Connected -> MaterialTheme.colorScheme.secondary
        is ConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary
        ConnectionState.Disconnected -> MaterialTheme.colorScheme.outline
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusPill(label = connectionLabel, color = statusColor)
        if (state.scanStatus == ScanStatus.Scanning) {
            StatusPill(label = "Scanning", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StatusPill(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        contentColor = color,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ControlRow(
    state: ScanConnectUiState,
    onScanClick: () -> Unit,
    onStopScan: () -> Unit,
    onDisconnect: () -> Unit
) {
    val scanLabel = when {
        state.scanStatus == ScanStatus.Scanning -> "Stop scan"
        state.devices.isEmpty() -> "Scan"
        else -> "Rescan"
    }
    val hasConnection = state.connectionState is ConnectionState.Connected ||
        state.connectionState is ConnectionState.Connecting

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            modifier = Modifier.weight(1f),
            onClick = { if (state.scanStatus == ScanStatus.Scanning) onStopScan() else onScanClick() }
        ) {
            Text(text = scanLabel)
        }
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = onDisconnect,
            enabled = hasConnection
        ) {
            Text(text = "Disconnect")
        }
    }
}

@Composable
private fun DeviceList(
    modifier: Modifier = Modifier,
    state: ScanConnectUiState,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Devices", style = MaterialTheme.typography.titleMedium)
        }

        if (state.devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize()) {
                EmptyState()
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = true),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.devices, key = { it.address }) { device ->
                    DeviceRow(
                        device = device,
                        connectionState = state.connectionState,
                        onConnect = onConnect,
                        onDisconnect = onDisconnect
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                text = "No devices yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tap Scan to discover nearby hovercraft controllers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceRow(
    device: BleDevice,
    connectionState: ConnectionState,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    val isConnected = connectionState is ConnectionState.Connected &&
        connectionState.address == device.address
    val isConnecting = connectionState is ConnectionState.Connecting &&
        connectionState.address == device.address

    val buttonLabel = when {
        isConnected -> "Disconnect"
        isConnecting -> "Connecting"
        else -> "Connect"
    }

    val buttonEnabled = !isConnecting
    val action = if (isConnected) {
        onDisconnect
    } else {
        { onConnect(device.address) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isConnecting && !isConnected) { onConnect(device.address) },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = action,
                    enabled = buttonEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isConnected) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Text(text = buttonLabel)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RssiBadge(rssi = device.rssi)
                Text(
                    text = "RSSI ${device.rssi} dBm",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RssiBadge(rssi: Int) {
    val strengthColor = when {
        rssi >= -55 -> MaterialTheme.colorScheme.secondary
        rssi >= -70 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        color = strengthColor.copy(alpha = 0.16f),
        contentColor = strengthColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            text = "Signal",
            style = MaterialTheme.typography.labelMedium
        )
    }
}

private fun requiredPermissions(): List<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}
