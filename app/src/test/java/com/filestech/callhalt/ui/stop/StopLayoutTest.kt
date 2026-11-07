package com.filestech.callhalt.ui.stop

import com.filestech.callhalt.ui.stop.StopPart.BUTTON
import com.filestech.callhalt.ui.stop.StopPart.CONTINUE
import com.filestech.callhalt.ui.stop.StopPart.EXPLANATION
import com.filestech.callhalt.ui.stop.StopPart.MESSAGE
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * The STOP screen's arrangement (U2): the buttons at the bottom when all fits, and otherwise right under the warning,
 * the explanation after them and "Continue" last — never the explanation between the warning and the buttons.
 */
class StopLayoutTest {

    /** Title, what happened, the warning; the rule, the spoofed number, the advice; two buttons; "Continue". */
    private val parts = listOf(MESSAGE, MESSAGE, MESSAGE, EXPLANATION, EXPLANATION, EXPLANATION, BUTTON, BUTTON, CONTINUE)
    private val heights = listOf(90, 120, 50, 60, 40, 80, 56, 56, 40)

    /** All of it, read in order: 592 of texts and buttons, 5 gaps of 12 between texts and 3 of 16 around the buttons. */
    private val asTall = heights.sum() + 5 * TEXT + 3 * GROUP

    @Test
    fun `when all fits, the texts are at the top and the buttons at the bottom`() {
        val placement = stopPlacement(parts, heights, available = 1_000, TEXT, GROUP)

        assertThat(placement.order).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8).inOrder()
        assertThat(placement.height).isEqualTo(1_000)
        // The texts from the top, 12 apart.
        assertThat(placement.ys.take(6)).containsExactly(0, 102, 234, 296, 368, 420).inOrder()
        // "Continue" against the bottom, the buttons above it, 16 apart.
        assertThat(placement.ys.drop(6)).containsExactly(1_000 - 40 - 16 - 56 - 16 - 56, 1_000 - 40 - 16 - 56, 960).inOrder()
    }

    @Test
    fun `just fitting, it is still the arrangement that fits`() {
        assertThat(stopPlacement(parts, heights, available = asTall, TEXT, GROUP).order)
            .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8).inOrder()
    }

    @Test
    fun `when it does not fit, the buttons come right under the warning and Continue last`() {
        val placement = stopPlacement(parts, heights, available = asTall - 1, TEXT, GROUP)

        assertThat(placement.order).containsExactly(0, 1, 2, 6, 7, 3, 4, 5, 8).inOrder()
        // One after the other, the whole screen scrolling: a text after a button is a group's gap away.
        assertThat(placement.ys).containsExactly(0, 102, 234, 300, 372, 444, 516, 568, 664).inOrder()
        assertThat(placement.height).isEqualTo(704)
    }

    /** No room known (a preview in a scrolling list): the pieces follow each other. */
    @Test
    fun `with no room at all, the pieces follow each other in the compact order`() {
        assertThat(stopPlacement(parts, heights, available = 0, TEXT, GROUP).order)
            .containsExactly(0, 1, 2, 6, 7, 3, 4, 5, 8).inOrder()
    }

    /** During a call the app cannot end, no button: "Continue" alone, at the bottom when all fits. */
    @Test
    fun `with no safe button, Continue alone is at the bottom`() {
        val placement = stopPlacement(listOf(MESSAGE, EXPLANATION, CONTINUE), listOf(100, 100, 40), 1_000, TEXT, GROUP)

        assertThat(placement.ys).containsExactly(0, 112, 960).inOrder()
    }

    /** The demonstration's banner is one more message, first: it stays first, the buttons still under the warning. */
    @Test
    fun `the demonstration's banner stays first`() {
        val demo = listOf(MESSAGE) + parts
        val placement = stopPlacement(demo, listOf(60) + heights, available = 500, TEXT, GROUP)

        assertThat(placement.order).containsExactly(0, 1, 2, 3, 7, 8, 4, 5, 6, 9).inOrder()
    }

    /** One safe button (after the call, a remote-control app): under the warning too. */
    @Test
    fun `a single safe button comes under the warning`() {
        val placement = stopPlacement(
            listOf(MESSAGE, MESSAGE, EXPLANATION, BUTTON, CONTINUE),
            listOf(300, 300, 300, 56, 40),
            available = 600,
            TEXT,
            GROUP,
        )

        assertThat(placement.order).containsExactly(0, 1, 3, 2, 4).inOrder()
    }

    /** Whatever the heights and the room: no two pieces overlap, each comes once, and all are inside the height. */
    @Test
    fun `no two pieces ever overlap`() {
        val random = Random(SEED)
        repeat(RUNS) {
            val sizes = parts.map { random.nextInt(1, 400) }
            val placement = stopPlacement(parts, sizes, available = random.nextInt(0, 3_000), TEXT, GROUP)

            assertThat(placement.order.sorted()).isEqualTo(parts.indices.toList())
            val spans = placement.order.mapIndexed { at, index -> placement.ys[at] to placement.ys[at] + sizes[index] }
                .sortedBy { it.first }
            spans.zipWithNext().forEach { (above, below) -> assertThat(below.first).isAtLeast(above.second) }
            assertThat(spans.first().first).isAtLeast(0)
            assertThat(spans.last().second).isAtMost(placement.height)
        }
    }

    private companion object {
        const val TEXT = 12
        const val GROUP = 16
        const val SEED = 20261107L
        const val RUNS = 500
    }
}
