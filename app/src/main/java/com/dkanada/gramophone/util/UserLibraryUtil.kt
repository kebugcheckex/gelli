package com.dkanada.gramophone.util

import android.util.Log
import com.dkanada.gramophone.model.Song
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.userLibraryApi

object UserLibraryUtil {
    private const val TAG = "UserLibraryUtil"

    @JvmStatic
    fun toggleFavorite(song: Song) {
        val api = JellyfinSdkSession.createApiOrNull() ?: return
        val itemId = QueryUtil.toUuidOrNull(song.id) ?: return
        val userId = QueryUtil.toUuidOrNull(JellyfinSdkSession.getCurrentUserId())
        val newFavorite = !song.favorite
        song.favorite = newFavorite

        Thread {
            try {
                runBlocking {
                    val data by if (newFavorite) {
                        api.userLibraryApi.markFavoriteItem(itemId, userId)
                    } else {
                        api.userLibraryApi.unmarkFavoriteItem(itemId, userId)
                    }
                    song.favorite = data.isFavorite
                }
            } catch (err: ApiClientException) {
                Log.w(TAG, "toggleFavorite: ${err.message}", err)
            } catch (err: Exception) {
                Log.w(TAG, "toggleFavorite: unexpected error: ${err.message}", err)
            }
        }.start()
    }
}
