package com.example.hovercraftcontroller.ui.control

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onToggleArm = viewModel::toggleArm,
        onStop = viewModel::stop,
        onThrottleChange = viewModel::updateThrottle,
        onTurnChange = viewModel::updateTurn
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
    onStop: () -> Unit,
    onThrottleChange: (Float) -> Unit,
    onTurnChange: (Float) -> Unit
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
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeaderBar(state = state, onBack = onBack, onOpenSettings = onOpenSettings)
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThrottlePanel(
                    modifier = Modifier.weight(1f),
                    value = state.throttle,
                    onValueChange = onThrottleChange
                )
                CenterPanel(
                    modifier = Modifier.weight(1f),
                    state = state,
                    onToggleArm = onToggleArm,
                    onStop = onStop
                )
                TurnPanel(
                    modifier = Modifier.weight(1f),
                    value = state.turn,
                    onValueChange = onTurnChange
                )
            }
        }
    }
}

@Composable
private fun HeaderBar(
    state: ControlUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Control",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = state.connectionLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onOpenSettings) {
                Text(text = "Settings")
            }
            TextButton(onClick = onBack) {
                Text(text = "Back to Scan")
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
        StatusPill(label = "RSSI ${state.rssi} dBm", color = MaterialTheme.colorScheme.secondary)
        StatusPill(
            label = "${state.commandRateHz} Hz",
            color = MaterialTheme.colorScheme.tertiary
        )
        val armColor = when {
            state.isStopped -> MaterialTheme.colorScheme.error
            state.isArmed -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outline
        }
        StatusPill(
            label = when {
                state.isStopped -> "STOPPED"
                state.isArmed -> "ARMED"
                else -> "SAFE"
            },
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
    onToggleArm: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            modifier = Modifier.weight(1f),
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
            Text(text = if (state.isArmed) "Disarm" else "Arm")
        }
        Button(
            modifier = Modifier.weight(1f),
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text(text = "STOP", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ThrottlePanel(
    modifier: Modifier,
    value: Float,
    onValueChange: (Float) -> Unit
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
                    onValueChange = onValueChange
                )
            }
        }
    }
}

@Composable
private fun CenterPanel(
    modifier: Modifier,
    state: ControlUiState,
    onToggleArm: () -> Unit,
    onStop: () -> Unit
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
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Status", style = MaterialTheme.typography.titleMedium)
                StatusStrip(state = state)
            }
            ActionRow(state = state, onToggleArm = onToggleArm, onStop = onStop)
        }
    }
}

@Composable
private fun TurnPanel(
    modifier: Modifier,
    value: Float,
    onValueChange: (Float) -> Unit
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
                Text(text = "Turn", style = MaterialTheme.typography.titleMedium)
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
            Spacer(modifier = Modifier.height(18.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = -1f..1f
            )
        }
    }
}

@Composable
private fun VerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Box(
        modifier = Modifier
            .height(260.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Slider(
            modifier = Modifier
                .fillMaxWidth()
                .rotate(-90f),
            value = value,
            onValueChange = onValueChange,
            valueRange = -1f..1f
        )
    }
}
