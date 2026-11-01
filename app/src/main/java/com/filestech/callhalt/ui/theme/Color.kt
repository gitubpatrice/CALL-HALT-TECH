package com.filestech.callhalt.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// The two themes of the Files Tech portfolio, copied from Read Files Tech 3 (itself aligned on Agenda Tech, App
// Manager Tech and SMS Tech), in harmony with the checkerboard of the logo (Damier): Patrice wants people to
// recognise at a glance that the app comes from Files Tech (2026-11-01). Contrasts (WCAG 2.x, text 4.5:1)
// checked by BrandColorsTest.

/** The portfolio's blue, the light theme's primary. White on it: 6.3:1. */
private val PortfolioBlue = Color(0xFF2460AB)

/**
 * Pale blue-grey for the light theme's container surfaces — menus, dialogs, sheets — as the other apps pin them:
 * left unset, Material 3 derives a lavender wash that matches nothing of the brand.
 */
private val LightContainer = Color(0xFFF0F5FB)

internal val LightColors = lightColorScheme(
    primary = PortfolioBlue,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF001A40),
    inversePrimary = Color(0xFFA9C7FF),
    secondary = Color(0xFF555F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF3E4758),
    tertiary = Color(0xFF6F5575),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF9D8FD),
    onTertiaryContainer = Color(0xFF563E5C),
    background = Color(0xFFFDFCFF),
    onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFDFCFF),
    onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    surfaceTint = PortfolioBlue,
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFF0F0F7),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6CF),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFDFCFF),
    surfaceContainer = LightContainer,
    surfaceContainerHigh = LightContainer,
    surfaceContainerHighest = LightContainer,
    surfaceContainerLow = LightContainer,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFD9D9E0),
)

// Dark: the "GitHub dark" palette of the portfolio.
private val GithubBackground = Color(0xFF0D1117)
private val GithubSurface = Color(0xFF161B22)
private val GithubSurface2 = Color(0xFF21262D)
private val GithubBorder = Color(0xFF30363D)
private val GithubText = Color(0xFFE6EDF3)
private val GithubTextMuted = Color(0xFFC9D1D9)
private val GithubTextSecondary = Color(0xFF8B949E)
private val GithubBlue = Color(0xFF58A6FF)
private val GithubBlueContainer = Color(0xFF1F6FEB)
private val GithubError = Color(0xFFF85149)

internal val DarkColors = darkColorScheme(
    primary = GithubBlue,
    onPrimary = GithubBackground,
    primaryContainer = GithubBlueContainer,
    onPrimaryContainer = Color.White,
    inversePrimary = GithubBlueContainer,
    secondary = GithubBlue,
    onSecondary = GithubBackground,
    secondaryContainer = GithubSurface2,
    onSecondaryContainer = GithubTextMuted,
    tertiary = GithubBlue,
    onTertiary = GithubBackground,
    tertiaryContainer = GithubSurface2,
    onTertiaryContainer = GithubTextMuted,
    background = GithubBackground,
    onBackground = GithubText,
    surface = GithubSurface,
    onSurface = GithubText,
    surfaceVariant = GithubSurface2,
    onSurfaceVariant = GithubTextSecondary,
    surfaceTint = GithubBlue,
    inverseSurface = GithubText,
    inverseOnSurface = GithubSurface,
    error = GithubError,
    onError = Color.Black,
    errorContainer = Color(0xFF8E1519),
    onErrorContainer = Color(0xFFFFDAD6),
    // GitHub's subtle grey: 3.8:1 on the surface (WCAG 1.4.11 asks 3); the border stays for the dividers.
    outline = Color(0xFF6E7681),
    outlineVariant = GithubBorder,
    scrim = Color.Black,
    surfaceBright = GithubSurface,
    surfaceContainer = GithubSurface,
    surfaceContainerHigh = GithubSurface,
    surfaceContainerHighest = GithubSurface2,
    surfaceContainerLow = GithubSurface,
    surfaceContainerLowest = GithubSurface,
    surfaceDim = GithubSurface,
)
