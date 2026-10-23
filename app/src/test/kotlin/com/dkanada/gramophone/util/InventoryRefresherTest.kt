package com.dkanada.gramophone.util

import com.dkanada.gramophone.database.InventorySnapshot
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class InventoryRefresherTest {
    private val coordinator = InventoryRefreshCoordinator()
    private var selected = "a"
    private val old = InventorySnapshot("a", setOf("old"), 12)
    private val snapshots = mutableMapOf("a" to old)

    private fun refresher(load: suspend (String) -> Set<String>) = InventoryRefresher(
        coordinator, { selected == it }, load, { snapshots[it.accountId] = it }, { 123L }
    )

    @Test
    fun successReplacesIdsAndTimestampTogether() = runBlocking {
        val result = refresher { setOf("new", "other") }.refresh("a")
        assertTrue(result is InventoryRefreshResult.Updated)
        assertEquals(InventorySnapshot("a", setOf("new", "other"), 123), snapshots["a"])
    }

    @Test
    fun successfulEmptyInventoryReplacesOldSnapshot() = runBlocking {
        assertTrue(refresher { emptySet() }.refresh("a") is InventoryRefreshResult.Updated)
        assertEquals(InventorySnapshot("a", emptySet(), 123), snapshots["a"])
    }

    @Test
    fun firstAndLaterPageFailuresPreserveOldSnapshot() = runBlocking {
        for (failAt in listOf(0, 1)) {
            val result = refresher {
                loadInventoryPages(1) { start, _ ->
                    if (start == failAt) throw IOException("network unavailable")
                    InventoryPage(listOf("new"), 2, start)
                }.toSet()
            }.refresh("a")
            assertTrue(result is InventoryRefreshResult.Failed)
            assertEquals(old, snapshots["a"])
        }
    }

    @Test
    fun invalidPaginationPreservesOldSnapshot() = runBlocking {
        val result = refresher {
            loadInventoryPages<String>(1) { start, _ -> InventoryPage(emptyList(), 2, start) }.toSet()
        }.refresh("a")
        assertTrue(result is InventoryRefreshResult.Failed)
        assertEquals(old, snapshots["a"])
    }

    @Test
    fun newerRefreshWinsEvenWhenOlderFinishesLast() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val older = async {
            refresher { started.complete(Unit); finish.await(); setOf("older") }.refresh("a")
        }
        started.await()
        assertTrue(refresher { setOf("newer") }.refresh("a") is InventoryRefreshResult.Updated)
        finish.complete(Unit)
        assertEquals(InventoryRefreshResult.Superseded, older.await())
        assertEquals(setOf("newer"), snapshots["a"]!!.songIds)
    }

    @Test
    fun failedNewerRefreshDoesNotAllowOlderRequestToOverwriteSnapshot() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val older = async {
            refresher { started.complete(Unit); finish.await(); setOf("older") }.refresh("a")
        }
        started.await()
        assertTrue(refresher { throw IOException() }.refresh("a") is InventoryRefreshResult.Failed)
        finish.complete(Unit)
        assertEquals(InventoryRefreshResult.Superseded, older.await())
        assertEquals(old, snapshots["a"])
    }

    @Test
    fun switchingAwayAndBackInvalidatesInFlightRefresh() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val pending = async {
            refresher { started.complete(Unit); finish.await(); setOf("stale") }.refresh("a")
        }
        started.await()
        coordinator.changeAccount { selected = "b" }
        coordinator.changeAccount { selected = "a" }
        finish.complete(Unit)
        assertEquals(InventoryRefreshResult.Superseded, pending.await())
        assertEquals(old, snapshots["a"])
        assertTrue(refresher { setOf("fresh") }.refresh("a") is InventoryRefreshResult.Updated)
    }

    @Test
    fun switchingAccountsCannotPublishOldIdsIntoEitherAccount() = runBlocking {
        val result = refresher {
            coordinator.changeAccount { selected = "b" }
            setOf("a-song")
        }.refresh("a")
        assertEquals(InventoryRefreshResult.Superseded, result)
        assertEquals(old, snapshots["a"])
        assertNull(snapshots["b"])
        refresher { setOf("b-song") }.refresh("b")
        assertEquals(InventorySnapshot("b", setOf("b-song"), 123), snapshots["b"])
        assertEquals(old, snapshots["a"])
    }

    @Test
    fun nonselectedAccountDoesNotFetch() = runBlocking {
        val result = refresher { error("Must not fetch") }.refresh("b")
        assertEquals(InventoryRefreshResult.Superseded, result)
    }

    @Test
    fun delayedDispatchCannotMakeAnOlderRequestTheNewest() = runBlocking {
        val queued = coordinator.begin("a")
        refresher { setOf("newer") }.refresh("a")
        val result = refresher { error("Superseded request must not fetch") }.refresh("a", queued)
        assertEquals(InventoryRefreshResult.Superseded, result)
        assertEquals(setOf("newer"), snapshots["a"]!!.songIds)
    }

    @Test
    fun errorFromPreviousAccountIsReportedAsSuperseded() = runBlocking {
        val result = refresher {
            coordinator.changeAccount { selected = "b" }
            throw IOException("Previous account failed")
        }.refresh("a")
        assertEquals(InventoryRefreshResult.Superseded, result)
        assertEquals(old, snapshots["a"])
    }

    @Test
    fun cancellationPropagatesAndPreservesOldSnapshot() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val pending = async {
            refresher { started.complete(Unit); awaitCancellation() }.refresh("a")
        }
        started.await()
        pending.cancelAndJoin()
        assertTrue(pending.isCancelled)
        assertEquals(old, snapshots["a"])
    }

    @Test
    fun cancellationStillPreventsPublicationIfLoaderReturnsAfterCancellation() = runBlocking {
        val pending = launch {
            refresher {
                currentCoroutineContext().cancel()
                setOf("cancelled")
            }.refresh("a")
        }
        pending.join()
        assertTrue(pending.isCancelled)
        assertEquals(old, snapshots["a"])
    }
}
