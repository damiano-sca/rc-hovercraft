package com.example.hovercraftcontroller

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.hovercraftcontroller.ui.control.ControlRoute
import com.example.hovercraftcontroller.ui.scan.ScanConnectRoute
import com.example.hovercraftcontroller.ui.settings.SettingsRoute

private enum class AppScreen {
    Scan,
    Control,
    Settings
}

@Composable
fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf(AppScreen.Scan) }
    // This is a simple back stack. A more robust solution would use a proper navigation library.
    var previousScreen by rememberSaveable { mutableStateOf(AppScreen.Scan) }

    fun navigate(newScreen: AppScreen) {
        if (newScreen != screen) {
            previousScreen = screen
            screen = newScreen
        }
    }

    when (screen) {
        AppScreen.Scan -> ScanConnectRoute(
            onContinue = { navigate(AppScreen.Control) },
            onOpenSettings = { navigate(AppScreen.Settings) }
        )
        AppScreen.Control -> ControlRoute(
            onBack = { navigate(AppScreen.Scan) },
            onOpenSettings = { navigate(AppScreen.Settings) }
        )
        AppScreen.Settings -> SettingsRoute(onBack = { screen = previousScreen })
    }
}
