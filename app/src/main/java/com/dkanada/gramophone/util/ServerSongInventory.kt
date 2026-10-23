package com.dkanada.gramophone.util

import com.dkanada.gramophone.App
import com.dkanada.gramophone.database.InventorySnapshot
import com.dkanada.gramophone.mapper.SdkMapperUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
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

        return loadSongIds(userId, views = {
            // CollectionFolder queries can return no rows even when the user has music libraries.
            // Include libraries hidden from the home screen: this inventory covers all accessible music.
            val result by api.userViewsApi.getUserViews(
                userId = userId, includeExternalContent = false, includeHidden = true)
            result
        }) { request ->
            val result by api.itemsApi.getItems(request)
            result
        }
    }

    internal suspend fun loadSongIds(
        userId: UUID,
        views: suspend () -> BaseItemDtoQueryResult,
        fetch: suspend (GetItemsRequest) -> BaseItemDtoQueryResult
    ): Set<String> {
        suspend fun <T> pages(parentId: UUID, map: (BaseItemDto) -> T): List<T> =
            loadInventoryPages(PAGE_SIZE) { start, limit ->
                val result = fetch(request(userId, parentId, start, limit))
                InventoryPage(result.items.map(map), result.totalRecordCount, result.startIndex)
            }

        // UserViews has no pagination; an incomplete response must not replace a good snapshot.
        val result = views()
        check(result.startIndex == 0 && result.totalRecordCount == result.items.size) { "Incomplete library views" }
        val libraries = result.items.filter { it.collectionType == CollectionType.MUSIC }
            .distinctBy { it.id }
        val ids = mutableSetOf<String>()
        for (library in libraries) {
            ids.addAll(pages(library.id) { SdkMapperUtil.uuidToId(it.id) })
        }
        return ids
    }

    internal fun request(userId: UUID, parentId: UUID, start: Int, limit: Int) =
        GetItemsRequest(
            userId = userId,
            parentId = parentId,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            recursive = true,
            startIndex = start,
            limit = limit,
            enableTotalRecordCount = true,
            sortBy = listOf(ItemSortBy.SORT_NAME),
            sortOrder = listOf(SortOrder.ASCENDING)
        )
}
