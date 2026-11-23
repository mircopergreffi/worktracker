package com.example.workhours.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Light: the colors of the app icon. Beige dial as background, black as the main color
 * (IN button, like the black pushers), red for OUT (like the red pusher), steel for outlines.
 */
private val Light = lightColorScheme(
    primary = Color(0xFF1F1F1F), onPrimary = Color(0xFFEADFC3),
    primaryContainer = Color(0xFFD9CDB0), onPrimaryContainer = Color(0xFF1F1F1F),
    secondary = Color(0xFFC0392B), onSecondary = Color(0xFFF7F1E3),
    secondaryContainer = Color(0xFFE8C9C2), onSecondaryContainer = Color(0xFF5B1A13),
    tertiary = Color(0xFFBCC1C5), onTertiary = Color(0xFF1F1F1F),
    background = Color(0xFFEADFC3), onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFEADFC3), onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFF4EDDC), onSurfaceVariant = Color(0xFF4A4A46),
    outline = Color(0xFF8F9499), outlineVariant = Color(0xFFBCC1C5),
    error = Color(0xFFC0392B), onError = Color(0xFFF7F1E3),
    surfaceTint = Color(0xFFEADFC3),
    surfaceContainerLowest = Color(0xFFF9F4E8), surfaceContainerLow = Color(0xFFF1E9D6),
    surfaceContainer = Color(0xFFF4EDDC), surfaceContainerHigh = Color(0xFFF6F0E2),
    surfaceContainerHighest = Color(0xFFF8F3E7),
    inverseSurface = Color(0xFF1F1F1F), inverseOnSurface = Color(0xFFEADFC3)
)

/**
 * Dark: a black Hanhart dial. Matte black background, aged cream (lume) for text and the main color,
 * red accent for OUT, steel gray for outlines.
 */
private val Dark = darkColorScheme(
    primary = Color(0xFFEADFC3), onPrimary = Color(0xFF1A1A18),
    primaryContainer = Color(0xFF3A372E), onPrimaryContainer = Color(0xFFEADFC3),
    secondary = Color(0xFFD9473A), onSecondary = Color(0xFF121314),
    secondaryContainer = Color(0xFF5A201A), onSecondaryContainer = Color(0xFFFFDAD4),
    tertiary = Color(0xFF8A9096), onTertiary = Color(0xFF121314),
    background = Color(0xFF121314), onBackground = Color(0xFFEADFC3),
    surface = Color(0xFF121314), onSurface = Color(0xFFEADFC3),
    surfaceVariant = Color(0xFF1F2123), onSurfaceVariant = Color(0xFFB5AFA0),
    outline = Color(0xFF6B7075), outlineVariant = Color(0xFF3B3E41),
    error = Color(0xFFE0584B), onError = Color(0xFF1A0000),
    surfaceTint = Color(0xFF121314),
    surfaceContainerLowest = Color(0xFF0D0E0F), surfaceContainerLow = Color(0xFF17181A),
    surfaceContainer = Color(0xFF1B1D1F), surfaceContainerHigh = Color(0xFF232527),
    surfaceContainerHighest = Color(0xFF2B2D30),
    inverseSurface = Color(0xFFEADFC3), inverseOnSurface = Color(0xFF1F1F1F)
)

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
