package com.dkanada.gramophone.model

import org.junit.Assert.*
import org.junit.Test

class LibraryLayoutTest {
    // the cell widths in dp, matching dimens.xml
    private val cellWidthOf: (LibraryLayout) -> Int = {
        when (it) {
            LibraryLayout.GRID_SMALL -> 128
            LibraryLayout.GRID_MEDIUM -> 168
            LibraryLayout.GRID_LARGE -> 216
            LibraryLayout.LIST -> error("not a grid")
        }
    }

    private fun spans(availableWidth: Int) = listOf(
        LibraryLayout.GRID_SMALL.gridSpanCount(availableWidth, cellWidthOf),
        LibraryLayout.GRID_MEDIUM.gridSpanCount(availableWidth, cellWidthOf),
        LibraryLayout.GRID_LARGE.gridSpanCount(availableWidth, cellWidthOf),
    )

    @Test
    fun gridSpanCount_fitsCellsWhenTheWidthAllows() {
        // a 411dp phone without the letter bar
        assertEquals(listOf(3, 2, 1), spans(403))
    }

    @Test
    fun gridSpanCount_keepsSmallAheadOfMediumWithTheLetterBar() {
        // a 411dp phone with the letter bar, where only two small cells fit
        assertEquals(listOf(3, 2, 1), spans(375))
    }

    @Test
    fun gridSpanCount_keepsMediumAheadOfLargeOnNarrowPhones() {
        // a 360dp phone with the letter bar, where only one medium cell fits
        assertEquals(listOf(3, 2, 1), spans(324))
    }

    @Test
    fun gridSpanCount_leavesWideScreensToTheCellWidths() {
        assertEquals(listOf(9, 7, 5), spans(1200))
    }

    @Test
    fun gridSpanCount_givesAtLeastOneColumn() {
        assertEquals(listOf(3, 2, 1), spans(50))
    }

    @Test(expected = IllegalArgumentException::class)
    fun gridSpanCount_rejectsTheList() {
        LibraryLayout.LIST.gridSpanCount(400, cellWidthOf)
    }
}
