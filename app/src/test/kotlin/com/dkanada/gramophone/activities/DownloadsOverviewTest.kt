package com.dkanada.gramophone.activities

import com.dkanada.gramophone.database.InventorySnapshot
import com.dkanada.gramophone.util.InventoryRefreshResult
import com.dkanada.gramophone.util.LocalLibrarySummary
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DownloadsOverviewTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val cached = InventorySnapshot("a", setOf("saved", "remote"), 12)
    private val saved = LocalLibrarySummary("a", setOf("saved"), 100)
    private val repository = FakeRepository()
    private val overview = DownloadsOverview(scope, repository)

    @After
    fun close() = scope.cancel()

    @Test
    fun waitsForCachedInventoryBeforeRefreshingAndThenReplacesIt() {
        val disk = CompletableDeferred<InventorySnapshot?>()
        val server = CompletableDeferred<InventoryRefreshResult>()
        repository.read = { disk.await() }
        repository.fetch = { server.await() }

        overview.open("a", true)
        assertEquals(saved, overview.state.value.local)
        assertTrue(overview.state.value.loadingInventory)
        overview.refresh()
        assertEquals(0, repository.refreshes)

        disk.complete(cached)
        assertEquals(cached, overview.state.value.inventory)
        assertTrue(overview.state.value.refreshing)
        val fresh = cached.copy(songIds = setOf("saved"), checkedAt = 34)
        server.complete(InventoryRefreshResult.Updated(fresh))
        assertEquals(fresh, overview.state.value.inventory)
        assertFalse(overview.state.value.refreshing)
        assertEquals(1, repository.refreshes)
    }

    @Test
    fun reopeningSameAccountRetainsPendingRefreshAcrossRotation() {
        val server = CompletableDeferred<InventoryRefreshResult>()
        repository.fetch = { server.await() }
        overview.open("a", true)
        overview.open("a", true)
        overview.refresh()
        assertEquals(1, repository.inventoryReads)
        assertEquals(1, repository.refreshes)
        assertTrue(overview.state.value.refreshing)
        server.complete(InventoryRefreshResult.Updated(cached))
        assertEquals(cached, overview.state.value.inventory)
    }

    @Test
    fun offlineWithoutInventoryStillShowsSavedSongsWithoutInventingATotal() {
        repository.read = { null }
        overview.open("a", false)
        overview.refresh()
        assertEquals(saved, overview.state.value.local)
        assertNull(overview.state.value.inventory)
        assertFalse(overview.state.value.loadingInventory)
        assertFalse(overview.state.value.refreshing)
        assertEquals(0, repository.refreshes)
    }

    @Test
    fun failedRefreshRetainsCachedIdsAndTimestampThenRecoversOnReconnect() {
        repository.fetch = { InventoryRefreshResult.Failed(IOException()) }
        overview.open("a", true)
        assertTrue(overview.state.value.refreshFailed)
        assertEquals(cached, overview.state.value.inventory)

        val fresh = cached.copy(checkedAt = 56)
        repository.fetch = { InventoryRefreshResult.Updated(fresh) }
        overview.networkChanged(false)
        overview.networkChanged(true)
        overview.networkChanged(true)
        assertEquals(2, repository.refreshes)
        assertEquals(fresh, overview.state.value.inventory)
        assertFalse(overview.state.value.refreshFailed)
    }

    @Test
    fun failedDiskReadCanRecoverFromSuccessfulServerRefresh() {
        repository.read = { throw IOException() }
        overview.open("a", true)
        assertEquals(cached, overview.state.value.inventory)
        assertFalse(overview.state.value.inventoryFailed)
        assertFalse(overview.state.value.refreshFailed)
    }

    @Test
    fun accountSwitchDiscardsLateLocalAndInventoryResults() {
        val oldLocal = CompletableDeferred<LocalLibrarySummary>()
        val oldInventory = CompletableDeferred<InventorySnapshot?>()
        repository.scan = { account ->
            if (account == "a") withContext(NonCancellable) { oldLocal.await() }
            else LocalLibrarySummary(account, emptySet(), 0)
        }
        repository.read = { account ->
            if (account == "a") withContext(NonCancellable) { oldInventory.await() }
            else InventorySnapshot(account, emptySet(), 34)
        }
        overview.open("a", false)
        overview.open("b", false)
        val current = overview.state.value
        assertEquals("b", current.local?.accountId)
        assertEquals("b", current.inventory?.accountId)

        oldLocal.complete(saved)
        oldInventory.complete(cached)
        assertEquals(current, overview.state.value)
    }

    @Test
    fun accountSwitchDiscardsLateRefreshAndClearsPreviousErrors() {
        val server = CompletableDeferred<InventoryRefreshResult>()
        repository.fetch = { withContext(NonCancellable) { server.await() } }
        overview.open("a", true)
        overview.open("b", false)
        val current = overview.state.value
        server.complete(InventoryRefreshResult.Updated(cached))
        assertEquals(current, overview.state.value)
        assertFalse(current.refreshing)
        assertFalse(current.refreshFailed)
    }

    @Test
    fun downloadNotificationsDuringScanAreCoalescedIntoOneAdditionalScan() {
        val disk = CompletableDeferred<LocalLibrarySummary>()
        repository.scan = { disk.await() }
        overview.open("a", false)
        repeat(10) { overview.localChanged() }
        assertEquals(1, repository.localReads)
        val newer = saved.copy(availableSongIds = setOf("saved", "new"), audioBytes = 200)
        repository.scan = { newer }
        disk.complete(saved)
        assertEquals(2, repository.localReads)
        assertEquals(newer, overview.state.value.local)
        assertFalse(overview.state.value.loadingLocal)
    }

    @Test
    fun localScanFailureIsVisibleAndManualRefreshRecovers() {
        overview.open("a", false)
        repository.scan = { throw IOException() }
        overview.localChanged()
        assertTrue(overview.state.value.localFailed)
        assertFalse(overview.state.value.loadingLocal)
        repository.scan = { saved }
        overview.refresh()
        assertFalse(overview.state.value.localFailed)
        assertEquals(saved, overview.state.value.local)
    }

    @Test
    fun signingOutClearsAllAccountDataAndDoesNotStartMoreWork() {
        overview.open("a", true)
        overview.open(null, true)
        val reads = repository.localReads
        overview.refresh()
        overview.localChanged()
        assertEquals(DownloadsState(connected = true), overview.state.value)
        assertEquals(reads, repository.localReads)
    }

    private inner class FakeRepository : DownloadsRepository {
        var localReads = 0
        var inventoryReads = 0
        var refreshes = 0
        var scan: suspend (String) -> LocalLibrarySummary = { saved.copy(accountId = it) }
        var read: suspend (String) -> InventorySnapshot? = { cached.copy(accountId = it) }
        var fetch: suspend (String) -> InventoryRefreshResult = {
            InventoryRefreshResult.Updated(cached.copy(accountId = it))
        }

        override suspend fun local(accountId: String): LocalLibrarySummary {
            localReads++
            return scan(accountId)
        }

        override suspend fun inventory(accountId: String): InventorySnapshot? {
            inventoryReads++
            return read(accountId)
        }

        override suspend fun refresh(accountId: String): InventoryRefreshResult {
            refreshes++
            return fetch(accountId)
        }
    }
}
