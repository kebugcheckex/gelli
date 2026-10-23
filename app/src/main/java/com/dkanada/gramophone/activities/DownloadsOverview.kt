package com.dkanada.gramophone.activities

import com.dkanada.gramophone.database.InventorySnapshot
import com.dkanada.gramophone.util.InventoryRefreshResult
import com.dkanada.gramophone.util.LocalLibrarySummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal interface DownloadsRepository {
    suspend fun local(accountId: String): LocalLibrarySummary
    suspend fun inventory(accountId: String): InventorySnapshot?
    suspend fun refresh(accountId: String): InventoryRefreshResult
}

internal data class DownloadsState(
    val accountId: String? = null,
    val connected: Boolean = false,
    val local: LocalLibrarySummary? = null,
    val inventory: InventorySnapshot? = null,
    val loadingLocal: Boolean = false,
    val loadingInventory: Boolean = false,
    val refreshing: Boolean = false,
    val localFailed: Boolean = false,
    val inventoryFailed: Boolean = false,
    val refreshFailed: Boolean = false
)

/** Main-thread screen state, retained across rotation by DownloadsViewModel. */
internal class DownloadsOverview(private val scope: CoroutineScope, private val repository: DownloadsRepository) {
    private val mutableState = MutableStateFlow(DownloadsState())
    val state = mutableState.asStateFlow()
    private var opened = false
    private var generation = 0
    private var localJob: Job? = null
    private var inventoryJob: Job? = null
    private var refreshJob: Job? = null
    private var localDirty = false

    fun open(accountId: String?, connected: Boolean) {
        if (opened && state.value.accountId == accountId) {
            networkChanged(connected)
            localChanged()
            return
        }
        opened = true
        val current = ++generation
        localJob?.cancel()
        localJob = null
        inventoryJob?.cancel()
        refreshJob?.cancel()
        localDirty = false
        mutableState.value = DownloadsState(accountId, connected,
            loadingLocal = accountId != null, loadingInventory = accountId != null)
        if (accountId == null) return
        localChanged()
        inventoryJob = scope.launch {
            try {
                val snapshot = repository.inventory(accountId)
                if (generation == current) mutableState.value = state.value.copy(inventory = snapshot)
            } catch (err: CancellationException) {
                throw err
            } catch (_: Exception) {
                if (generation == current) mutableState.value = state.value.copy(inventoryFailed = true)
            } finally {
                if (generation == current) mutableState.value = state.value.copy(loadingInventory = false)
            }
            if (generation == current && state.value.connected) refreshInventory()
        }
    }

    /** Coalesce notifications while scanning disk; do not let an older scan overwrite a newer one. */
    fun localChanged() {
        val accountId = state.value.accountId ?: return
        if (localJob?.isActive == true) {
            localDirty = true
            return
        }
        val current = generation
        localJob = scope.launch {
            do {
                localDirty = false
                mutableState.value = state.value.copy(loadingLocal = true)
                try {
                    val summary = repository.local(accountId)
                    if (generation == current) mutableState.value = state.value.copy(local = summary, localFailed = false)
                } catch (err: CancellationException) {
                    throw err
                } catch (_: Exception) {
                    if (generation == current) mutableState.value = state.value.copy(localFailed = true)
                } finally {
                    if (generation == current) mutableState.value = state.value.copy(loadingLocal = false)
                }
            } while (generation == current && localDirty)
        }
    }

    fun networkChanged(connected: Boolean) {
        val reconnected = !state.value.connected && connected
        mutableState.value = state.value.copy(connected = connected)
        if (reconnected) refresh()
    }

    fun refresh() {
        localChanged()
        refreshInventory()
    }

    private fun refreshInventory() {
        val accountId = state.value.accountId ?: return
        if (!state.value.connected || state.value.loadingInventory || state.value.refreshing) return
        val current = generation
        mutableState.value = state.value.copy(refreshing = true, refreshFailed = false)
        refreshJob = scope.launch {
            try {
                when (val result = repository.refresh(accountId)) {
                    is InventoryRefreshResult.Updated -> if (generation == current) {
                        mutableState.value = state.value.copy(inventory = result.snapshot, inventoryFailed = false)
                    }
                    is InventoryRefreshResult.Failed -> if (generation == current) {
                        mutableState.value = state.value.copy(refreshFailed = true)
                    }
                    InventoryRefreshResult.Superseded -> Unit
                }
            } catch (err: CancellationException) {
                throw err
            } catch (_: Exception) {
                if (generation == current) mutableState.value = state.value.copy(refreshFailed = true)
            } finally {
                if (generation == current) mutableState.value = state.value.copy(refreshing = false)
            }
        }
    }
}
