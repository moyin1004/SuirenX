package io.suirenx.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import io.suirenx.core.model.ThemeMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SuirenXColors = lightColorScheme(
    primary = Color(0xFF191919),
    onPrimary = Color.White,
    primaryContainer = Color.White,
    onPrimaryContainer = Color(0xFF191919),
    secondary = Color(0xFF82E600),
    onSecondary = Color(0xFF172000),
    secondaryContainer = Color(0xFFE4F7C4),
    onSecondaryContainer = Color(0xFF223300),
    background = Color(0xFFF5F5F3),
    onBackground = Color(0xFF191919),
    surface = Color.White,
    onSurface = Color(0xFF191919),
    surfaceVariant = Color(0xFFEFEFED),
    onSurfaceVariant = Color(0xFF777773),
    outline = Color(0xFFB9B9B3),
    error = Color(0xFFBA1A1A),
)

private val SuirenXDarkColors = darkColorScheme(
    primary = Color(0xFFE4E4DF),
    onPrimary = Color(0xFF20201D),
    primaryContainer = Color(0xFF343430),
    onPrimaryContainer = Color(0xFFF4F4EE),
    secondary = Color(0xFFB5F56D),
    onSecondary = Color(0xFF203000),
    secondaryContainer = Color(0xFF304B17),
    onSecondaryContainer = Color(0xFFD5FF9B),
    background = Color(0xFF121310),
    onBackground = Color(0xFFE7E8E0),
    surface = Color(0xFF1B1C19),
    onSurface = Color(0xFFE7E8E0),
    surfaceVariant = Color(0xFF30312D),
    onSurfaceVariant = Color(0xFFC3C5B9),
    outline = Color(0xFF8D9085),
    error = Color(0xFFFFB4AB),
)

@Composable
fun SuirenXTheme(themeMode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MaterialTheme(
        colorScheme = if (dark) SuirenXDarkColors else SuirenXColors,
        typography = Typography(),
        content = content,
    )
}
