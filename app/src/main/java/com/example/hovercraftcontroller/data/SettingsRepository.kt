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
        val deadZone = floatPreferencesKey("dead_zone")
        val invertRudder = booleanPreferencesKey("invert_rudder")
        val rudderCenter = intPreferencesKey("rudder_center")
        val rudderMaxAngle = intPreferencesKey("rudder_max_angle")
    }

    val settingsFlow: Flow<SettingsUiState> = context.dataStore.data.map { prefs ->
        SettingsUiState(
            commandRateHz = prefs[Keys.commandRate] ?: 60,
            deadZone = prefs[Keys.deadZone] ?: 0.05f,
            invertRudder = prefs[Keys.invertRudder] ?: false,
            rudderCenter = prefs[Keys.rudderCenter] ?: 90,
            rudderMaxAngle = prefs[Keys.rudderMaxAngle] ?: 70
        )
    }

    suspend fun setCommandRate(rate: Int) {
        context.dataStore.edit { it[Keys.commandRate] = rate }
    }

    suspend fun setDeadZone(value: Float) {
        context.dataStore.edit { it[Keys.deadZone] = value }
    }

    suspend fun setInvertRudder(enabled: Boolean) {
        context.dataStore.edit { it[Keys.invertRudder] = enabled }
    }

    suspend fun setRudderCenter(value: Int) {
        context.dataStore.edit { it[Keys.rudderCenter] = value }
    }

    suspend fun setRudderMaxAngle(value: Int) {
        context.dataStore.edit { it[Keys.rudderMaxAngle] = value }
    }

    suspend fun resetRudderDefaults() {
        context.dataStore.edit {
            it[Keys.rudderCenter] = 90
            it[Keys.rudderMaxAngle] = 70
        }
    }
}
