package com.filestech.callhalt.ui.theme

import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The checkerboard of the Files Tech logo (J:\PSD FILES\background-files-tech.png), on the icon of every app of
 * the portfolio, this one's included: two tones only, the values of Read Files Tech and Notes Tech.
 */
object Damier {
    /** The blue: the app's signature on its filled buttons, in both themes. White on it: 6.0:1. */
    val Blue = Color(0xFF0B60C5)

    /**
     * The red of the logo. In the other apps it never stands for a warning; here it is the STOP screen's ground,
     * on purpose: the one moment the app must be recognised at a glance, and the one alarm it raises.
     * White on it: 5.25:1 (BrandColorsTest).
     */
    val Red = Color(0xFFCA331F)

    /** Text and icons on [Blue] and [Red]. */
    val OnColour = Color.White
}

/** The STOP screen's colours, fixed whatever the theme: the interruption looks the same on every phone. */
val StopBackground = Damier.Red
val OnStop = Damier.OnColour

/** Every filled button that destroys nothing: white on the checkerboard's blue in both themes, as the other apps. */
@Composable
fun brandButtonColors(): ButtonColors = ButtonDefaults.buttonColors(containerColor = Damier.Blue, contentColor = Damier.OnColour)
