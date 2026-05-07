package com.dkanada.gramophone.util

import com.dkanada.gramophone.model.SortMethod
import com.dkanada.gramophone.model.SortOrder as AppSortOrder
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
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
            startIndex = 0,
            userId = userId,
            pageSize = 50
        )

        assertNull("playlist request must not inherit library parentId", request.parentId)
        assertEquals(listOf(BaseItemKind.PLAYLIST), request.includeItemTypes)
        assertEquals(true, request.recursive)
    }

    @Test
    fun playlistsRequest_isStableWithNoLibrary() {
        val request = QueryUtil.playlistsRequest(
            startIndex = 0,
            userId = userId,
            pageSize = 50
        )

        assertNull(request.parentId)
        assertEquals(listOf(BaseItemKind.PLAYLIST), request.includeItemTypes)
    }

    @Test
    fun playlistsRequest_passesStartIndex() {
        val request = QueryUtil.playlistsRequest(startIndex = 25, userId = userId, pageSize = 50)

        assertEquals(25, request.startIndex)
    }

    // --- albumsRequest: library scope, sort, paging ---

    @Test
    fun albumsRequest_scopesToCurrentLibrary() {
        val request = QueryUtil.albumsRequest(
            sortMethod = null,
            sortOrder = null,
            startIndex = 0,
            userId = userId,
            pageSize = 50,
            library = library
        )

        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), request.parentId)
        assertEquals(UUID.fromString("22222222-2222-2222-2222-222222222222"), request.userId)
        assertEquals(listOf(BaseItemKind.MUSIC_ALBUM), request.includeItemTypes)
        assertEquals(true, request.recursive)
        assertEquals(50, request.limit)
    }

    @Test
    fun albumsRequest_hasNullParentId_whenNoLibrary() {
        val request = QueryUtil.albumsRequest(
            sortMethod = null,
            sortOrder = null,
            startIndex = 0,
            userId = userId,
            pageSize = 50,
            library = null
        )

        assertNull(request.parentId)
    }

    @Test
    fun albumsRequest_passesStartIndex() {
        val request = QueryUtil.albumsRequest(
            sortMethod = null,
            sortOrder = null,
            startIndex = 100,
            userId = userId,
            pageSize = 50,
            library = library
        )

        assertEquals(100, request.startIndex)
    }

    @Test
    fun albumsRequest_mapsSortFieldsAndOrder() {
        val request = QueryUtil.albumsRequest(
            sortMethod = SortMethod.NAME,
            sortOrder = AppSortOrder.ASCENDING,
            startIndex = 0,
            userId = userId,
            pageSize = 50,
            library = library
        )

        assertEquals(listOf(ItemSortBy.SORT_NAME), request.sortBy)
        assertEquals(listOf(SortOrder.ASCENDING), request.sortOrder)
    }

    @Test
    fun albumsRequest_hasEmptySortLists_whenNullSort() {
        val request = QueryUtil.albumsRequest(
            sortMethod = null,
            sortOrder = null,
            startIndex = 0,
            userId = userId,
            pageSize = 50,
            library = library
        )

        assertTrue(request.sortBy.isNullOrEmpty())
        assertTrue(request.sortOrder.isNullOrEmpty())
    }

    // --- artistsRequest: library scope, sort, paging, genres field ---

    @Test
    fun artistsRequest_scopesToCurrentLibrary() {
        val request = QueryUtil.artistsRequest(
            sortMethod = null,
            sortOrder = null,
            startIndex = 0,
            userId = userId,
            pageSize = 50,
            library = library
        )

        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), request.parentId)
        assertEquals(50, request.limit)
    }

    @Test
    fun artistsRequest_passesStartIndex() {
        val request = QueryUtil.artistsRequest(
            sortMethod = null,
            sortOrder = null,
            startIndex = 50,
            userId = userId,
            pageSize = 50,
            library = library
        )

        assertEquals(50, request.startIndex)
    }

    // --- songsRequest: library scope, sort, paging, favorites ---

    @Test
    fun songsRequest_scopesToCurrentLibrary() {
        val request = QueryUtil.songsRequest(
            sortMethod = null,
            sortOrder = null,
            startIndex = 0,
            onlyFavorites = false,
            userId = userId,
            pageSize = 50,
            library = library
        )

        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), request.parentId)
        assertEquals(listOf(BaseItemKind.AUDIO), request.includeItemTypes)
        assertEquals(true, request.recursive)
        assertNull(request.isFavorite)
    }

    @Test
    fun songsRequest_setsFavoriteFlag_whenOnlyFavorites() {
        val request = QueryUtil.songsRequest(
            sortMethod = null,
            sortOrder = null,
            startIndex = 0,
            onlyFavorites = true,
            userId = userId,
            pageSize = 50,
            library = library
        )

        assertEquals(true, request.isFavorite)
    }

    @Test
    fun songsRequest_passesStartIndex() {
        val request = QueryUtil.songsRequest(
            sortMethod = null,
            sortOrder = null,
            startIndex = 75,
            onlyFavorites = false,
            userId = userId,
            pageSize = 50,
            library = library
        )

        assertEquals(75, request.startIndex)
    }

    @Test
    fun songsRequest_mapsSortDescending() {
        val request = QueryUtil.songsRequest(
            sortMethod = SortMethod.ADDED,
            sortOrder = AppSortOrder.DESCENDING,
            startIndex = 0,
            onlyFavorites = false,
            userId = userId,
            pageSize = 50,
            library = library
        )

        assertEquals(listOf(ItemSortBy.DATE_CREATED), request.sortBy)
        assertEquals(listOf(SortOrder.DESCENDING), request.sortOrder)
    }

    // --- genresRequest: library scope ---

    @Test
    fun genresRequest_scopesToCurrentLibrary() {
        val request = QueryUtil.genresRequest(
            startIndex = 0,
            userId = userId,
            pageSize = 50,
            library = library
        )

        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), request.parentId)
        assertEquals(listOf(BaseItemKind.MUSIC_GENRE), request.includeItemTypes)
        assertEquals(50, request.limit)
    }

    // --- mapSortOrder ---

    @Test
    fun mapSortOrder_translatesAscDesc() {
        assertEquals(listOf(SortOrder.ASCENDING), QueryUtil.mapSortOrder(AppSortOrder.ASCENDING))
        assertEquals(listOf(SortOrder.DESCENDING), QueryUtil.mapSortOrder(AppSortOrder.DESCENDING))
    }

    @Test
    fun mapSortOrder_returnsEmptyForNull() {
        assertEquals(emptyList<SortOrder>(), QueryUtil.mapSortOrder(null))
    }

    // --- mapSortBy ---

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

    // --- albumSongsRequest: album scoping, sort, fields ---

    @Test
    fun albumSongsRequest_scopesToAlbumId() {
        val albumId = "aabbccddeeff00112233445566778899"
        val request = QueryUtil.albumSongsRequest(albumId, userId)

        assertEquals(listOf(UUID.fromString("aabbccdd-eeff-0011-2233-445566778899")), request.albumIds)
        assertNull("album songs must not scope by parentId", request.parentId)
        assertEquals(listOf(BaseItemKind.AUDIO), request.includeItemTypes)
        assertEquals(true, request.recursive)
    }

    @Test
    fun albumSongsRequest_includesMediaSources() {
        val request = QueryUtil.albumSongsRequest("aabbccddeeff00112233445566778899", userId)

        assertTrue(request.fields?.contains(ItemFields.MEDIA_SOURCES) == true)
    }

    @Test
    fun albumSongsRequest_sortsByDiscThenTrack() {
        val request = QueryUtil.albumSongsRequest("aabbccddeeff00112233445566778899", userId)

        assertEquals(
            listOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER),
            request.sortBy
        )
    }

    @Test
    fun albumSongsRequest_convertsUserId() {
        val request = QueryUtil.albumSongsRequest("aabbccddeeff00112233445566778899", userId)

        assertEquals(UUID.fromString("22222222-2222-2222-2222-222222222222"), request.userId)
    }

    // --- UUID round-trip ---

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
