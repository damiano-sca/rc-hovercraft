package com.example.hovercraftcontroller.ui.control

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.hovercraftcontroller.ble.ConnectionState
import kotlin.math.roundToInt

@Composable
fun ControlRoute(
    onBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: ControlViewModel = viewModel()
) {
    LockOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ControlScreen(
        state = state,
        onBack = {
            viewModel.disconnect()
            onBack()
        },
        onOpenSettings = onOpenSettings,
        onToggleArm = viewModel::toggleArm,
        onThrottleChange = viewModel::updateThrottle,
        onThrottleRelease = { viewModel.updateThrottle(0f) },
        onRudderChange = viewModel::updateRudder,
        onRudderRelease = viewModel::centerRudder
    )
}

@Composable
private fun LockOrientation(orientation: Int) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(orientation) {
        val previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = orientation
        onDispose {
            activity.requestedOrientation = previousOrientation
        }
    }
}

@Composable
fun ControlScreen(
    state: ControlUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onToggleArm: () -> Unit,
    onThrottleChange: (Float) -> Unit,
    onThrottleRelease: () -> Unit,
    onRudderChange: (Float) -> Unit,
    onRudderRelease: () -> Unit
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
            HeaderBar(
                connectionState = state.connectionState,
                onBack = onBack,
                onOpenSettings = onOpenSettings
            )
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThrottlePanel(
                    modifier = Modifier.weight(1f),
                    value = state.throttle,
                    onValueChange = onThrottleChange,
                    onValueRelease = onThrottleRelease
                )
                CenterPanel(
                    modifier = Modifier.weight(1f),
                    state = state,
                    onToggleArm = onToggleArm
                )
                RudderPanel(
                    modifier = Modifier.weight(1f),
                    value = state.rudder,
                    centerAngle = state.rudderCenter,
                    maxAngle = state.rudderMaxAngle,
                    onValueChange = onRudderChange,
                    onValueRelease = onRudderRelease
                )
            }
        }
    }
}

@Composable
private fun HeaderBar(
    connectionState: ConnectionState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val dotColor = when (connectionState) {
            is ConnectionState.Connected -> Color(0xFF35C46A)
            is ConnectionState.Connecting -> Color(0xFFF0B14A)
            ConnectionState.Disconnected -> MaterialTheme.colorScheme.outline
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Control",
                style = MaterialTheme.typography.headlineMedium
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings"
                )
            }
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.Bluetooth,
                    contentDescription = "Bluetooth"
                )
            }
        }
    }
}

@Composable
private fun StatusStrip(state: ControlUiState) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val rssiValue = state.rssi
        val rssiLabel = when {
            rssiValue == null -> "RSSI --"
            rssiValue >= -60 -> "Good (RSSI $rssiValue dBm)"
            rssiValue >= -75 -> "Average (RSSI $rssiValue dBm)"
            else -> "Weak (RSSI $rssiValue dBm)"
        }
        val rssiColor = when {
            rssiValue == null -> MaterialTheme.colorScheme.outline
            rssiValue >= -60 -> Color(0xFF2FBF71)
            rssiValue >= -75 -> Color(0xFFF2B705)
            else -> Color(0xFFE05454)
        }
        StatusPill(label = rssiLabel, color = rssiColor)
        StatusPill(
            label = "${state.commandRateHz} Hz",
            color = MaterialTheme.colorScheme.tertiary
        )
        val armColor = if (state.isArmed) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline
        }
        StatusPill(
            label = if (state.isArmed) "ARMED" else "SAFE",
            color = armColor
        )
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
private fun ActionRow(
    state: ControlUiState,
    onToggleArm: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onToggleArm,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isArmed) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (state.isArmed) {
                    MaterialTheme.colorScheme.onSecondary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            ),
            border = if (!state.isArmed) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            } else {
                null
            }
        ) {
            Text(text = if (state.isArmed) "DISARM" else "ARM")
        }
    }
}

@Composable
private fun ThrottlePanel(
    modifier: Modifier,
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueRelease: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Throttle", style = MaterialTheme.typography.titleMedium)
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        text = "${(value * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                VerticalSlider(
                    value = value,
                    onValueChange = onValueChange,
                    onValueRelease = onValueRelease
                )
            }
        }
    }
}

@Composable
private fun CenterPanel(
    modifier: Modifier,
    state: ControlUiState,
    onToggleArm: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceAround
        ) {
            StatusStrip(state)
            ActionRow(
                state = state,
                onToggleArm = onToggleArm
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RudderPanel(
    modifier: Modifier,
    value: Float,
    centerAngle: Int,
    maxAngle: Int,
    onValueChange: (Float) -> Unit,
    onValueRelease: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val minAngle = (centerAngle - maxAngle).coerceAtLeast(0)
            val maxAngleBound = (centerAngle + maxAngle).coerceAtMost(180)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Rudder", style = MaterialTheme.typography.titleMedium)
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        text = "${value.roundToInt()} °",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Slider(
                    modifier = Modifier.size(width = 280.dp, height = 72.dp),
                    value = value,
                    onValueChange = onValueChange,
                    onValueChangeFinished = onValueRelease,
                    valueRange = minAngle.toFloat()..maxAngleBound.toFloat(),
                    track = {
                        Box(
                            Modifier
                                .height(48.dp)
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                    RoundedCornerShape(999.dp)
                                )
                        )
                    },
                    thumb = {
                        Box(
                            Modifier
                                .size(72.dp)
                                .background(MaterialTheme.colorScheme.onSurface, CircleShape)
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.rotate(-90f)) {
        Slider(
            modifier = Modifier.size(width = 240.dp, height = 72.dp),
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueRelease,
            track = {
                Box(
                    Modifier
                        .height(48.dp)
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                            RoundedCornerShape(999.dp)
                        )
                )
            },
            thumb = {
                Box(
                    Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.onSurface, CircleShape)
                )
            }
        )
    }
}
