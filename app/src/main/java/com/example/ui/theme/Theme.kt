package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = StudioObsidian,
    primaryContainer = ElectricCyanMuted,
    onPrimaryContainer = ElectricCyan,
    secondary = VibrantViolet,
    onSecondary = TextPrimaryDark,
    secondaryContainer = VibrantVioletMuted,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = CoralRose,
    onTertiary = TextPrimaryDark,
    background = StudioObsidian,
    onBackground = TextPrimaryDark,
    surface = StudioSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = StudioSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioSurfaceElevated,
    surfaceContainerHigh = StudioSurfaceHighlight,
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle,
    error = CoralRose,
    onError = TextPrimaryDark,
    errorContainer = ErrorSurfaceDark,
    onErrorContainer = ErrorTextDark
)

val StudioShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MotionStudioTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = MotionTypography,
        shapes = StudioShapes,
        content = content
    )
}
