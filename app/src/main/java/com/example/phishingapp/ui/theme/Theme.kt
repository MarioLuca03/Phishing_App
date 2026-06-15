package com.example.phishingapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = TextPrimary,
    secondary = AccentBlue,
    onSecondary = TextPrimary,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = BackgroundDark,
    onSurface = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = TextPrimary,
    secondary = AccentBlue,
    onSecondary = TextPrimary,
    background = BackgroundLight,
    onBackground = BackgroundDark,
    surface = BackgroundLight,
    onSurface = BackgroundDark
)

@Composable
fun PhishingAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}