package com.dkanada.gramophone.database

/** Last complete server inventory, independent of the currently selected browsing library. */
data class InventorySnapshot(val accountId: String, val songIds: Set<String>, val checkedAt: Long)
