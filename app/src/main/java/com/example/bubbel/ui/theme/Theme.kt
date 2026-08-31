package com.example.bubbel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val BubbelColorScheme = lightColorScheme(
    primary = LightGreen,
    onPrimary = DarkBrown,
    primaryContainer = LimeGreen,
    onPrimaryContainer = DarkBrown,
    secondary = SoftBlue,
    onSecondary = DarkBrown,
    secondaryContainer = BananaGreen,
    onSecondaryContainer = DarkBrown,
    tertiary = SunsetOrange,
    onTertiary = DarkBrown,
    tertiaryContainer = LightOrange,
    onTertiaryContainer = DarkBrown,
    error = SunsetRed,
    onError = DarkBrown,
    background = PastelYellow,
    onBackground = DarkBrown,
    surface = CreamYellow,
    onSurface = DarkBrown,
    surfaceVariant = LightOrange,
    onSurfaceVariant = DarkGrey,
    outline = DarkGrey,
    outlineVariant = DarkGrey
)

@Composable
fun BubbelTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BubbelColorScheme,
        typography = Typography,
        content = content
    )
}
