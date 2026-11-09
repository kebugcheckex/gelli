package com.dkanada.gramophone.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dkanada.gramophone.App
import com.dkanada.gramophone.interfaces.MediaCallback
import com.dkanada.gramophone.interfaces.SectionedCallback
import com.dkanada.gramophone.mapper.SdkMediaMapper
import com.dkanada.gramophone.mapper.SdkSongMapper
import com.dkanada.gramophone.model.Album
import com.dkanada.gramophone.model.Artist
import com.dkanada.gramophone.model.Genre
import com.dkanada.gramophone.model.Playlist
import com.dkanada.gramophone.model.Song
import com.dkanada.gramophone.model.SortMethod
import com.dkanada.gramophone.model.SortOrder as AppSortOrder
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.extensions.genresApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetAlbumArtistsRequest
import org.jellyfin.sdk.model.api.request.GetArtistsRequest
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

object QueryUtil {
    private const val TAG = "QueryUtil"

    data class Library(
        val id: String,
        val name: String
    )

    @JvmField
    var currentLibrary: Library? = null

    /** The user's music libraries. An items query for CollectionFolder returns no rows, so this reads the user views. */
    @JvmStatic
    fun getLibraries(callback: MediaCallback<Library>) {
        fetch("getLibraries", callback) { api ->
            val result by api.userViewsApi.getUserViews(userId = currentUserUuidOrNull())
            musicLibraries(result.items)
        }
    }

    internal fun musicLibraries(views: List<BaseItemDto>): List<Library> = views
        .filter { it.collectionType == CollectionType.MUSIC }
        .map { Library(id = uuidToId(it.id), name = it.name ?: "") }

    @JvmStatic
    fun getPlaylists(callback: MediaCallback<Playlist>) {
        getPlaylists(0, callback)
    }

    @JvmStatic
    fun getPlaylists(startIndex: Int, callback: MediaCallback<Playlist>) {
        fetchItems(
            request = playlistsRequest(startIndex, JellyfinSdkSession.getCurrentUserId(), defaultPageSize()),
            onErrorTag = "getPlaylists",
            mapper = SdkMediaMapper::toPlaylist,
            callback = callback
        )
    }

    /** Genres with songs in the current library. An items query for MusicGenre ignores parentId, so this reads /Genres. */
    @JvmStatic
    fun getGenres(startIndex: Int, callback: MediaCallback<Genre>) {
        fetch("getGenres", callback) { api ->
            val request = genresRequest(startIndex, JellyfinSdkSession.getCurrentUserId(), defaultPageSize(), currentLibrary)
            val result by api.genresApi.getGenres(request)
            result.items.map(SdkMediaMapper::toGenre)
        }
    }

    @JvmStatic
    fun searchItems(searchTerm: String, callback: MediaCallback<Any>) {
        fetchItems(
            request = GetItemsRequest(
                userId = currentUserUuidOrNull(),
                parentId = toUuidOrNull(currentLibrary?.id),
                searchTerm = searchTerm,
                includeItemTypes = listOf(BaseItemKind.MUSIC_ARTIST, BaseItemKind.MUSIC_ALBUM, BaseItemKind.AUDIO),
                fields = listOf(ItemFields.MEDIA_SOURCES),
                limit = 40,
                recursive = true
            ),
            onErrorTag = "searchItems",
            mapper = { item ->
                when (item.type) {
                    BaseItemKind.MUSIC_ARTIST -> SdkMediaMapper.toArtist(item)
                    BaseItemKind.MUSIC_ALBUM -> SdkMediaMapper.toAlbum(item)
                    else -> SdkSongMapper.fromItem(item)
                }
            },
            callback = callback
        )
    }

    @JvmStatic
    fun getAlbums(sortMethod: SortMethod?, sortOrder: AppSortOrder?, startIndex: Int, callback: MediaCallback<Album>) {
        fetchItems(
            request = albumsRequest(sortMethod, sortOrder, startIndex, JellyfinSdkSession.getCurrentUserId(), defaultPageSize(), currentLibrary),
            onErrorTag = "getAlbums",
            mapper = SdkMediaMapper::toAlbum,
            callback = callback
        )
    }

