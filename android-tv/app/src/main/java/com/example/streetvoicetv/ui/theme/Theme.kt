package com.example.streetvoicetv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// StreetVoice brand-inspired colors
val SvOrange = Color(0xFFFF6B35)
val SvDarkBackground = Color(0xFF121212)
val SvSurfaceDark = Color(0xFF1E1E1E)
val SvSurfaceVariant = Color(0xFF2A2A2A)
val SvOnSurface = Color(0xFFE0E0E0)
val SvOnSurfaceVariant = Color(0xFF9E9E9E)

@OptIn(ExperimentalTvMaterial3Api::class)
private val DarkColorScheme = darkColorScheme(
    primary = SvOrange,
    onPrimary = Color.White,
    surface = SvSurfaceDark,
    onSurface = SvOnSurface,
    onSurfaceVariant = SvOnSurfaceVariant,
    background = SvDarkBackground,
    onBackground = SvOnSurface,
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StreetVoiceTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        shapes = MaterialTheme.shapes.copy(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(16.dp),
        ),
        content = content,
    )
}
