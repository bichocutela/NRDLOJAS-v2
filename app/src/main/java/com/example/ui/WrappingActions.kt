package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight

/** Wrap complete actions before they would crowd out an adjacent control. */
@Composable
internal fun WrappingActions(
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gap = 8.dp.roundToPx()
        val rows = mutableListOf<MutableList<androidx.compose.ui.layout.Placeable>>()
        val widths = mutableListOf<Int>()
        val heights = mutableListOf<Int>()
        measurables.forEach { measurable ->
            val child = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
            if (rows.isEmpty() || widths.last() + gap + child.width > constraints.maxWidth) {
                rows.add(mutableListOf())
                widths.add(0)
                heights.add(0)
            }
            val row = rows.lastIndex
            if (rows[row].isNotEmpty()) widths[row] += gap
            rows[row].add(child)
            widths[row] += child.width
            heights[row] = maxOf(heights[row], child.height)
        }
        val width = constraints.constrainWidth(widths.maxOrNull() ?: 0)
        val height = constraints.constrainHeight(heights.sum() + gap * maxOf(0, rows.size - 1))
        layout(width, height) {
            var y = 0
            rows.forEachIndexed { row, children ->
                var x = if (alignEnd) width - widths[row] else 0
                children.forEach { child ->
                    child.placeRelative(x, y + (heights[row] - child.height) / 2)
                    x += child.width + gap
                }
                y += heights[row] + gap
            }
        }
    }
}