    /** People credited on individual tracks, including featured artists. */
    @JvmStatic
    fun getArtists(sortMethod: SortMethod?, sortOrder: AppSortOrder?, startIndex: Int, callback: MediaCallback<Artist>) {
        fetch("getArtists", callback) { api ->
            val request = artistsRequest(sortMethod, sortOrder, startIndex, JellyfinSdkSession.getCurrentUserId(), defaultPageSize(), currentLibrary)
            val result by api.artistsApi.getArtists(request)
            result.items.map(SdkMediaMapper::toArtist)
        }
    }

    /** Primary artists of albums. Distinct from [getArtists]. */
    @JvmStatic
    fun getAlbumArtists(sortMethod: SortMethod?, sortOrder: AppSortOrder?, startIndex: Int, callback: MediaCallback<Artist>) {
        fetch("getAlbumArtists", callback) { api ->
            val request = albumArtistsRequest(sortMethod, sortOrder, startIndex, JellyfinSdkSession.getCurrentUserId(), defaultPageSize(), currentLibrary)
            val result by api.artistsApi.getAlbumArtists(request)
            result.items.map(SdkMediaMapper::toArtist)
        }
    }

    /**
     * Every artist, or every album artist, sorted into letter sections on the device. The server
     * sorts Han names by code point, so the Pinyin order of the letter bar has to be built here.
     */
    @JvmStatic
    fun getSectionedArtists(albumArtists: Boolean, sortOrder: AppSortOrder?, callback: SectionedCallback<Artist>) {
        fetchValue("getSectionedArtists", SectionedList.empty(), callback::onLoadMedia) { api ->
            val userId = JellyfinSdkSession.getCurrentUserId()
            val items = if (albumArtists) {
                val result by api.artistsApi.getAlbumArtists(albumArtistsRequest(SortMethod.NAME, sortOrder, 0, userId, null, currentLibrary))
                result.items
            } else {
                val result by api.artistsApi.getArtists(artistsRequest(SortMethod.NAME, sortOrder, 0, userId, null, currentLibrary))
                result.items
            }

            AlphabetIndex.sort(items, sortOrder == AppSortOrder.DESCENDING, sectionedArtistKeys()).map(SdkMediaMapper::toArtist)
        }
    }

    /** Every album sorted into letter sections on the device, by name or by album artist. See [getSectionedArtists]. */
    @JvmStatic
    fun getSectionedAlbums(sortMethod: SortMethod, sortOrder: AppSortOrder?, callback: SectionedCallback<Album>) {
        fetchValue("getSectionedAlbums", SectionedList.empty(), callback::onLoadMedia) { api ->
            val result by api.itemsApi.getItems(albumsRequest(sortMethod, sortOrder, 0, JellyfinSdkSession.getCurrentUserId(), null, currentLibrary))
            AlphabetIndex.sort(result.items, sortOrder == AppSortOrder.DESCENDING, sectionedAlbumKeys(sortMethod)).map(SdkMediaMapper::toAlbum)
        }
    }

    internal fun sectionedArtistKeys(): List<AlphabetIndex.Key<BaseItemDto>> = listOf(
        AlphabetIndex.Key(personName = true) { it.sortName ?: it.name }
    )

    internal fun sectionedAlbumKeys(sortMethod: SortMethod): List<AlphabetIndex.Key<BaseItemDto>> {
        val title = AlphabetIndex.Key<BaseItemDto>(personName = false) { it.sortName ?: it.name }
        if (sortMethod != SortMethod.ARTIST) return listOf(title)

        val artist = AlphabetIndex.Key<BaseItemDto>(personName = true) {
            (it.albumArtists?.firstOrNull() ?: it.artistItems?.firstOrNull())?.name
        }
        return listOf(artist, title)
    }

    @JvmStatic
    fun getSongs(sortMethod: SortMethod?, sortOrder: AppSortOrder?, startIndex: Int, onlyFavorites: Boolean, callback: MediaCallback<Song>) {
        fetchItems(
            request = songsRequest(sortMethod, sortOrder, startIndex, onlyFavorites, JellyfinSdkSession.getCurrentUserId(), defaultPageSize(), currentLibrary),
            onErrorTag = "getSongs",
            mapper = SdkSongMapper::fromItem,
            callback = callback
        )
    }

