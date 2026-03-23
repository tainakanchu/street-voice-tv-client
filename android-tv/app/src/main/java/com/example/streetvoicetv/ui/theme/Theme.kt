package com.example.streetvoicetv.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// StreetVoice brand colors based on #F5565C
val SvRed = Color(0xFFF5565C)
val SvRedLight = Color(0xFFFF8A8E)
val SvRedDark = Color(0xFFBF2D33)
val SvDarkBackground = Color(0xFF121212)
val SvSurfaceDark = Color(0xFF1C1C1C)
val SvSurfaceVariant = Color(0xFF2A2222)
val SvOnSurface = Color(0xFFECE0E0)
val SvOnSurfaceVariant = Color(0xFFA09696)
val SvError = Color(0xFFFF6B6B)

@OptIn(ExperimentalTvMaterial3Api::class)
private val DarkColorScheme = darkColorScheme(
    primary = SvRed,
    onPrimary = Color.White,
    secondary = SvRedLight,
    onSecondary = Color.Black,
    surface = SvSurfaceDark,
    onSurface = SvOnSurface,
    surfaceVariant = SvSurfaceVariant,
    onSurfaceVariant = SvOnSurfaceVariant,
    background = SvDarkBackground,
    onBackground = SvOnSurface,
    error = SvError,
    onError = Color.White,
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
