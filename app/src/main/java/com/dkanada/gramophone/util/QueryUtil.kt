package com.dkanada.gramophone.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dkanada.gramophone.App
import com.dkanada.gramophone.interfaces.MediaCallback
import com.dkanada.gramophone.mapper.LegacyMediaMapper
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
import org.jellyfin.apiclient.model.querying.ArtistsQuery
import org.jellyfin.apiclient.model.querying.ItemFilter
import org.jellyfin.apiclient.model.querying.ItemQuery
import org.jellyfin.apiclient.model.querying.ItemsByNameQuery
import org.jellyfin.apiclient.model.querying.ItemsResult
import org.jellyfin.apiclient.interaction.Response
import org.jellyfin.sdk.api.client.extensions.artistsApi
import org.jellyfin.sdk.api.client.exception.ApiClientException
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
        getPlaylists(ItemQuery(), callback)
    }

    @JvmStatic
    fun getPlaylists(query: ItemQuery, callback: MediaCallback<Playlist>) {
        val request = itemQueryToRequest(query)
        fetchItems(
            request = request.copy(
                includeItemTypes = listOf(BaseItemKind.PLAYLIST),
                recursive = true
            ),
            onErrorTag = "getPlaylists",
            mapper = SdkMediaMapper::toPlaylist,
            callback = callback
        )
    }

    @JvmStatic
    fun getGenres(callback: MediaCallback<Genre>) {
        getGenres(ItemsByNameQuery(), callback)
    }

    @JvmStatic
    fun getGenres(query: ItemsByNameQuery, callback: MediaCallback<Genre>) {
        val request = itemsByNameToRequest(query)
        fetchItems(
            request = request.copy(includeItemTypes = listOf(BaseItemKind.MUSIC_GENRE)),
            onErrorTag = "getGenres",
            mapper = SdkMediaMapper::toGenre,
            callback = callback
        )
    }

    @JvmStatic
    fun getItems(query: ItemQuery, callback: MediaCallback<Any>) {
        val request = itemQueryToRequest(query).copy(
            includeItemTypes = listOf(BaseItemKind.MUSIC_ARTIST, BaseItemKind.MUSIC_ALBUM, BaseItemKind.AUDIO),
            fields = listOf(ItemFields.MEDIA_SOURCES),
            limit = 40,
            recursive = true
        )

        fetchItems(
            request = request,
            onErrorTag = "getItems",
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
    fun searchItems(searchTerm: String, callback: MediaCallback<Any>) {
        val query = ItemQuery()
        query.searchTerm = searchTerm
        getItems(query, callback)
    }

    @JvmStatic
    fun getAlbums(query: ItemQuery, callback: MediaCallback<Album>) {
        query.includeItemTypes = arrayOf("MusicAlbum")
        applyProperties(query)
        Log.d(
            TAG,
            "getAlbums (legacy) request: parentId=${query.parentId}, userId=${query.userId}, includeItemTypes=${query.includeItemTypes?.toList()}, start=${query.startIndex}, limit=${query.limit}, sortBy=${query.sortBy?.toList()}, sortOrder=${query.sortOrder}, recursive=${query.recursive}"
        )

        App.getApiClient().GetItemsAsync(query, object : Response<ItemsResult>() {
            override fun onResponse(result: ItemsResult) {
                val albums = result.items.map(LegacyMediaMapper::toAlbum)
                Log.d(TAG, "getAlbums (legacy): response count=${albums.size}, total=${result.totalRecordCount}")
                callback.onLoadMedia(albums)
            }

            override fun onError(exception: Exception) {
                Log.w(TAG, "getAlbums (legacy): request failed", exception)
                callback.onLoadMedia(emptyList())
            }
        })
    }

    @JvmStatic
    fun getArtists(query: ArtistsQuery, callback: MediaCallback<Artist>) {
        val api = JellyfinSdkSession.createApiOrNull()
        val request = artistsQueryToRequest(query)
        if (api == null) {
            Log.w(TAG, "getArtists: missing SDK session state")
            callback.onLoadMedia(emptyList())
            return
        }

        Thread {
            val mapped = try {
                runBlocking {
                    val result by api.artistsApi.getAlbumArtists(request)
                    Log.d(
                        TAG,
                        "getArtists: response count=${result.items.size}, total=${result.totalRecordCount}, request=${albumArtistsRequestSummary(request)}"
                    )
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
    fun getSongs(query: ItemQuery, callback: MediaCallback<Song>) {
        fetchItems(
            request = itemQueryToRequest(query).copy(
                includeItemTypes = listOf(BaseItemKind.AUDIO),
                fields = listOf(ItemFields.MEDIA_SOURCES)
            ),
            onErrorTag = "getSongs",
            mapper = SdkSongMapper::fromItem,
            callback = callback
        )
    }

    @JvmStatic
    fun getSongsByParent(parentId: String, callback: MediaCallback<Song>) {
        val query = ItemQuery()
        query.parentId = parentId
        getSongs(query, callback)
    }

    @JvmStatic
    fun getSongsByArtistIds(artistIds: List<String>, callback: MediaCallback<Song>) {
        val query = ItemQuery()
        query.artistIds = artistIds.toTypedArray()
        getSongs(query, callback)
    }

    @JvmStatic
    fun getSongsByGenreId(genreId: String, callback: MediaCallback<Song>) {
        val query = ItemQuery()
        query.genreIds = arrayOf(genreId)
        getSongs(query, callback)
    }

    @JvmStatic
    fun getAlbumsByArtistId(artistId: String, callback: MediaCallback<Album>) {
        val query = ItemQuery()
        query.artistIds = arrayOf(artistId)
        getAlbums(query, callback)
    }

    @JvmStatic
    fun getSongsByArtistId(artistId: String, callback: MediaCallback<Song>) {
        val query = ItemQuery()
        query.artistIds = arrayOf(artistId)
        getSongs(query, callback)
    }

    @JvmStatic
    fun getSongsBySort(
        sortMethod: SortMethod,
        sortOrder: AppSortOrder,
        limit: Int,
        onlyFavorites: Boolean,
        callback: MediaCallback<Song>
    ) {
        val query = ItemQuery()
        query.sortBy = arrayOf(sortMethod.api)
        query.sortOrder = if (sortOrder == AppSortOrder.ASCENDING) {
            org.jellyfin.apiclient.model.entities.SortOrder.Ascending
        } else {
            org.jellyfin.apiclient.model.entities.SortOrder.Descending
        }
        query.limit = limit
        if (onlyFavorites) {
            query.filters = arrayOf(ItemFilter.IsFavorite)
        }
        getSongs(query, callback)
    }

    @JvmStatic
    fun applyAlbumIdFilter(query: ItemQuery, albumId: String?) {
        query.parentId = albumId
    }

    @JvmStatic
    fun applyProperties(query: ItemQuery) {
        query.userId = JellyfinSdkSession.getCurrentUserId()
        query.recursive = true

        val artistIds = query.artistIds ?: emptyArray()
        if (query.parentId == null && artistIds.isEmpty()) {
            query.limit = PreferenceUtil.getInstance(App.getInstance()).getPageSize()
        }

        if (currentLibrary == null || query.parentId != null) return
        if (artistIds.isEmpty()) query.parentId = currentLibrary?.id
    }

    @JvmStatic
    fun applyProperties(query: ItemsByNameQuery) {
        query.userId = JellyfinSdkSession.getCurrentUserId()
        query.recursive = true

        if (query.parentId == null) {
            query.limit = PreferenceUtil.getInstance(App.getInstance()).getPageSize()
        }

        if (currentLibrary == null || query.parentId != null) return
        query.parentId = currentLibrary?.id
    }

    private fun itemQueryToRequest(query: ItemQuery): GetItemsRequest {
        applyProperties(query)
        val parentUuid = toUuidOrNull(query.parentId)
        if (!query.parentId.isNullOrBlank() && parentUuid == null) {
            Log.w(TAG, "itemQueryToRequest: invalid parentId='${query.parentId}'")
        }

        return GetItemsRequest(
            userId = currentUserUuidOrNull(),
            parentId = parentUuid,
            searchTerm = query.searchTerm,
            artistIds = (query.artistIds ?: emptyArray()).mapNotNull(::toUuidOrNull),
            genreIds = (query.genreIds ?: emptyArray()).mapNotNull(::toUuidOrNull),
            startIndex = query.startIndex,
            limit = query.limit,
            recursive = query.recursive,
            sortBy = (query.sortBy ?: emptyArray()).mapNotNull(::mapSortBy),
            sortOrder = mapSortOrder(query.sortOrder),
            isFavorite = if (query.filters?.contains(ItemFilter.IsFavorite) == true) true else null
        )
    }

    private fun artistsQueryToRequest(query: ArtistsQuery): GetAlbumArtistsRequest {
        applyProperties(query)
        val parentUuid = toUuidOrNull(query.parentId)
        if (!query.parentId.isNullOrBlank() && parentUuid == null) {
            Log.w(TAG, "artistsQueryToRequest: invalid parentId='${query.parentId}'")
        }

        return GetAlbumArtistsRequest(
            userId = currentUserUuidOrNull(),
            parentId = parentUuid,
            startIndex = query.startIndex,
            limit = query.limit,
            sortBy = (query.sortBy ?: emptyArray()).mapNotNull(::mapSortBy),
            sortOrder = mapSortOrder(query.sortOrder),
            fields = listOf(ItemFields.GENRES)
        )
    }

    private fun itemsByNameToRequest(query: ItemsByNameQuery): GetItemsRequest {
        applyProperties(query)

        return GetItemsRequest(
            userId = currentUserUuidOrNull(),
            parentId = toUuidOrNull(query.parentId),
            startIndex = query.startIndex,
            limit = query.limit,
            recursive = query.recursive
        )
    }

    private fun mapSortBy(sort: String?): ItemSortBy? {
        return when (sort) {
            "SortName" -> ItemSortBy.SORT_NAME
            "Album" -> ItemSortBy.ALBUM
            "AlbumArtist" -> ItemSortBy.ALBUM_ARTIST
            "ProductionYear" -> ItemSortBy.PRODUCTION_YEAR
            "DateCreated" -> ItemSortBy.DATE_CREATED
            "Random" -> ItemSortBy.RANDOM
            "PlayCount" -> ItemSortBy.PLAY_COUNT
            else -> null
        }
    }

    private fun mapSortOrder(order: org.jellyfin.apiclient.model.entities.SortOrder?): List<SortOrder> {
        return when (order) {
            org.jellyfin.apiclient.model.entities.SortOrder.Ascending -> listOf(SortOrder.ASCENDING)
            org.jellyfin.apiclient.model.entities.SortOrder.Descending -> listOf(SortOrder.DESCENDING)
            else -> emptyList()
        }
    }

    private fun currentUserUuidOrNull(): UUID? {
        return toUuidOrNull(JellyfinSdkSession.getCurrentUserId())
    }

    private fun uuidToId(value: UUID?): String {
        return value?.toString()?.replace("-", "") ?: ""
    }

    private fun toUuidOrNull(raw: String?): UUID? {
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
                    Log.d(
                        TAG,
                        "$onErrorTag: response count=${result.items.size}, total=${result.totalRecordCount}, request=${requestSummary(request)}"
                    )
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

    private fun requestSummary(request: GetItemsRequest): String {
        return "parentId=${request.parentId}, userId=${request.userId}, includeItemTypes=${request.includeItemTypes}, start=${request.startIndex}, limit=${request.limit}, sortBy=${request.sortBy}, sortOrder=${request.sortOrder}, recursive=${request.recursive}"
    }

    private fun albumArtistsRequestSummary(request: GetAlbumArtistsRequest): String {
        return "parentId=${request.parentId}, userId=${request.userId}, start=${request.startIndex}, limit=${request.limit}, sortBy=${request.sortBy}, sortOrder=${request.sortOrder}, fields=${request.fields}"
    }
}
