package com.example.hovercraftcontroller

import android.app.Application
import com.example.hovercraftcontroller.ble.BleRepository
import com.example.hovercraftcontroller.data.SettingsRepository

class HovercraftApplication : Application() {
    lateinit var bleRepository: BleRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        bleRepository = BleRepository(this)
        settingsRepository = SettingsRepository(this)
    }
}
