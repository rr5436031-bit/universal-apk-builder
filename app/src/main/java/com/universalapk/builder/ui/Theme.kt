package com.universalapk.builder.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Brand {
    val success = Color(0xFF6FCF97)
    val warning = Color(0xFFF2C14E)
}

private val scheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF0B1B33),
    secondary = Color(0xFF9AA7BA),
    background = Color(0xFF1C1E22),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF25282D),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF2E3237),
    onSurfaceVariant = Color(0xFF9AA0A8),
    error = Color(0xFFEF7B7B),
    outline = Color(0xFF3A3F46)
)

@Composable
fun UabTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(),
        shapes = Shapes(medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(24.dp)),
        content = content
    )
}
