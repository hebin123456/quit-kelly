package com.quitkelly.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val KellyGreen = Color(0xFF0F7B63)
val KellyDeepGreen = Color(0xFF0B2E27)
val KellyGold = Color(0xFFC9A227)
val KellyBrightGold = Color(0xFFF2C14E)
val KellyCream = Color(0xFFF7F6F2)

private val LightColors = lightColorScheme(
    primary = KellyGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEEE4),
    onPrimaryContainer = Color(0xFF062B24),
    secondary = KellyGold,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E6BC),
    onSecondaryContainer = Color(0xFF3D2F00),
    background = KellyCream,
    onBackground = Color(0xFF1B1C19),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1B1C19),
    surfaceVariant = Color(0xFFEDEAE1),
    onSurfaceVariant = Color(0xFF4A4A44),
    outline = Color(0xFF8A8A80),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5FD6B8),
    onPrimary = Color(0xFF00382C),
    primaryContainer = Color(0xFF114B3F),
    onPrimaryContainer = Color(0xFFC8F0E4),
    secondary = Color(0xFFE7C66A),
    onSecondary = Color(0xFF3B2F00),
    secondaryContainer = Color(0xFF544600),
    onSecondaryContainer = Color(0xFFF5E6BC),
    background = Color(0xFF0E1513),
    onBackground = Color(0xFFE2E4DF),
    surface = Color(0xFF14201D),
    onSurface = Color(0xFFE2E4DF),
    surfaceVariant = Color(0xFF25322E),
    onSurfaceVariant = Color(0xFFBFC9C4),
    outline = Color(0xFF89938E),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

@Composable
fun KellyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
