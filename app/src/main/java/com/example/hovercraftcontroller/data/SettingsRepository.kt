package com.example.hovercraftcontroller.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.hovercraftcontroller.ui.settings.SettingsUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("hovercraft_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val commandRate = intPreferencesKey("command_rate_hz")
        val sensitivity = floatPreferencesKey("sensitivity")
        val deadZone = floatPreferencesKey("dead_zone")
        val invertThrottle = booleanPreferencesKey("invert_throttle")
        val invertRudder = booleanPreferencesKey("invert_rudder")
        val debugLogging = booleanPreferencesKey("debug_logging")
    }

    val settingsFlow: Flow<SettingsUiState> = context.dataStore.data.map { prefs ->
        SettingsUiState(
            commandRateHz = prefs[Keys.commandRate] ?: 60,
            sensitivity = prefs[Keys.sensitivity] ?: 1.0f,
            deadZone = prefs[Keys.deadZone] ?: 0.05f,
            invertThrottle = prefs[Keys.invertThrottle] ?: false,
            invertRudder = prefs[Keys.invertRudder] ?: false,
            debugLogging = prefs[Keys.debugLogging] ?: false
        )
    }

    suspend fun setCommandRate(rate: Int) {
        context.dataStore.edit { it[Keys.commandRate] = rate }
    }

    suspend fun setSensitivity(value: Float) {
        context.dataStore.edit { it[Keys.sensitivity] = value }
    }

    suspend fun setDeadZone(value: Float) {
        context.dataStore.edit { it[Keys.deadZone] = value }
    }

    suspend fun setInvertThrottle(enabled: Boolean) {
        context.dataStore.edit { it[Keys.invertThrottle] = enabled }
    }

    suspend fun setInvertRudder(enabled: Boolean) {
        context.dataStore.edit { it[Keys.invertRudder] = enabled }
    }

    suspend fun setDebugLogging(enabled: Boolean) {
        context.dataStore.edit { it[Keys.debugLogging] = enabled }
    }
}
