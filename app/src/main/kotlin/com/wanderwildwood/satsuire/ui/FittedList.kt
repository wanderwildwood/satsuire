package com.wanderwildwood.satsuire.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/*
 * A paged list in a dialog fills all the height it is offered, so a short one left a tall blank
 * box (the Groups dialog with no groups yet). These give it the height of its rows when they all
 * fit, and [room] when they do not; then it pages as any list does.
 */

/** A row of a list as laid out: its index, where it starts and how tall it is, in pixels. */
internal data class RowExtent(val index: Int, val offset: Int, val size: Int)

/**
 * The height of rows 0 until [rowCount] when every one of them is laid out whole above
 * [viewportEnd]; null when some are not (the list then needs all the room it has).
 */
internal fun rowsHeight(rows: List<RowExtent>, rowCount: Int, viewportEnd: Int): Int? {
    if (rowCount <= 0) return null
    val real = rows.filter { it.index < rowCount }
    if (real.size != rowCount || real.first().index != 0 || real.first().offset < 0) return null
    val end = real.last().offset + real.last().size
    return if (end <= viewportEnd) end else null
}

/**
 * The height for a paged list on [state]: its rows' when they fit, else [room]. [tail] is true for
 * a list without the cut row, whose last item is its own blank tail and not a row.
 */
@Composable
fun rememberFittedHeight(state: LazyListState, room: Dp, tail: Boolean): Dp {
    val density = LocalDensity.current
    val fitted by remember(state, tail) { derivedStateOf {
        val info = state.layoutInfo
        rowsHeight(
            info.visibleItemsInfo.map { RowExtent(it.index, it.offset, it.size) },
            rowCount = info.totalItemsCount - if (tail) 1 else 0,
            viewportEnd = info.viewportEndOffset,
        )
    }}
    return fitted?.let { with(density) { it.toDp() } }?.coerceAtMost(room) ?: room
}