    @JvmStatic
    fun getSongsByParent(parentId: String, callback: MediaCallback<Song>) {
        fetchItems(
            request = GetItemsRequest(
                userId = currentUserUuidOrNull(),
                parentId = toUuidOrNull(parentId),
                includeItemTypes = listOf(BaseItemKind.AUDIO),
                fields = listOf(ItemFields.MEDIA_SOURCES),
                recursive = true
            ),
            onErrorTag = "getSongsByParent",
            mapper = SdkSongMapper::fromItem,
            callback = callback
        )
    }

    /**
     * Fetches the songs of several albums or playlists and calls back once with all of them, in the
     * order of the ids, so a multi-selection acts as one list.
     */
    @JvmStatic
    fun getSongsByParents(parentIds: List<String>, callback: MediaCallback<Song>) {
        if (parentIds.isEmpty()) {
            callback.onLoadMedia(emptyList())
            return
        }

        // callbacks run on the main thread, so the results need no locking
        val results = arrayOfNulls<List<Song>>(parentIds.size)
        var remaining = parentIds.size

        parentIds.forEachIndexed { index, parentId ->
            getSongsByParent(parentId) { media ->
                results[index] = media
                if (--remaining == 0) callback.onLoadMedia(results.flatMap { it.orEmpty() })
            }
        }
    }

    @JvmStatic
    @JvmOverloads
    fun getSongsByArtistIds(artistIds: List<String>, callback: MediaCallback<Song>, albumArtist: Boolean = false) {
        fetchItems(
            request = songsByArtistIdsRequest(artistIds, JellyfinSdkSession.getCurrentUserId(), albumArtist),
            onErrorTag = "getSongsByArtistIds",
            mapper = SdkSongMapper::fromItem,
            callback = callback
        )
    }

    @JvmStatic
    fun getSongsByGenreId(genreId: String, callback: MediaCallback<Song>) {
        fetchItems(
            request = GetItemsRequest(
                userId = currentUserUuidOrNull(),
                parentId = toUuidOrNull(currentLibrary?.id),
                genreIds = listOfNotNull(toUuidOrNull(genreId)),
                includeItemTypes = listOf(BaseItemKind.AUDIO),
                fields = listOf(ItemFields.MEDIA_SOURCES),
                recursive = true,
                limit = defaultPageSize()
            ),
            onErrorTag = "getSongsByGenreId",
            mapper = SdkSongMapper::fromItem,
            callback = callback
        )
    }

    @JvmStatic
    @JvmOverloads
    fun getAlbumsByArtistId(artistId: String, callback: MediaCallback<Album>, albumArtist: Boolean = false) {
        fetchItems(
            request = albumsByArtistIdRequest(artistId, JellyfinSdkSession.getCurrentUserId(), albumArtist),
            onErrorTag = "getAlbumsByArtistId",
            mapper = SdkMediaMapper::toAlbum,
            callback = callback
        )
    }

    @JvmStatic
    @JvmOverloads
    fun getSongsByArtistId(artistId: String, callback: MediaCallback<Song>, albumArtist: Boolean = false) {
        fetchItems(
            request = songsByArtistIdsRequest(listOf(artistId), JellyfinSdkSession.getCurrentUserId(), albumArtist),
            onErrorTag = "getSongsByArtistId",
            mapper = SdkSongMapper::fromItem,
            callback = callback
        )
    }

    @JvmStatic
    fun getAlbumSongs(albumId: String, callback: MediaCallback<Song>) {
        fetchItems(
            request = albumSongsRequest(albumId, JellyfinSdkSession.getCurrentUserId()),
            onErrorTag = "getAlbumSongs",
            mapper = SdkSongMapper::fromItem,
            callback = callback
        )
    }

    @JvmStatic
    fun getSongsBySort(sortMethod: SortMethod, sortOrder: AppSortOrder, limit: Int, onlyFavorites: Boolean, callback: MediaCallback<Song>) {
        fetchItems(
            request = GetItemsRequest(
                userId = currentUserUuidOrNull(),
                parentId = toUuidOrNull(currentLibrary?.id),
                includeItemTypes = listOf(BaseItemKind.AUDIO),
                fields = listOf(ItemFields.MEDIA_SOURCES),
                sortBy = listOfNotNull(mapSortBy(sortMethod.api)),
                sortOrder = mapSortOrder(sortOrder),
                limit = limit,
                isFavorite = if (onlyFavorites) true else null,
                recursive = true
            ),
            onErrorTag = "getSongsBySort",
            mapper = SdkSongMapper::fromItem,
            callback = callback
        )
    }

