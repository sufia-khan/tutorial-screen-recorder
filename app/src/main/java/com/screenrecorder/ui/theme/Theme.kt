package com.screenrecorder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.screenrecorder.manager.ThemeMode

private val AppDarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF2A2A2A),
    onPrimaryContainer = Color.White,
    secondary = Color.White,
    onSecondary = Color.Black,
    tertiary = Color(0xFFE0E0E0),
    background = Color.Black,
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFA0A0A0),
    outline = Color(0xFF333333),
    error = Color.White,
)

private val AppLightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color.Black,
    secondary = Color.Black,
    onSecondary = Color.White,
    tertiary = Color(0xFF424242),
    background = Color(0xFFF8F8F8),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEEEEE),
    onBackground = Color.Black,
    onSurface = Color.Black,
    onSurfaceVariant = Color(0xFF616161),
    outline = Color(0xFFE0E0E0),
    error = Color.Black,
)

@Composable
fun ScreenRecorderTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDark) AppDarkColors else AppLightColors,
        content = content
    )
}
