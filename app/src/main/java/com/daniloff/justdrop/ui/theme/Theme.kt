package com.daniloff.justdrop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Background,

    background = Background,
    onBackground = OnBackground,

    surface = Surface,
    onSurface = OnBackground,

    error = Error,
    onError = OnBackground
)

@Composable
fun JustDropTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}