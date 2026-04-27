package com.dkanada.gramophone.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dkanada.gramophone.interfaces.MediaCallback
import com.dkanada.gramophone.model.Song
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

object JellyfinSdkBridge {
    private const val TAG = "JellyfinSdkBridge"

    @JvmStatic
    fun getAlbumSongs(albumId: String, callback: MediaCallback<Song>) {
        val api = JellyfinSdkSession.createApiOrNull()
        val userId = JellyfinSdkSession.getCurrentUserId()
        if (api == null || userId.isNullOrBlank()) {
            Log.w(TAG, "getAlbumSongs: missing SDK session state")
            callback.onLoadMedia(emptyList())
            return
        }

        Thread {
            val result = try {
                runBlocking {
                    val request = GetItemsRequest(
                        userId = toUuidOrNull(userId),
                        albumIds = listOfNotNull(toUuidOrNull(albumId)),
                        includeItemTypes = listOf(BaseItemKind.AUDIO),
                        fields = listOf(ItemFields.MEDIA_SOURCES),
                        sortBy = listOf(ItemSortBy.PARENT_INDEX_NUMBER, ItemSortBy.INDEX_NUMBER),
                        recursive = true
                    )

                    val itemsResult by api.itemsApi.getItems(request)
                    itemsResult.items.map { it.toSong() }
                }
            } catch (err: ApiClientException) {
                Log.w(TAG, "getAlbumSongs: API error: ${err.message}", err)
                emptyList()
            } catch (err: Exception) {
                Log.w(TAG, "getAlbumSongs: unexpected error: ${err.message}", err)
                emptyList()
            }

            Handler(Looper.getMainLooper()).post {
                callback.onLoadMedia(result)
            }
        }.start()
    }

    private fun BaseItemDto.toSong(): Song {
        val song = Song()
        song.id = uuidToId(id)
        song.title = name ?: ""
        song.trackNumber = indexNumber ?: 0
        song.discNumber = parentIndexNumber ?: 0
        song.year = productionYear ?: 0
        song.duration = (runTimeTicks ?: 0L) / 10000

        song.albumId = uuidToId(albumId)
        song.albumName = album

        val artistItem = artistItems?.firstOrNull() ?: albumArtists?.firstOrNull()
        song.artistId = uuidToId(artistItem?.id)
        song.artistName = artistItem?.name

        song.primary = if (albumPrimaryImageTag != null && song.albumId != null) song.albumId else null
        song.blurHash = imageBlurHashes
            ?.get(ImageType.PRIMARY)
            ?.values
            ?.firstOrNull()

        song.favorite = userData?.isFavorite == true

        val source = mediaSources?.firstOrNull()
        if (source != null) {
            song.path = source.path
            song.size = source.size ?: 0L
            song.container = source.container
            song.bitRate = source.bitrate ?: 0
            song.supportsTranscoding = source.supportsTranscoding

            val stream = source.mediaStreams?.firstOrNull()
            if (stream != null) {
                song.codec = stream.codec
                song.sampleRate = stream.sampleRate ?: 0
                song.bitDepth = stream.bitDepth ?: 0
                song.channels = stream.channels ?: 0
            }
        }

        return song
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

    private fun uuidToId(value: UUID?): String {
        return value?.toString()?.replace("-", "") ?: ""
    }
}