    // --- Internal request builders (injectable for unit testing) ---

    internal fun albumsRequest(
        sortMethod: SortMethod?,
        sortOrder: AppSortOrder?,
        startIndex: Int,
        userId: String?,
        pageSize: Int?,
        library: Library?
    ): GetItemsRequest = GetItemsRequest(
        userId = toUuidOrNull(userId),
        parentId = toUuidOrNull(library?.id),
        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
        fields = listOf(ItemFields.SORT_NAME),
        recursive = true,
        limit = pageSize,
        startIndex = startIndex,
        sortBy = listOfNotNull(sortMethod?.let { mapSortBy(it.api) }),
        sortOrder = mapSortOrder(sortOrder)
    )

    internal fun artistsRequest(
        sortMethod: SortMethod?,
        sortOrder: AppSortOrder?,
        startIndex: Int,
        userId: String?,
        pageSize: Int?,
        library: Library?
    ): GetArtistsRequest = GetArtistsRequest(
        userId = toUuidOrNull(userId),
        parentId = toUuidOrNull(library?.id),
        startIndex = startIndex,
        limit = pageSize,
        sortBy = listOfNotNull(sortMethod?.let { mapSortBy(it.api) }),
        sortOrder = mapSortOrder(sortOrder),
        fields = listOf(ItemFields.GENRES, ItemFields.SORT_NAME)
    )

    internal fun songsByArtistIdsRequest(
        artistIds: List<String>,
        userId: String?,
        albumArtist: Boolean
    ): GetItemsRequest {
        val ids = artistIds.mapNotNull(::toUuidOrNull)
        return GetItemsRequest(
            userId = toUuidOrNull(userId),
            artistIds = if (albumArtist) null else ids,
            albumArtistIds = if (albumArtist) ids else null,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            fields = listOf(ItemFields.MEDIA_SOURCES),
            recursive = true
        )
    }

    internal fun albumsByArtistIdRequest(
        artistId: String,
        userId: String?,
        albumArtist: Boolean
    ): GetItemsRequest {
        val ids = listOfNotNull(toUuidOrNull(artistId))
        return GetItemsRequest(
            userId = toUuidOrNull(userId),
            artistIds = if (albumArtist) null else ids,
            albumArtistIds = if (albumArtist) ids else null,
            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
            recursive = true
        )
    }

    internal fun albumArtistsRequest(
        sortMethod: SortMethod?,
        sortOrder: AppSortOrder?,
        startIndex: Int,
        userId: String?,
        pageSize: Int?,
        library: Library?
    ): GetAlbumArtistsRequest = GetAlbumArtistsRequest(
        userId = toUuidOrNull(userId),
        parentId = toUuidOrNull(library?.id),
        startIndex = startIndex,
        limit = pageSize,
        sortBy = listOfNotNull(sortMethod?.let { mapSortBy(it.api) }),
        sortOrder = mapSortOrder(sortOrder),
        fields = listOf(ItemFields.GENRES, ItemFields.SORT_NAME)
    )

    internal fun songsRequest(
        sortMethod: SortMethod?,
        sortOrder: AppSortOrder?,
        startIndex: Int,
        onlyFavorites: Boolean,
        userId: String?,
        pageSize: Int,
        library: Library?
    ): GetItemsRequest = GetItemsRequest(
        userId = toUuidOrNull(userId),
        parentId = toUuidOrNull(library?.id),
        includeItemTypes = listOf(BaseItemKind.AUDIO),
        fields = listOf(ItemFields.MEDIA_SOURCES),
        recursive = true,
        limit = pageSize,
        startIndex = startIndex,
        sortBy = listOfNotNull(sortMethod?.let { mapSortBy(it.api) }),
        sortOrder = mapSortOrder(sortOrder),
        isFavorite = if (onlyFavorites) true else null
    )

    internal fun genresRequest(
        startIndex: Int,
        userId: String?,
        pageSize: Int,
        library: Library?
    ): GetGenresRequest = GetGenresRequest(
        userId = toUuidOrNull(userId),
        parentId = toUuidOrNull(library?.id),
        limit = pageSize,
        startIndex = startIndex,
        sortBy = listOf(ItemSortBy.SORT_NAME)
    )

