package com.dkanada.gramophone.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dkanada.gramophone.App
import com.dkanada.gramophone.interfaces.MediaCallback
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
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetAlbumArtistsRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

object QueryUtil {
    private const val TAG = "QueryUtil"

    data class Library(
        val id: String,
        val name: String,
        val collectionType: String?
    )

    @JvmField
    var currentLibrary: Library? = null

    @JvmStatic
    fun getLibraries(callback: MediaCallback<Library>) {
        fetchItems(
            request = GetItemsRequest(
                userId = currentUserUuidOrNull(),
                includeItemTypes = listOf(BaseItemKind.COLLECTION_FOLDER),
                recursive = false
            ),
            onErrorTag = "getLibraries",
            mapper = { item ->
                Library(
                    id = uuidToId(item.id),
                    name = item.name ?: "",
                    collectionType = item.collectionType?.toString()
                )
            },
            callback = callback
        )
    }

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

    @JvmStatic
    fun getGenres(startIndex: Int, callback: MediaCallback<Genre>) {
        fetchItems(
            request = genresRequest(startIndex, JellyfinSdkSession.getCurrentUserId(), defaultPageSize(), currentLibrary),
            onErrorTag = "getGenres",
            mapper = SdkMediaMapper::toGenre,
            callback = callback
        )
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

    @JvmStatic
    fun getArtists(sortMethod: SortMethod?, sortOrder: AppSortOrder?, startIndex: Int, callback: MediaCallback<Artist>) {
        val api = JellyfinSdkSession.createApiOrNull()
        if (api == null) {
            Log.w(TAG, "getArtists: missing SDK session state")
            callback.onLoadMedia(emptyList())
            return
        }

        val request = artistsRequest(sortMethod, sortOrder, startIndex, JellyfinSdkSession.getCurrentUserId(), defaultPageSize(), currentLibrary)

        Thread {
            val mapped = try {
                runBlocking {
                    val result by api.artistsApi.getAlbumArtists(request)
                    result.items.map(SdkMediaMapper::toArtist)
                }
            } catch (err: ApiClientException) {
                Log.w(TAG, "getArtists: API error: ${err.message}", err)
                emptyList()
            } catch (err: Exception) {
                Log.w(TAG, "getArtists: unexpected error: ${err.message}", err)
                emptyList()
            }

            Handler(Looper.getMainLooper()).post {
                callback.onLoadMedia(mapped)
            }
        }.start()
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

    @JvmStatic
    fun getSongsByArtistIds(artistIds: List<String>, callback: MediaCallback<Song>) {
        fetchItems(
            request = GetItemsRequest(
                userId = currentUserUuidOrNull(),
                artistIds = artistIds.mapNotNull(::toUuidOrNull),
                includeItemTypes = listOf(BaseItemKind.AUDIO),
                fields = listOf(ItemFields.MEDIA_SOURCES),
                recursive = true
            ),
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
    fun getAlbumsByArtistId(artistId: String, callback: MediaCallback<Album>) {
        fetchItems(
            request = GetItemsRequest(
                userId = currentUserUuidOrNull(),
                artistIds = listOfNotNull(toUuidOrNull(artistId)),
                includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                recursive = true
            ),
            onErrorTag = "getAlbumsByArtistId",
            mapper = SdkMediaMapper::toAlbum,
            callback = callback
        )
    }

    @JvmStatic
    fun getSongsByArtistId(artistId: String, callback: MediaCallback<Song>) {
        fetchItems(
            request = GetItemsRequest(
                userId = currentUserUuidOrNull(),
                artistIds = listOfNotNull(toUuidOrNull(artistId)),
                includeItemTypes = listOf(BaseItemKind.AUDIO),
                fields = listOf(ItemFields.MEDIA_SOURCES),
                recursive = true
            ),
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
        pageSize: Int,
        library: Library?
    ): GetItemsRequest = GetItemsRequest(
        userId = toUuidOrNull(userId),
        parentId = toUuidOrNull(library?.id),
        includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
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
        pageSize: Int,
        library: Library?
    ): GetAlbumArtistsRequest = GetAlbumArtistsRequest(
        userId = toUuidOrNull(userId),
        parentId = toUuidOrNull(library?.id),
        startIndex = startIndex,
        limit = pageSize,
        sortBy = listOfNotNull(sortMethod?.let { mapSortBy(it.api) }),
        sortOrder = mapSortOrder(sortOrder),
        fields = listOf(ItemFields.GENRES)
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
    ): GetItemsRequest = GetItemsRequest(
        userId = toUuidOrNull(userId),
        parentId = toUuidOrNull(library?.id),
        includeItemTypes = listOf(BaseItemKind.MUSIC_GENRE),
        recursive = true,
        limit = pageSize,
        startIndex = startIndex
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

    private fun <T> fetchItems(
        request: GetItemsRequest,
        onErrorTag: String,
        mapper: (BaseItemDto) -> T,
        callback: MediaCallback<T>
    ) {
        val api = JellyfinSdkSession.createApiOrNull()
        if (api == null) {
            Log.w(TAG, "$onErrorTag: missing SDK session state")
            callback.onLoadMedia(emptyList())
            return
        }

        Thread {
            val mapped = try {
                runBlocking {
                    val result by api.itemsApi.getItems(request)
                    result.items.map(mapper)
                }
            } catch (err: ApiClientException) {
                Log.w(TAG, "$onErrorTag: API error: ${err.message}", err)
                emptyList()
            } catch (err: Exception) {
                Log.w(TAG, "$onErrorTag: unexpected error: ${err.message}", err)
                emptyList()
            }

            Handler(Looper.getMainLooper()).post {
                callback.onLoadMedia(mapped)
            }
        }.start()
    }
}
