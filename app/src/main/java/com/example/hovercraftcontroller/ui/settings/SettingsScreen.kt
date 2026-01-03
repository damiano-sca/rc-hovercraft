package com.example.hovercraftcontroller.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

private val CommandRates = listOf(50, 60, 80, 100)

@Composable
fun SettingsRoute(
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onCommandRateChange = viewModel::setCommandRate,
        onDeadZoneChange = viewModel::setDeadZone,
        onInvertRudderChange = viewModel::setInvertRudder,
        onRudderCenterChange = viewModel::setRudderCenter,
        onRudderMaxAngleChange = viewModel::setRudderMaxAngle,
        onRestoreRudderDefaults = viewModel::resetRudderDefaults
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onCommandRateChange: (Int) -> Unit,
    onDeadZoneChange: (Float) -> Unit,
    onInvertRudderChange: (Boolean) -> Unit,
    onRudderCenterChange: (Int) -> Unit,
    onRudderMaxAngleChange: (Int) -> Unit,
    onRestoreRudderDefaults: () -> Unit
) {
    val backgroundBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.background,
            MaterialTheme.colorScheme.surfaceVariant
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SettingsHeader(onBack = onBack)
        CommandRateCard(
            selectedRate = state.commandRateHz,
            onRateSelected = onCommandRateChange
        )
        SliderCard(
            title = "Dead zone",
            valueLabel = "${(state.deadZone * 100).roundToInt()}%",
            value = state.deadZone,
            valueRange = 0f..0.2f,
            onValueChange = onDeadZoneChange
        )
        RudderConfigCard(
            centerValue = state.rudderCenter,
            maxAngle = state.rudderMaxAngle,
            onCenterChange = onRudderCenterChange,
            onMaxAngleChange = onRudderMaxAngleChange,
            onRestoreDefaults = onRestoreRudderDefaults
        )
        ToggleCard(
            title = "Rudder inversion",
            rows = listOf(
                ToggleRow(
                    label = "Invert rudder",
                    checked = state.invertRudder,
                    onCheckedChange = onInvertRudderChange
                )
            )
        )
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Tune the hovercraft response",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onBack) {
            Text(text = "Back")
        }
    }
}

@Composable
private fun CommandRateCard(
    selectedRate: Int,
    onRateSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Command rate (Hz)",
                style = MaterialTheme.typography.titleMedium
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CommandRates.forEach { rate ->
                        val isSelected = rate == selectedRate
                        if (isSelected) {
                            Button(
                                modifier = Modifier.padding(horizontal = 5.dp),
                                onClick = { onRateSelected(rate) }
                            ) {
                                Text(text = "$rate")
                            }
                        } else {
                            OutlinedButton(
                                modifier = Modifier.padding(horizontal = 5.dp),
                                onClick = { onRateSelected(rate) }
                            ) {
                                Text(text = "$rate")
                            }
                        }
                    }
                }

            }
        }
    }
}

@Composable
private fun CommandRateRow(
    rates: List<Int>,
    selectedRate: Int,
    onRateSelected: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        rates.forEach { rate ->
            val isSelected = rate == selectedRate
            if (isSelected) {
                Button(onClick = { onRateSelected(rate) }) {
                    Text(text = "$rate")
                }
            } else {
                OutlinedButton(onClick = { onRateSelected(rate) }) {
                    Text(text = "$rate")
                }
            }
        }
    }
}

@Composable
private fun SliderCard(
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange
            )
        }
    }
}

private data class ToggleRow(
    val label: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit
)

@Composable
private fun ToggleCard(
    title: String,
    rows: List<ToggleRow>
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            rows.forEach { row ->
                ToggleRowItem(row = row)
            }
        }
    }
}

@Composable
private fun ToggleRowItem(row: ToggleRow) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = row.label, style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = row.checked,
            onCheckedChange = row.onCheckedChange
        )
    }
}

@Composable
private fun RudderConfigCard(
    centerValue: Int,
    maxAngle: Int,
    onCenterChange: (Int) -> Unit,
    onMaxAngleChange: (Int) -> Unit,
    onRestoreDefaults: () -> Unit
) {
    val centerTextState = rememberSaveable { mutableStateOf(centerValue.toString()) }
    val maxTextState = rememberSaveable { mutableStateOf(maxAngle.toString()) }

    LaunchedEffect(centerValue) {
        centerTextState.value = centerValue.toString()
    }
    LaunchedEffect(maxAngle) {
        maxTextState.value = maxAngle.toString()
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "Rudder range", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = centerTextState.value,
                    onValueChange = { text ->
                        val sanitized = text.filter { it.isDigit() }
                        centerTextState.value = sanitized
                        sanitized.toIntOrNull()?.let { onCenterChange(it) }
                    },
                    label = { Text(text = "Center angle") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = maxTextState.value,
                    onValueChange = { text ->
                        val sanitized = text.filter { it.isDigit() }
                        maxTextState.value = sanitized
                        sanitized.toIntOrNull()?.let { onMaxAngleChange(it) }
                    },
                    label = { Text(text = "Max angle delta") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onRestoreDefaults) {
                    Text(text = "Restore defaults")
                }
            }
        }
    }
}
