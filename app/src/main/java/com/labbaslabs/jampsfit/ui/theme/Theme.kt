package com.labbaslabs.jampsfit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OledDarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color.Black,
    surfaceTint = Color.Black,
    onSurface = Color.White,
    onBackground = Color.White,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainerLowest = Color.Black,
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    outline = Color.White.copy(alpha = 0.22f),
    outlineVariant = Color.White.copy(alpha = 0.12f)
)

@Composable
fun JampsFitTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OledDarkColorScheme,
        typography = Typography,
        content = content
    )
}
