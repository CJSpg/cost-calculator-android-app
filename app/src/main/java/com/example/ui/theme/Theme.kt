package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = WellnessGreenLight,
    onPrimary = Color.Black,
    secondary = WellnessGold,
    onSecondary = Color.Black,
    tertiary = WellnessAmber,
    background = DarkBg,
    surface = DarkSurface,
    onBackground = Color(0xFFE2E3E3),
    onSurface = Color(0xFFE2E3E3)
)

private val LightColorScheme = lightColorScheme(
    primary = WellnessGreen,
    onPrimary = Color.White,
    secondary = WellnessGold,
    onSecondary = Color.Black,
    tertiary = WellnessAmber,
    background = WarmBgLight,
    surface = WarmSurfaceLight,
    onBackground = Color(0xFF2E3131),
    onSurface = Color(0xFF2E3131)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Force our custom beautiful branding colors
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
