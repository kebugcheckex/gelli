package com.dkanada.gramophone.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dkanada.gramophone.interfaces.MediaCallback
import com.dkanada.gramophone.mapper.SdkSongMapper
import com.dkanada.gramophone.model.Playlist
import com.dkanada.gramophone.model.PlaylistSong
import com.dkanada.gramophone.model.Song
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.UpdatePlaylistDto
import org.jellyfin.sdk.model.api.request.GetPlaylistItemsRequest
import java.util.UUID

object PlaylistUtil {
    private const val TAG = "PlaylistUtil"

    @JvmStatic
    fun getPlaylist(playlistId: String, callback: MediaCallback<PlaylistSong>) {
        val playlistUuid = QueryUtil.toUuidOrNull(playlistId)
        if (playlistUuid == null) {
            Log.w(TAG, "getPlaylist: invalid playlistId: $playlistId")
            callback.onLoadMedia(emptyList())
            return
        }
        val api = JellyfinSdkSession.createApiOrNull()
        if (api == null) {
            Log.w(TAG, "getPlaylist: missing SDK session state")
            callback.onLoadMedia(emptyList())
            return
        }
        val request = getPlaylistItemsRequest(playlistUuid, JellyfinSdkSession.getCurrentUserId())

        Thread {
            val result = try {
                runBlocking {
                    val response by api.playlistsApi.getPlaylistItems(request)
                    response.items.map { SdkSongMapper.fromPlaylistItem(it, playlistId) }
                }
            } catch (err: ApiClientException) {
                Log.w(TAG, "getPlaylist: API error: ${err.message}", err)
                emptyList()
            } catch (err: Exception) {
                Log.w(TAG, "getPlaylist: unexpected error: ${err.message}", err)
                emptyList()
            }

            Handler(Looper.getMainLooper()).post {
                callback.onLoadMedia(result)
            }
        }.start()
    }

    @JvmStatic
    fun createPlaylist(name: String, songs: List<Song>) {
        val api = JellyfinSdkSession.createApiOrNull() ?: return
        val userId = QueryUtil.toUuidOrNull(JellyfinSdkSession.getCurrentUserId())
        val ids = songs.mapNotNull { QueryUtil.toUuidOrNull(it.id) }

        Thread {
            try {
                runBlocking {
                    api.playlistsApi.createPlaylist(
                        CreatePlaylistDto(
                            name = name,
                            ids = ids,
                            userId = userId,
                            users = emptyList(),
                            isPublic = false
                        )
                    )
                }
            } catch (err: Exception) {
                Log.w(TAG, "createPlaylist: error: ${err.message}", err)
            }
        }.start()
    }

    @JvmStatic
    fun deletePlaylist(playlists: List<Playlist>) {
        val api = JellyfinSdkSession.createApiOrNull() ?: return

        Thread {
            try {
                runBlocking {
                    for (playlist in playlists) {
                        val id = QueryUtil.toUuidOrNull(playlist.id) ?: continue
                        api.libraryApi.deleteItem(id)
                    }
                }
            } catch (err: Exception) {
                Log.w(TAG, "deletePlaylist: error: ${err.message}", err)
            }
        }.start()
    }

    @JvmStatic
    fun addItems(songs: List<Song>, playlist: String) {
        val api = JellyfinSdkSession.createApiOrNull() ?: return
        val playlistId = QueryUtil.toUuidOrNull(playlist) ?: return
        val userId = QueryUtil.toUuidOrNull(JellyfinSdkSession.getCurrentUserId())
        val ids = songs.mapNotNull { QueryUtil.toUuidOrNull(it.id) }

        Thread {
            try {
                runBlocking {
                    api.playlistsApi.addItemToPlaylist(playlistId, ids, userId)
                }
            } catch (err: Exception) {
                Log.w(TAG, "addItems: error: ${err.message}", err)
            }
        }.start()
    }

    @JvmStatic
    fun deleteItems(songs: List<PlaylistSong>, playlist: String) {
        val api = JellyfinSdkSession.createApiOrNull() ?: return
        val playlistId = QueryUtil.toUuidOrNull(playlist)?.toString() ?: return
        val entryIds = songs.mapNotNull { it.indexId }

        Thread {
            try {
                runBlocking {
                    api.playlistsApi.removeItemFromPlaylist(playlistId, entryIds)
                }
            } catch (err: Exception) {
                Log.w(TAG, "deleteItems: error: ${err.message}", err)
            }
        }.start()
    }

    @JvmStatic
    fun moveItem(playlist: String, song: PlaylistSong, to: Int) {
        val api = JellyfinSdkSession.createApiOrNull() ?: return
        val playlistId = QueryUtil.toUuidOrNull(playlist)?.toString() ?: return
        val indexId = song.indexId ?: return

        Thread {
            try {
                runBlocking {
                    api.playlistsApi.moveItem(playlistId, indexId, to)
                }
            } catch (err: Exception) {
                Log.w(TAG, "moveItem: error: ${err.message}", err)
            }
        }.start()
    }

    @JvmStatic
    fun renamePlaylist(playlist: String, name: String) {
        val api = JellyfinSdkSession.createApiOrNull() ?: return
        val playlistId = QueryUtil.toUuidOrNull(playlist) ?: return

        Thread {
            try {
                runBlocking {
                    api.playlistsApi.updatePlaylist(playlistId, UpdatePlaylistDto(name = name))
                }
            } catch (err: Exception) {
                Log.w(TAG, "renamePlaylist: error: ${err.message}", err)
            }
        }.start()
    }

    // --- Internal request builders (injectable for unit testing) ---

    internal fun getPlaylistItemsRequest(playlistId: UUID, userId: String?): GetPlaylistItemsRequest =
        GetPlaylistItemsRequest(
            playlistId = playlistId,
            userId = QueryUtil.toUuidOrNull(userId),
            fields = listOf(ItemFields.MEDIA_SOURCES)
        )
}
