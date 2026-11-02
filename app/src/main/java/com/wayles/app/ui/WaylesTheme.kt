package com.wayles.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Dark = ColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF0B0B0D),
    primaryContainer = Color(0xFF242428),
    onPrimaryContainer = Color(0xFFECECEF),
    inversePrimary = Color(0xFF101013),
    secondary = Color(0xFFC8C8CD),
    onSecondary = Color(0xFF1E1E22),
    secondaryContainer = Color(0xFF1C1C20),
    onSecondaryContainer = Color(0xFFD8D8DC),
    tertiary = Color(0xFFB3B3BB),
    onTertiary = Color(0xFF17171B),
    tertiaryContainer = Color(0xFF222226),
    onTertiaryContainer = Color(0xFFD0D0D6),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFECECEF),
    surface = Color(0xFF0B0B0D),
    onSurface = Color(0xFFECECEF),
    surfaceVariant = Color(0xFF1B1B1F),
    onSurfaceVariant = Color(0xFF9C9CA3),
    surfaceTint = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFFECECEF),
    inverseOnSurface = Color(0xFF161619),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF66666E),
    outlineVariant = Color(0xFF2B2B30),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF313134),
    surfaceDim = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF16161A),
    surfaceContainerHigh = Color(0xFF1E1E22),
    surfaceContainerHighest = Color(0xFF28282C),
    surfaceContainerLow = Color(0xFF121215),
    surfaceContainerLowest = Color(0xFF060608)
)

private val Light = ColorScheme(
    primary = Color(0xFF101013),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8E8EA),
    onPrimaryContainer = Color(0xFF16161A),
    inversePrimary = Color(0xFFECECEF),
    secondary = Color(0xFF56565C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9E9EC),
    onSecondaryContainer = Color(0xFF1B1B20),
    tertiary = Color(0xFF4A4A52),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE2E2E6),
    onTertiaryContainer = Color(0xFF16161A),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF16161A),
    surface = Color(0xFFFAFAFA),
    onSurface = Color(0xFF16161A),
    surfaceVariant = Color(0xFFE4E4E7),
    onSurfaceVariant = Color(0xFF5A5A61),
    surfaceTint = Color(0xFF101013),
    inverseSurface = Color(0xFF2F2F33),
    inverseOnSurface = Color(0xFFF2F2F4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF8B8B92),
    outlineVariant = Color(0xFFD8D8DC),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAFAFA),
    surfaceDim = Color(0xFFDBDBDC),
    surfaceContainer = Color(0xFFEEEEEF),
    surfaceContainerHigh = Color(0xFFE8E8EA),
    surfaceContainerHighest = Color(0xFFE2E2E4),
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainerLowest = Color(0xFFFFFFFF)
)

private val WaylesShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun WaylesTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        shapes = WaylesShapes,
        content = content
    )
}
