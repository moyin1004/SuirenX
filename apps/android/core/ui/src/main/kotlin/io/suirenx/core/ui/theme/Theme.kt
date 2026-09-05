package io.suirenx.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
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

@Composable
fun SuirenXTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SuirenXColors,
        typography = Typography(),
        content = content,
    )
}

