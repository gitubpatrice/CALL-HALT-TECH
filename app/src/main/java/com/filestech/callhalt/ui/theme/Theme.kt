package com.filestech.callhalt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The portfolio's palettes (Color.kt), light or dark as the phone is set. No dynamic colour (Material You): the
 * app must look like the other Files Tech apps, and a palette drawn from the wallpaper guarantees no contrast.
 */
@Composable
fun CallHaltTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
