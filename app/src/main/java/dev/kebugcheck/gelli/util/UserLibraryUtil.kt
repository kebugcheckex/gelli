package dev.kebugcheck.gelli.util

import android.util.Log
import android.widget.Toast
import dev.kebugcheck.gelli.App
import dev.kebugcheck.gelli.R
import dev.kebugcheck.gelli.model.Song
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.userLibraryApi

object UserLibraryUtil {
    private const val TAG = "UserLibraryUtil"

    @JvmStatic
    fun toggleFavorite(song: Song) {
        if (NetworkUtil.isOffline(App.getInstance())) {
            Toast.makeText(App.getInstance(), R.string.favorites_unavailable_offline, Toast.LENGTH_SHORT).show()
            return
        }

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
