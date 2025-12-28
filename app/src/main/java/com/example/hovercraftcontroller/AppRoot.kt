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

    when (screen) {
        AppScreen.Scan -> ScanConnectRoute(onContinue = { screen = AppScreen.Control })
        AppScreen.Control -> ControlRoute(
            onBack = { screen = AppScreen.Scan },
            onOpenSettings = { screen = AppScreen.Settings }
        )
        AppScreen.Settings -> SettingsRoute(onBack = { screen = AppScreen.Control })
    }
}
