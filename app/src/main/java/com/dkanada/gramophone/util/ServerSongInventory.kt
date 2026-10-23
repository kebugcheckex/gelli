package com.dkanada.gramophone.util

import com.dkanada.gramophone.App
import com.dkanada.gramophone.database.InventorySnapshot
import com.dkanada.gramophone.mapper.SdkMapperUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemDtoQueryResult
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

/** Account-wide music inventory. Refresh failures never become a zero-song snapshot. */
object ServerSongInventory {
    private const val PAGE_SIZE = 500
    private val coordinator = InventoryRefreshCoordinator()

    /** Account selection and snapshot publication share a lock, including switching away and back. */
    @JvmStatic
    fun changeAccount(update: Runnable) = coordinator.changeAccount { update.run() }

    suspend fun read(accountId: String): InventorySnapshot? = withContext(Dispatchers.IO) {
        App.getDatabase().songInventoryDao().read(accountId)
    }

    suspend fun refresh(accountId: String): InventoryRefreshResult {
        // Assign order before dispatch: IO scheduling must not make an older request the newest.
        val ticket = coordinator.begin(accountId)
        return withContext(Dispatchers.IO) {
            InventoryRefresher(
                coordinator = coordinator,
                isCurrent = { LocalLibrary.getCurrentAccountId() == it },
                load = ::fetchSongIds,
                replace = { App.getDatabase().songInventoryDao().replace(it) }
            ).refresh(accountId, ticket)
        }
    }

    private suspend fun fetchSongIds(accountId: String): Set<String> {
        val user = checkNotNull(App.getDatabase().userDao().getUser(accountId)) { "Account unavailable" }
        // Capture credentials from this account's row; never combine a global client with another user.
        val api = checkNotNull(JellyfinSdkSession.createApiForUser(user)) { "Account credentials unavailable" }
        val userId = QueryUtil.toUuidOrNull(user.jellyfinUserId)
        checkNotNull(userId) { "Invalid account user ID" }

        return loadSongIds(userId) { request ->
            val result by api.itemsApi.getItems(request)
            result
        }
    }

    internal suspend fun loadSongIds(
        userId: UUID,
        fetch: suspend (GetItemsRequest) -> BaseItemDtoQueryResult
    ): Set<String> {
        suspend fun <T> pages(parentId: UUID?, libraries: Boolean, map: (BaseItemDto) -> T): List<T> =
            loadInventoryPages(PAGE_SIZE) { start, limit ->
                val result = fetch(request(userId, parentId, libraries, start, limit))
                InventoryPage(result.items.map(map), result.totalRecordCount, result.startIndex)
            }

        val libraries = pages(null, true) { it }.filter { it.collectionType == CollectionType.MUSIC }
            .distinctBy { it.id }
        val ids = mutableSetOf<String>()
        for (library in libraries) {
            ids.addAll(pages(library.id, false) { SdkMapperUtil.uuidToId(it.id) })
        }
        return ids
    }

    internal fun request(userId: UUID, parentId: UUID?, libraries: Boolean, start: Int, limit: Int) =
        GetItemsRequest(
            userId = userId,
            parentId = parentId,
            includeItemTypes = listOf(if (libraries) BaseItemKind.COLLECTION_FOLDER else BaseItemKind.AUDIO),
            recursive = !libraries,
            startIndex = start,
            limit = limit,
            enableTotalRecordCount = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING)
        )
}
