package com.dkanada.gramophone.model

import androidx.annotation.DimenRes
import com.dkanada.gramophone.R

/**
 * How a library tab shows its items. A grid layout sets a target cell width, and the column count
 * follows the available width, so the density stays the same across phones, split-screen, and tablets.
 */
enum class LibraryLayout(@DimenRes val cellWidth: Int) {
    LIST(0),
    GRID_SMALL(R.dimen.grid_cell_small),
    GRID_MEDIUM(R.dimen.grid_cell_medium),
    GRID_LARGE(R.dimen.grid_cell_large);

    val isGrid: Boolean
        get() = this != LIST
}