    internal fun albumSongsRequest(albumId: String, userId: String?): GetItemsRequest = GetItemsRequest(
        userId = toUuidOrNull(userId),
        albumIds = listOfNotNull(toUuidOrNull(albumId)),
        includeItemTypes = listOf(BaseItemKind.AUDIO),
        fields = listOf(ItemFields.MEDIA_SOURCES),
        sortBy = listOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER),
        recursive = true
    )

    internal fun playlistsRequest(
        startIndex: Int,
        userId: String?,
        pageSize: Int
    ): GetItemsRequest = GetItemsRequest(
        userId = toUuidOrNull(userId),
        parentId = null,
        includeItemTypes = listOf(BaseItemKind.PLAYLIST),
        recursive = true,
        limit = pageSize,
        startIndex = startIndex
    )

    internal fun mapSortOrder(order: AppSortOrder?): List<SortOrder> = when (order) {
        AppSortOrder.ASCENDING -> listOf(SortOrder.ASCENDING)
        AppSortOrder.DESCENDING -> listOf(SortOrder.DESCENDING)
        null -> emptyList()
    }

    internal fun mapSortBy(sort: String?): ItemSortBy? = when (sort) {
        "SortName" -> ItemSortBy.SORT_NAME
        "Album" -> ItemSortBy.ALBUM
        "AlbumArtist" -> ItemSortBy.ALBUM_ARTIST
        "ProductionYear" -> ItemSortBy.PRODUCTION_YEAR
        "DateCreated" -> ItemSortBy.DATE_CREATED
        "Random" -> ItemSortBy.RANDOM
        "PlayCount" -> ItemSortBy.PLAY_COUNT
        else -> null
    }

    private fun defaultPageSize(): Int = PreferenceUtil.getInstance(App.getInstance()).getPageSize()

    private fun currentUserUuidOrNull(): UUID? = toUuidOrNull(JellyfinSdkSession.getCurrentUserId())

    internal fun uuidToId(value: UUID?): String = value?.toString()?.replace("-", "") ?: ""

    internal fun toUuidOrNull(raw: String?): UUID? {
        if (raw.isNullOrBlank()) return null

        val normalized = if (raw.contains("-")) raw else raw.toUuidStringOrNull()
        if (normalized == null) return null

        return try {
            UUID.fromString(normalized)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun String.toUuidStringOrNull(): String? {
        val value = replace("-", "")
        if (value.length != 32) return null

        return buildString(36) {
            append(value, 0, 8)
            append('-')
            append(value, 8, 12)
            append('-')
            append(value, 12, 16)
            append('-')
            append(value, 16, 20)
            append('-')
            append(value, 20, 32)
        }
    }

    private fun <T> fetch(
        onErrorTag: String,
        callback: MediaCallback<T>,
        load: suspend (ApiClient) -> List<T>
    ) {
        fetchValue(onErrorTag, emptyList(), callback::onLoadMedia, load)
    }

    /** Runs [load] off the main thread and delivers its result, or [fallback] on failure, on the main thread. */
    private fun <R> fetchValue(
        onErrorTag: String,
        fallback: R,
        deliver: (R) -> Unit,
        load: suspend (ApiClient) -> R
    ) {
        val api = JellyfinSdkSession.createApiOrNull()
        if (api == null) {
            Log.w(TAG, "$onErrorTag: missing SDK session state")
            deliver(fallback)
            return
        }

        Thread {
            val loaded = try {
                runBlocking { load(api) }
            } catch (err: ApiClientException) {
                Log.w(TAG, "$onErrorTag: API error: ${err.message}", err)
                fallback
            } catch (err: Exception) {
                Log.w(TAG, "$onErrorTag: unexpected error: ${err.message}", err)
                fallback
            }

            Handler(Looper.getMainLooper()).post {
                deliver(loaded)
            }
        }.start()
    }

    private fun <T> fetchItems(
        request: GetItemsRequest,
        onErrorTag: String,
        mapper: (BaseItemDto) -> T,
        callback: MediaCallback<T>
    ) {
        fetch(onErrorTag, callback) { api ->
            val result by api.itemsApi.getItems(request)
            result.items.map(mapper)
        }
    }
}
