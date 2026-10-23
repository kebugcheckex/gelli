package com.dkanada.gramophone.util

import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.model.api.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ServerSongInventoryTest {
    private fun id(value: Int) = UUID(0, value.toLong())

    @Test
    fun requestsUseExplicitAccountAndAllMusicLibrariesInsteadOfBrowsingSelection() = runBlocking {
        val music = BaseItemDto(id = id(1), type = BaseItemKind.COLLECTION_FOLDER, collectionType = CollectionType.MUSIC)
        val otherMusic = BaseItemDto(id = id(2), type = BaseItemKind.COLLECTION_FOLDER, collectionType = CollectionType.MUSIC)
        val books = BaseItemDto(id = id(3), type = BaseItemKind.COLLECTION_FOLDER, collectionType = CollectionType.BOOKS)
        val requests = mutableListOf<UUID?>()
        QueryUtil.currentLibrary = QueryUtil.Library(id(99).toString(), "Other", "music")
        try {
            val ids = ServerSongInventory.loadSongIds(id(10)) { request ->
                assertEquals(id(10), request.userId)
                assertEquals(true, request.enableTotalRecordCount)
                assertEquals(listOf(ItemSortBy.SORT_NAME), request.sortBy)
                assertEquals(listOf(SortOrder.ASCENDING), request.sortOrder)
                requests.add(request.parentId)
                if (request.parentId == null) {
                    assertEquals(false, request.recursive)
                    assertEquals(listOf(BaseItemKind.COLLECTION_FOLDER), request.includeItemTypes)
                    BaseItemDtoQueryResult(listOf(music, books, otherMusic, music), 4, 0)
                } else {
                    assertEquals(true, request.recursive)
                    assertEquals(listOf(BaseItemKind.AUDIO), request.includeItemTypes)
                    val start = request.startIndex!!
                    if (request.parentId == music.id) {
                        // Server may return fewer items than requested; continue to the reported total.
                        BaseItemDtoQueryResult(listOf(BaseItemDto(id = id(20 + start), type = BaseItemKind.AUDIO)), 2, start)
                    } else {
                        BaseItemDtoQueryResult(listOf(BaseItemDto(id = id(20), type = BaseItemKind.AUDIO)), 1, 0)
                    }
                }
            }
            assertEquals(setOf(id(20), id(21)).map { it.toString().replace("-", "") }.toSet(), ids)
            assertEquals(listOf(null, music.id, music.id, otherMusic.id), requests)
        } finally {
            QueryUtil.currentLibrary = null
        }
    }

    @Test
    fun noMusicLibrariesIsASuccessfulEmptyInventory() = runBlocking {
        val ids = ServerSongInventory.loadSongIds(id(10)) {
            BaseItemDtoQueryResult(listOf(BaseItemDto(id = id(1), type = BaseItemKind.COLLECTION_FOLDER,
                collectionType = CollectionType.BOOKS)), 1, 0)
        }
        assertTrue(ids.isEmpty())
    }

    @Test
    fun emptyMusicLibraryIsASuccessfulEmptyInventory() = runBlocking {
        val ids = ServerSongInventory.loadSongIds(id(10)) {
            if (it.parentId == null) {
                BaseItemDtoQueryResult(listOf(BaseItemDto(id = id(1), type = BaseItemKind.COLLECTION_FOLDER,
                    collectionType = CollectionType.MUSIC)), 1, 0)
            } else BaseItemDtoQueryResult(emptyList(), 0, 0)
        }
        assertTrue(ids.isEmpty())
    }

    @Test
    fun pagesAdvanceByReturnedRowsAndKeepDuplicatesUntilFinalDeduplication() = runBlocking {
        val starts = mutableListOf<Int>()
        val items = loadInventoryPages(2) { start, _ ->
            starts.add(start)
            InventoryPage(if (start == 0) listOf("a", "a") else listOf("b"), 3, start)
        }
        assertEquals(listOf(0, 2), starts)
        assertEquals(listOf("a", "a", "b"), items)
    }

    @Test
    fun truncatedChangedMisindexedAndOversizedPagesAreRejected() = runBlocking {
        val invalid = listOf(
            InventoryPage(emptyList(), 2, 1),
            InventoryPage(listOf("b"), 3, 1),
            InventoryPage(listOf("b"), 2, 0),
            InventoryPage(listOf("b", "c"), 2, 1),
            InventoryPage(listOf("b"), -1, 1)
        )
        for (bad in invalid) {
            try {
                loadInventoryPages(1) { start, _ ->
                    if (start == 0) InventoryPage(listOf("a"), 2, 0) else bad
                }
                fail("Expected invalid page to be rejected: $bad")
            } catch (_: IllegalStateException) {
                // Old snapshot must survive these responses.
            }
        }
    }
}
