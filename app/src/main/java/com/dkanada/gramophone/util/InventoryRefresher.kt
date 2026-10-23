package com.dkanada.gramophone.util

import com.dkanada.gramophone.database.InventorySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

sealed interface InventoryRefreshResult {
    data class Updated(val snapshot: InventorySnapshot) : InventoryRefreshResult
    data object Superseded : InventoryRefreshResult
    data class Failed(val cause: Exception) : InventoryRefreshResult
}

/** Serializes publication with account selection. No network work runs while holding the lock. */
internal class InventoryRefreshCoordinator {
    data class Ticket(val accountId: String, val selection: Long, val request: Long)

    private val lock = Any()
    private var selection = 0L
    private var request = 0L
    private val latest = mutableMapOf<String, Long>()

    fun begin(accountId: String): Ticket = synchronized(lock) {
        Ticket(accountId, selection, ++request).also { latest[accountId] = it.request }
    }

    fun changeAccount(update: () -> Unit) = synchronized(lock) {
        selection++
        latest.clear()
        update()
    }

    fun publish(ticket: Ticket, isCurrent: () -> Boolean, write: () -> Unit): Boolean = synchronized(lock) {
        if (ticket.selection != selection || latest[ticket.accountId] != ticket.request || !isCurrent()) {
            false
        } else {
            write()
            true
        }
    }
}

/** All pages are loaded before touching persistent state. An error preserves the last good snapshot. */
internal class InventoryRefresher(
    private val coordinator: InventoryRefreshCoordinator,
    private val isCurrent: (String) -> Boolean,
    private val load: suspend (String) -> Set<String>,
    private val replace: (InventorySnapshot) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun refresh(
        accountId: String,
        ticket: InventoryRefreshCoordinator.Ticket = coordinator.begin(accountId)
    ): InventoryRefreshResult {
        require(ticket.accountId == accountId)
        if (!coordinator.publish(ticket, { isCurrent(accountId) }) {}) return InventoryRefreshResult.Superseded
        return try {
            val snapshot = InventorySnapshot(accountId, load(accountId).toSet(), clock())
            currentCoroutineContext().ensureActive()
            if (coordinator.publish(ticket, { isCurrent(accountId) }) { replace(snapshot) }) {
                InventoryRefreshResult.Updated(snapshot)
            } else {
                InventoryRefreshResult.Superseded
            }
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            if (coordinator.publish(ticket, { isCurrent(accountId) }) {}) {
                InventoryRefreshResult.Failed(err)
            } else InventoryRefreshResult.Superseded
        }
    }
}

internal data class InventoryPage<T>(val items: List<T>, val total: Int, val startIndex: Int)

/** Validate pagination instead of treating a truncated/changed inventory as a successful result. */
internal suspend fun <T> loadInventoryPages(
    pageSize: Int,
    fetch: suspend (startIndex: Int, limit: Int) -> InventoryPage<T>
): List<T> {
    require(pageSize > 0)
    val items = mutableListOf<T>()
    var total: Int? = null
    do {
        currentCoroutineContext().ensureActive()
        val page = fetch(items.size, pageSize)
        check(page.startIndex == items.size && page.total >= 0) { "Invalid inventory page" }
        check(total == null || total == page.total) { "Inventory changed during refresh" }
        total = page.total
        check(page.items.size <= pageSize && page.items.size <= total - items.size) {
            "Inventory page exceeds expected size"
        }
        check(page.items.isNotEmpty() || items.size == total) { "Incomplete inventory response" }
        items.addAll(page.items)
    } while (items.size < total)
    return items
}
