package com.dkanada.gramophone.util

import com.dkanada.gramophone.database.InventorySnapshot
import org.junit.Assert.*
import org.junit.Test

class OfflineCoverageTest {
    private fun coverage(local: Set<String>, server: Set<String>?) = OfflineCoverage.from(
        LocalLibrarySummary("account", local, 0),
        server?.let { InventorySnapshot("account", it, 123) }
    )

    @Test
    fun unknownInventoryShowsSavedCountWithoutPercentageOrTimestamp() {
        val result = coverage(setOf("a", "b"), null)
        assertEquals(2, result.availableSongCount)
        assertNull(result.totalSongCount)
        assertNull(result.percentage)
        assertNull(result.checkedAt)
        assertNull(result.savedOutsideInventoryCount)
    }

    @Test
    fun emptyInventoryIsKnownAndLocalOnlyFilesAreSeparate() {
        val result = coverage(setOf("a"), emptySet())
        assertEquals(0, result.availableSongCount)
        assertEquals(0, result.totalSongCount)
        assertEquals(1, result.savedOutsideInventoryCount)
        assertEquals(123L, result.checkedAt)
        assertNull(result.percentage)
    }

    @Test
    fun intersectionExcludesSavedSongsOutsideInventory() {
        val result = coverage(setOf("a", "outside"), setOf("a", "b", "c"))
        assertEquals(1, result.availableSongCount)
        assertEquals(3, result.totalSongCount)
        assertEquals(1, result.savedOutsideInventoryCount)
        assertEquals(33, result.percentage)
    }

    @Test
    fun percentageFloorsWithoutRoundingIncompleteLibraryToOneHundred() {
        val server = (1..1001).map { "$it" }.toSet()
        val tiny = coverage(setOf("1"), server)
        assertEquals(0, tiny.percentage)
        assertTrue(tiny.isBelowOnePercent)
        assertFalse(coverage(emptySet(), server).isBelowOnePercent)
        assertEquals(0, coverage(emptySet(), server).percentage)
        assertEquals(99, coverage(server - "1", server).percentage)
        assertEquals(100, coverage(server, server).percentage)
    }

    @Test(expected = IllegalArgumentException::class)
    fun mismatchedAccountsCannotBeCombined() {
        OfflineCoverage.from(LocalLibrarySummary("a", setOf("song"), null),
            InventorySnapshot("b", setOf("song"), 1))
    }
}
