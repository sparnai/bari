package com.sparnai.bari.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Adriatic blue + warm Apulian sun.
private val LightColors = lightColorScheme(
    primary = Color(0xFF00658E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7E7FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF9A4600),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBC8),
    onSecondaryContainer = Color(0xFF321300),
    tertiary = Color(0xFF00696C),
    tertiaryContainer = Color(0xFF9CF1F4),
    onTertiaryContainer = Color(0xFF002021),
    background = Color(0xFFF6FAFE),
    onBackground = Color(0xFF171C1F),
    surface = Color(0xFFF6FAFE),
    onSurface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F8),
    surfaceContainer = Color(0xFFEAEEF2),
    surfaceContainerHigh = Color(0xFFE4E9ED),
    surfaceContainerHighest = Color(0xFFDFE3E7),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF85CFFF),
    onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF004C6C),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFFFFB68B),
    onSecondary = Color(0xFF532200),
    secondaryContainer = Color(0xFF763300),
    onSecondaryContainer = Color(0xFFFFDBC8),
    tertiary = Color(0xFF80D4D8),
    tertiaryContainer = Color(0xFF004F52),
    onTertiaryContainer = Color(0xFF9CF1F4),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFDFE3E7),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFDFE3E7),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF171C1F),
    surfaceContainer = Color(0xFF1B2023),
    surfaceContainerHigh = Color(0xFF252B2E),
    surfaceContainerHighest = Color(0xFF303539),
    outline = Color(0xFF8B9298),
    outlineVariant = Color(0xFF41484D),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Semantic colours for price movement and connection quality. */
@Immutable
data class PriceColors(
    val down: Color,
    val downContainer: Color,
    val up: Color,
    val upContainer: Color,
    val warn: Color,
    val warnContainer: Color,
)

private val LightPriceColors = PriceColors(
    down = Color(0xFF1B7F3B),
    downContainer = Color(0xFFD3F5DC),
    up = Color(0xFFB3261E),
    upContainer = Color(0xFFFFE1DD),
    warn = Color(0xFF8A5A00),
    warnContainer = Color(0xFFFFE8B8),
)

private val DarkPriceColors = PriceColors(
    down = Color(0xFF7EDB97),
    downContainer = Color(0xFF0F3D1E),
    up = Color(0xFFFFB4AB),
    upContainer = Color(0xFF5C1712),
    warn = Color(0xFFFFCF70),
    warnContainer = Color(0xFF4A3300),
)

val LocalPriceColors = staticCompositionLocalOf { LightPriceColors }

@Composable
fun BariTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme: ColorScheme = if (dark) DarkColors else LightColors
    androidx.compose.runtime.CompositionLocalProvider(
        LocalPriceColors provides if (dark) DarkPriceColors else LightPriceColors,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
