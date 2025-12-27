package com.example.hovercraftcontroller.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme

private val LightColorScheme = lightColorScheme(
    primary = DeepTeal,
    onPrimary = Color.White,
    secondary = Ember,
    onSecondary = Color(0xFF2B1400),
    tertiary = Color(0xFF5B7C8F),
    background = Sand,
    onBackground = Ink,
    surface = Color(0xFFFFFBF6),
    onSurface = Ink,
    surfaceVariant = Driftwood,
    onSurfaceVariant = Ink,
    outline = OutlineWarm
)

private val DarkColorScheme = darkColorScheme(
    primary = TealGlow,
    onPrimary = Color(0xFF003737),
    secondary = EmberLight,
    onSecondary = Color(0xFF3B1B00),
    tertiary = Color(0xFF96B9CC),
    background = Night,
    onBackground = Fog,
    surface = NightSurface,
    onSurface = Fog,
    surfaceVariant = Color(0xFF2B2C2E),
    onSurfaceVariant = Fog,
    outline = Color(0xFF6F6B66)
)

@Composable
fun HovercraftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = HovercraftTypography,
        content = content
    )
}
