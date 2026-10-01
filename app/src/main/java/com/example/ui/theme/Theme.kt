package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NesDarkColorScheme = darkColorScheme(
    primary = NesPrimaryCyan,
    onPrimary = Color.Black,
    secondary = NesSecondaryRuby,
    onSecondary = Color.White,
    tertiary = NesAccentGold,
    onTertiary = Color.Black,
    background = NesDarkBg,
    onBackground = NesTextPrimary,
    surface = NesSurface,
    onSurface = NesTextPrimary,
    surfaceVariant = NesSurfaceVariant,
    onSurfaceVariant = NesTextSecondary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Android TV is best in immersive dark mode
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NesDarkColorScheme,
        typography = Typography,
        content = content
    )
}
