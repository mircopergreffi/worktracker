package com.example.workhours.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1F6F5C), onPrimary = Color.White,
    background = Color(0xFFFAFAF8), surface = Color(0xFFFAFAF8),
    onBackground = Color(0xFF1B1C1B), onSurface = Color(0xFF1B1C1B),
    secondary = Color(0xFFB3472F), surfaceVariant = Color(0xFFEDEEEA)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF6FD3B8), onPrimary = Color(0xFF00382C),
    background = Color(0xFF121413), surface = Color(0xFF121413),
    onBackground = Color(0xFFE3E4E1), onSurface = Color(0xFFE3E4E1),
    secondary = Color(0xFFFFB4A1), surfaceVariant = Color(0xFF232624)
)

@Composable
fun AppTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
