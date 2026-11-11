package com.dtp.spin.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DjColors = darkColorScheme(
    primary = Color(0xFF7CDBFF),
    onPrimary = Color(0xFF06222E),
    secondary = Color(0xFFFFB74D),
    surface = Color(0xFF14181D),
    onSurface = Color(0xFFE8EDF2),
    surfaceVariant = Color(0xFF1E242B),
    onSurfaceVariant = Color(0xFFAEB9C2),
    background = Color(0xFF0E1114),
    onBackground = Color(0xFFE8EDF2),
    error = Color(0xFFFF8A80),
)

val DeckAColor = Color(0xFF7CDBFF)
val DeckBColor = Color(0xFFFFB74D)
val WavePlayed = Color(0xFF3A4550)
val CueColor = Color(0xFF7CFF9B)

@Composable
fun DJTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DjColors,
        typography = Typography(),
        content = content,
    )
}
