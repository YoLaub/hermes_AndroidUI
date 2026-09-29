package com.example.hermes.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = HermesPrimary,
    onPrimary = Color.Black,
    primaryContainer = HermesPrimaryContainer,
    onPrimaryContainer = HermesOnPrimaryContainer,
    secondary = HermesSecondary,
    onSecondary = Color.Black,
    secondaryContainer = HermesSecondaryContainer,
    onSecondaryContainer = HermesOnSecondaryContainer,
    tertiary = HermesTertiary,
    onTertiary = Color.Black,
    background = OnyxDarkBackground,
    onBackground = HermesTextPrimary,
    surface = OnyxDarkSurface,
    onSurface = HermesTextPrimary,
    surfaceVariant = OnyxDarkSurfaceVariant,
    onSurfaceVariant = HermesTextSecondary,
    outline = OnyxBorder,
    error = HermesError,
    errorContainer = HermesErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0969DA),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF4FF),
    onPrimaryContainer = Color(0xFF001F3F),
    secondary = Color(0xFF1A7F37),
    onSecondary = Color.White,
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1F2328),
    surface = Color.White,
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEAEFF2),
    onSurfaceVariant = Color(0xFF656D76),
    outline = Color(0xFFD0D7DE),
    error = Color(0xFFCF222E)
)

@Composable
fun HermesTheme(
    darkTheme: Boolean = true, // Default to sleek dark mode
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
