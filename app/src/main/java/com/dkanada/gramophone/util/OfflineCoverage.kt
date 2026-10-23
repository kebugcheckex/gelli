package com.dkanada.gramophone.util

import com.dkanada.gramophone.database.InventorySnapshot

data class OfflineCoverage(
    val availableSongCount: Int,
    val totalSongCount: Int?,
    val savedOutsideInventoryCount: Int?,
    val checkedAt: Long?
) {
    /** Null for unknown or empty inventories. Positive values below 1% remain distinguishable. */
    val percentage: Int? get() = totalSongCount?.takeIf { it > 0 }
        ?.let { (availableSongCount.toLong() * 100 / it).toInt() }

    val isBelowOnePercent: Boolean get() = availableSongCount > 0 && percentage == 0

    companion object {
        fun from(local: LocalLibrarySummary, inventory: InventorySnapshot?): OfflineCoverage {
            require(inventory == null || inventory.accountId == local.accountId)
            if (inventory == null) return OfflineCoverage(local.availableSongCount, null, null, null)
            val matching = local.availableSongIds.count { it in inventory.songIds }
            return OfflineCoverage(matching, inventory.songIds.size,
                local.availableSongCount - matching, inventory.checkedAt)
        }
    }
}
