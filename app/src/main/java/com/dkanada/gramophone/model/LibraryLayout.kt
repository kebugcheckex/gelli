package com.dkanada.gramophone.model

import androidx.annotation.DimenRes
import com.dkanada.gramophone.R

/**
 * How a library tab shows its items. A grid layout sets a target cell width, and the column count
 * follows the available width, so the density stays the same across phones, split-screen, and tablets.
 * The grid layouts are declared from the smallest cell to the largest.
 */
enum class LibraryLayout(@DimenRes val cellWidth: Int) {
    LIST(0),
    GRID_SMALL(R.dimen.grid_cell_small),
    GRID_MEDIUM(R.dimen.grid_cell_medium),
    GRID_LARGE(R.dimen.grid_cell_large);

    val isGrid: Boolean
        get() = this != LIST

    /**
     * The column count of this grid layout for [availableWidth], with [cellWidthOf] giving each grid
     * layout's cell width in the same unit. Each smaller cell size gets at least one column more than
     * the next larger one, so the sizes never look the same, even when the width falls just short of
     * fitting another small cell.
     */
    fun gridSpanCount(availableWidth: Int, cellWidthOf: (LibraryLayout) -> Int): Int {
        require(isGrid) { "$this is not a grid layout" }

        var spanCount = 0
        for (layout in entries.filter { it.isGrid && it.ordinal >= ordinal }.reversed()) {
            spanCount = maxOf(spanCount + 1, availableWidth / cellWidthOf(layout))
        }

        return spanCount
    }
}
