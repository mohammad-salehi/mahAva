package com.mahava.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = MahavaPrimary,
    onPrimary = MahavaSurface,
    primaryContainer = MahavaPrimarySoft,
    onPrimaryContainer = MahavaTextPrimary,
    secondary = MahavaFertility,
    background = MahavaBackground,
    onBackground = MahavaTextPrimary,
    surface = MahavaSurface,
    onSurface = MahavaTextPrimary,
    surfaceVariant = MahavaPrimarySoft,
    onSurfaceVariant = MahavaTextSecondary,
    outline = MahavaBorder,
    error = MahavaDanger
)

private val MahavaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MahavaTheme(content: @Composable () -> Unit) {
    // Force RTL for Persian UI regardless of system locale quirks
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = LightColors,
            typography = MahavaTypography,
            shapes = MahavaShapes,
            content = content
        )
    }
}
