package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ClaudeAccentClay,
    onPrimary = Color.White,
    primaryContainer = ClaudeDarkSurfaceVariant,
    onPrimaryContainer = ClaudeDarkTextPrimary,
    secondary = ClaudeAccentClayLight,
    onSecondary = Color.Black,
    background = ClaudeDarkBg,
    onBackground = ClaudeDarkTextPrimary,
    surface = ClaudeDarkSurface,
    onSurface = ClaudeDarkTextPrimary,
    surfaceVariant = ClaudeDarkSurfaceVariant,
    onSurfaceVariant = ClaudeDarkTextSecondary,
    outline = ClaudeDarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = ClaudeAccentClay,
    onPrimary = Color.White,
    primaryContainer = ClaudeCreamSurfaceVariant,
    onPrimaryContainer = ClaudeTextPrimary,
    secondary = ClaudeAccentClayDark,
    onSecondary = Color.White,
    background = ClaudeCreamBg,
    onBackground = ClaudeTextPrimary,
    surface = ClaudeCreamBg,
    onSurface = ClaudeTextPrimary,
    surfaceVariant = ClaudeCreamSurface,
    onSurfaceVariant = ClaudeTextSecondary,
    outline = ClaudeBorder
)

@Composable
fun ProjectAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    ProjectAssistantTheme(darkTheme = darkTheme, content = content)
}

