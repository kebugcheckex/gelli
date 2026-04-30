package com.dkanada.gramophone.util

import org.jellyfin.apiclient.model.entities.SortOrder as LegacySortOrder
import org.jellyfin.apiclient.model.querying.ItemQuery
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * JVM unit tests for the pure request-building / mapping logic in [QueryUtil].
 *
 * These cover the surfaces that have produced regressions during the Kotlin SDK
 * migration (parentId scoping, sort/order mapping, ID format conversion). They
 * intentionally do not exercise the network path — only the deterministic
 * translation from app-level query objects into SDK request objects.
 */
class QueryUtilTest {

    private val library = QueryUtil.Library(
        id = "11111111111111111111111111111111",
        name = "Music",
        collectionType = "music"
    )

    private val userId = "22222222222222222222222222222222"

    // --- Regression: playlist queries must NOT inherit currentLibrary as parentId.
    // Jellyfin stores playlists outside any library, so scoping by the music
    // library returns zero results. See commit 31c823f8 / followup fix.

    @Test
    fun playlistsRequest_doesNotScopeByCurrentLibrary() {
        val request = QueryUtil.playlistsRequest(
            query = ItemQuery(),
            userId = userId,
            pageSize = 50,
            library = library
        )

        assertNull("playlist request must not inherit library parentId", request.parentId)
        assertEquals(listOf(BaseItemKind.PLAYLIST), request.includeItemTypes)
        assertEquals(true, request.recursive)
    }

    @Test
    fun playlistsRequest_isStableWithNoLibrary() {
        val request = QueryUtil.playlistsRequest(
            query = ItemQuery(),
            userId = userId,
            pageSize = 50,
            library = null
        )

        assertNull(request.parentId)
        assertEquals(listOf(BaseItemKind.PLAYLIST), request.includeItemTypes)
    }

    // --- applyProperties: the policy that scopes browse queries to the current library.

    @Test
    fun applyProperties_setsParentIdToCurrentLibrary_whenUnset() {
        val query = ItemQuery()
        QueryUtil.applyProperties(query, userId, pageSize = 50, library = library)

        assertEquals(library.id, query.parentId)
        assertEquals(userId, query.userId)
        assertTrue(query.recursive)
        assertEquals(50, query.limit)
    }

    @Test
    fun applyProperties_doesNotOverrideExplicitParentId() {
        val query = ItemQuery().apply { parentId = "explicit-parent" }
        QueryUtil.applyProperties(query, userId, pageSize = 50, library = library)

        assertEquals("explicit-parent", query.parentId)
    }

    @Test
    fun applyProperties_skipsLibraryScope_whenArtistFilterPresent() {
        val query = ItemQuery().apply { artistIds = arrayOf("artist-a") }
        QueryUtil.applyProperties(query, userId, pageSize = 50, library = library)

        // Artist-scoped queries must not be re-scoped to a library parent.
        assertNull(query.parentId)
    }

    @Test
    fun applyProperties_skipsLimit_whenArtistFilterPresent() {
        val query = ItemQuery().apply { artistIds = arrayOf("artist-a") }
        QueryUtil.applyProperties(query, userId, pageSize = 50, library = library)

        // Artist queries should not be paginated by the default page size.
        assertTrue("artist query must not have default page size applied", query.limit == null || query.limit == 0)
    }

    // --- itemQueryToRequest: the full ItemQuery → GetItemsRequest translation.

    @Test
    fun itemQueryToRequest_scopesAlbumsToCurrentLibrary() {
        val query = ItemQuery()
        val request = QueryUtil.itemQueryToRequest(query, userId, pageSize = 50, library = library)

        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), request.parentId)
        assertEquals(UUID.fromString("22222222-2222-2222-2222-222222222222"), request.userId)
        assertEquals(true, request.recursive)
        assertEquals(50, request.limit)
    }

    @Test
    fun itemQueryToRequest_propagatesFavoriteFilter() {
        val query = ItemQuery().apply {
            filters = arrayOf(org.jellyfin.apiclient.model.querying.ItemFilter.IsFavorite)
        }
        val request = QueryUtil.itemQueryToRequest(query, userId, pageSize = 50, library = library)

        assertEquals(true, request.isFavorite)
    }

    @Test
    fun itemQueryToRequest_ignoresInvalidParentId() {
        val query = ItemQuery().apply { parentId = "not-a-uuid" }
        val request = QueryUtil.itemQueryToRequest(query, userId, pageSize = 50, library = library)

        // Invalid parentId should be dropped rather than crash, so the request
        // still reaches the server (the warning is logged separately).
        assertNull(request.parentId)
    }

    // --- mapSortBy

    @Test
    fun mapSortBy_translatesKnownValues() {
        assertEquals(ItemSortBy.SORT_NAME, QueryUtil.mapSortBy("SortName"))
        assertEquals(ItemSortBy.ALBUM, QueryUtil.mapSortBy("Album"))
        assertEquals(ItemSortBy.ALBUM_ARTIST, QueryUtil.mapSortBy("AlbumArtist"))
        assertEquals(ItemSortBy.PRODUCTION_YEAR, QueryUtil.mapSortBy("ProductionYear"))
        assertEquals(ItemSortBy.DATE_CREATED, QueryUtil.mapSortBy("DateCreated"))
        assertEquals(ItemSortBy.RANDOM, QueryUtil.mapSortBy("Random"))
        assertEquals(ItemSortBy.PLAY_COUNT, QueryUtil.mapSortBy("PlayCount"))
    }

    @Test
    fun mapSortBy_returnsNullForUnknown() {
        assertNull(QueryUtil.mapSortBy(null))
        assertNull(QueryUtil.mapSortBy(""))
        assertNull(QueryUtil.mapSortBy("NotARealSort"))
    }

    // --- mapSortOrder

    @Test
    fun mapSortOrder_translatesAscDesc() {
        assertEquals(listOf(SortOrder.ASCENDING), QueryUtil.mapSortOrder(LegacySortOrder.Ascending))
        assertEquals(listOf(SortOrder.DESCENDING), QueryUtil.mapSortOrder(LegacySortOrder.Descending))
    }

    @Test
    fun mapSortOrder_returnsEmptyForNull() {
        assertEquals(emptyList<SortOrder>(), QueryUtil.mapSortOrder(null))
    }

    // --- UUID round-trip

    @Test
    fun toUuidOrNull_acceptsDashedAndDashlessForms() {
        val expected = UUID.fromString("aabbccdd-eeff-0011-2233-445566778899")

        assertEquals(expected, QueryUtil.toUuidOrNull("aabbccdd-eeff-0011-2233-445566778899"))
        assertEquals(expected, QueryUtil.toUuidOrNull("aabbccddeeff00112233445566778899"))
    }

    @Test
    fun toUuidOrNull_rejectsMalformedInput() {
        assertNull(QueryUtil.toUuidOrNull(null))
        assertNull(QueryUtil.toUuidOrNull(""))
        assertNull(QueryUtil.toUuidOrNull("   "))
        assertNull(QueryUtil.toUuidOrNull("not-a-uuid"))
        // Wrong length (31 chars).
        assertNull(QueryUtil.toUuidOrNull("aabbccddeeff0011223344556677889"))
    }

    @Test
    fun uuidToId_producesDashlessForm() {
        val uuid = UUID.fromString("aabbccdd-eeff-0011-2233-445566778899")
        assertEquals("aabbccddeeff00112233445566778899", QueryUtil.uuidToId(uuid))
    }

    @Test
    fun uuidToId_returnsEmptyForNull() {
        assertEquals("", QueryUtil.uuidToId(null))
    }
}
