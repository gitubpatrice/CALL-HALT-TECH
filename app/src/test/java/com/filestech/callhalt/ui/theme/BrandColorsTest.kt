package com.filestech.callhalt.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test

/**
 * The portfolio's colours keep their promise of contrast — measured here, not supposed: a brand colour is chosen
 * because it identifies, nothing makes it readable in advance. Text needs 4.5:1 (WCAG 1.4.3), a control against
 * what surrounds it 3:1 (1.4.11). Copied from Read Files Tech 3, with the STOP screen added.
 */
class BrandColorsTest {

    @Test
    fun `white reads on the checkerboard blue and on the STOP screen's red`() {
        assertThat(contrast(Damier.OnColour, Damier.Blue)).isAtLeast(TEXT)
        assertThat(contrast(OnStop, StopBackground)).isAtLeast(TEXT)
        // The "Hang up" button inverts them: red text on white.
        assertThat(contrast(StopBackground, OnStop)).isAtLeast(TEXT)
    }

    @Test
    fun `the checkerboard's buttons stand out on both pages`() {
        assertThat(contrast(Damier.Blue, LightColors.background)).isAtLeast(NON_TEXT)
        assertThat(contrast(Damier.Blue, DarkColors.background)).isAtLeast(NON_TEXT)
    }

    @Test
    fun `the text of both palettes reads on what it is drawn on`() {
        for (scheme in listOf(LightColors, DarkColors)) {
            textPairs(scheme).forEach { (role, pair) ->
                assertWithMessage(role).that(contrast(pair.first, pair.second)).isAtLeast(TEXT)
            }
        }
    }

    /** The witness: without it the tests above would pass as well if [contrast] returned a constant. */
    @Test
    fun `the contrast computation can say no`() {
        assertThat(contrast(Color(0xFF9E9E9E), Color(0xFFBDBDBD))).isLessThan(NON_TEXT)
    }

    private fun textPairs(scheme: ColorScheme): List<Pair<String, Pair<Color, Color>>> = listOf(
        "onBackground" to (scheme.onBackground to scheme.background),
        "onSurface" to (scheme.onSurface to scheme.surface),
        "onSurface on a card" to (scheme.onSurface to scheme.surfaceContainerHighest),
        "onSurfaceVariant" to (scheme.onSurfaceVariant to scheme.surface),
        "primary" to (scheme.primary to scheme.surface),
        "onPrimary" to (scheme.onPrimary to scheme.primary),
        "onPrimaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
        "onErrorContainer" to (scheme.onErrorContainer to scheme.errorContainer),
    )

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }

    private companion object {
        /** WCAG 2.1, 1.4.3: text of the usual size. */
        const val TEXT = 4.5f

        /** WCAG 2.1, 1.4.11: what is not text — a button against its background, an icon. */
        const val NON_TEXT = 3.0f
    }
}
