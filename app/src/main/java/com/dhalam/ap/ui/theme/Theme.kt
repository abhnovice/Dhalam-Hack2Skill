package com.dhalam.ap.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LightColors = lightColorScheme(
    secondary = Color(0xFF48635E), onSecondary = Color.White,
    tertiary = Color(0xFF48635E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0EAE7), onTertiaryContainer = Color(0xFF20332E),
    outline = Color(0xFF70847D), outlineVariant = Color(0xFFD4E0DB),
    surfaceVariant = Color(0xFFEAF1EE), surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F8F6),
    surfaceContainer = Color(0xFFEEF4F1), surfaceContainerHigh = Color(0xFFE7EFEB),
    surfaceContainerHighest = Color(0xFFDFE9E4),
    inverseSurface = Color(0xFF23342D), inverseOnSurface = Color(0xFFF4F8F6), inversePrimary = Color(0xFF8FE7D6),
    primary = Color(0xFF006B60),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB6F1E5),
    onPrimaryContainer = Color(0xFF002822),
    secondaryContainer = Color(0xFFD2EDE5),
    onSecondaryContainer = Color(0xFF153D34),
    background = Color(0xFFF4F9F7),
    onBackground = Color(0xFF14211D),
    surface = Color.White,
    onSurface = Color(0xFF14211D),
    onSurfaceVariant = Color(0xFF475952)
)

val DarkColors = darkColorScheme(
    secondary = Color(0xFFB4CDC3), onSecondary = Color(0xFF20332E),
    tertiary = Color(0xFFB4CDC3), onTertiary = Color(0xFF20332E),
    tertiaryContainer = Color(0xFF30463D), onTertiaryContainer = Color(0xFFD2F0E6),
    outline = Color(0xFF8A9E95), outlineVariant = Color(0xFF3C5147),
    surfaceVariant = Color(0xFF263A30), surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF0C1410), surfaceContainerLow = Color(0xFF17231D),
    surfaceContainer = Color(0xFF1D2C24), surfaceContainerHigh = Color(0xFF26372D),
    surfaceContainerHighest = Color(0xFF304339),
    inverseSurface = Color(0xFFE5EFEB), inverseOnSurface = Color(0xFF14211D), inversePrimary = Color(0xFF006B60),
    primary = Color(0xFF8FE7D6),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF16483F),
    onPrimaryContainer = Color(0xFFB1F4E6),
    secondaryContainer = Color(0xFF25483E),
    onSecondaryContainer = Color(0xFFD2F0E6),
    background = Color(0xFF101715),
    onBackground = Color(0xFFE5EFEB),
    surface = Color(0xFF1A2521),
    onSurface = Color(0xFFE5EFEB),
    onSurfaceVariant = Color(0xFFB8CCC3)
)


@Composable
fun DhalamTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = Typography, content = content)
}
