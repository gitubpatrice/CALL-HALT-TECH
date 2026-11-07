package com.filestech.callhalt.ui.stop

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** What a piece of the STOP screen is, for its place (see [stopPlacement]). */
internal enum class StopPart {
    /** What happened and "if you are told to ignore this screen, it is a scam": first, always. */
    MESSAGE,

    /** The rule, the spoofed number, the advice: under the safe buttons when the screen is too short for all. */
    EXPLANATION,

    /** A safe way out ("Hang up", "Call my bank"…). */
    BUTTON,

    /** "Continue": last, always. */
    CONTINUE,
}

/**
 * Where each piece goes: [tops], one per piece, in the pieces' own order (so that placing piece i at `tops[i]` cannot
 * pick another's place); [order], the pieces top to bottom; the whole [height] tall.
 */
internal data class StopPlacement(val tops: List<Int>, val order: List<Int>, val height: Int)

/**
 * The STOP screen's arrangement (publication audit of 2026-11-07, U2), from the [parts] in their reading order and
 * their [heights]. When all fits in [available], the texts at the top and the buttons at the bottom, in sight, as
 * before. When not — a large font, a short or turned screen, a long language — the screen scrolls as a whole, and
 * reads: what happened, the warning, the safe buttons, then the explanation, "Continue" last. Without it, the texts
 * scrolled in what the buttons left them: at a font of 200 %, the screen stopped at "If you are told to ignore", and
 * someone under pressure does not scroll. Two texts in a row are [textGap] apart, anything else [groupGap].
 */
internal fun stopPlacement(
    parts: List<StopPart>,
    heights: List<Int>,
    available: Int,
    textGap: Int,
    groupGap: Int,
): StopPlacement {
    require(parts.size == heights.size) { "one height per part" }
    fun gap(above: StopPart, below: StopPart): Int =
        if (above.isText() && below.isText()) textGap else groupGap
    fun length(order: List<Int>): Int =
        order.sumOf { heights[it] } + order.zipWithNext { a, b -> gap(parts[a], parts[b]) }.sum()

    val asRead = parts.indices.toList()
    val tops = IntArray(parts.size)
    fun stackAt(order: List<Int>, from: Int) =
        stack(order, from, heights, parts, ::gap).forEachIndexed { at, y -> tops[order[at]] = y }
    if (length(asRead) <= available) {
        val bottom = asRead.filter { parts[it] == StopPart.BUTTON || parts[it] == StopPart.CONTINUE }
        stackAt(asRead - bottom.toSet(), from = 0)
        stackAt(bottom, from = available - length(bottom))
        return StopPlacement(tops.toList(), asRead, available)
    }
    val compact = listOf(StopPart.MESSAGE, StopPart.BUTTON, StopPart.EXPLANATION, StopPart.CONTINUE)
        .flatMap { part -> asRead.filter { parts[it] == part } }
    stackAt(compact, from = 0)
    return StopPlacement(tops.toList(), compact, length(compact))
}

/** The tops of the pieces of [order], stacked from [from] down, each followed by its gap to the next. */
private fun stack(
    order: List<Int>,
    from: Int,
    heights: List<Int>,
    parts: List<StopPart>,
    gap: (StopPart, StopPart) -> Int,
): List<Int> = order.runningFold(from) { y, index ->
    val next = order.getOrNull(order.indexOf(index) + 1)
    y + heights[index] + (next?.let { gap(parts[index], parts[it]) } ?: 0)
}.dropLast(1)

private fun StopPart.isText() = this == StopPart.MESSAGE || this == StopPart.EXPLANATION

/**
 * Lays out [content], whose children each carry their [StopPart] as `Modifier.layoutId`, by [stopPlacement]: in one
 * measure, so that the screen never shows one arrangement and then the other.
 */
@Composable
internal fun StopLayout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        // Unbounded (a preview in a scrolling list): nothing "fits", the pieces follow each other.
        val available = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
        Layout(content = content, modifier = Modifier.verticalScroll(rememberScrollState())) { measurables, constraints ->
            val loose = Constraints(maxWidth = constraints.maxWidth)
            val placeables = measurables.map { it.measure(loose) }
            val parts = measurables.map { it.layoutId as? StopPart ?: StopPart.EXPLANATION }
            val placement = stopPlacement(
                parts,
                placeables.map { it.height },
                available,
                textGap = TEXT_GAP.roundToPx(),
                groupGap = GROUP_GAP.roundToPx(),
            )
            // Unbounded width (a preview in a horizontal list): as wide as the widest piece.
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeables.maxOfOrNull { it.width } ?: 0
            layout(width, placement.height) {
                placeables.forEachIndexed { index, piece -> piece.place(0, placement.tops[index]) }
            }
        }
    }
}

private val TEXT_GAP = 12.dp
private val GROUP_GAP = 16.dp
