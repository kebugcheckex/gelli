package com.dkanada.gramophone.util

import android.util.Log
import com.dkanada.gramophone.model.Song
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.RepeatMode
import java.util.UUID

object PlaybackReporter {
    private const val TAG = "PlaybackReporter"

    @JvmStatic
    fun reportStart(song: Song, volume: Int) {
        val api = JellyfinSdkSession.createApiOrNull() ?: return
        val itemId = QueryUtil.toUuidOrNull(song.id) ?: return
        Thread {
            try {
                runBlocking { api.playStateApi.reportPlaybackStart(buildStartInfo(itemId, volume)) }
            } catch (err: ApiClientException) {
                Log.w(TAG, "reportStart: ${err.message}", err)
            } catch (err: Exception) {
                Log.w(TAG, "reportStart: unexpected error: ${err.message}", err)
            }
        }.start()
    }

    @JvmStatic
    fun reportProgress(song: Song, progressMs: Long, volume: Int, isPaused: Boolean) {
        val api = JellyfinSdkSession.createApiOrNull() ?: return
        val itemId = QueryUtil.toUuidOrNull(song.id) ?: return
        val sessionId = song.id.hashCode().toString()
        Thread {
            try {
                runBlocking {
                    api.playStateApi.reportPlaybackProgress(
                        buildProgressInfo(itemId, progressMs, volume, isPaused, sessionId)
                    )
                }
            } catch (err: ApiClientException) {
                Log.w(TAG, "reportProgress: ${err.message}", err)
            } catch (err: Exception) {
                Log.w(TAG, "reportProgress: unexpected error: ${err.message}", err)
            }
        }.start()
    }

    @JvmStatic
    fun reportStop(song: Song, progressMs: Long) {
        val api = JellyfinSdkSession.createApiOrNull() ?: return
        val itemId = QueryUtil.toUuidOrNull(song.id) ?: return
        Thread {
            try {
                runBlocking { api.playStateApi.reportPlaybackStopped(buildStopInfo(itemId, progressMs)) }
            } catch (err: ApiClientException) {
                Log.w(TAG, "reportStop: ${err.message}", err)
            } catch (err: Exception) {
                Log.w(TAG, "reportStop: unexpected error: ${err.message}", err)
            }
        }.start()
    }

    @JvmStatic
    fun markPlayed(song: Song) {
        val api = JellyfinSdkSession.createApiOrNull() ?: return
        val itemId = QueryUtil.toUuidOrNull(song.id) ?: return
        val userId = QueryUtil.toUuidOrNull(JellyfinSdkSession.getCurrentUserId())
        Thread {
            try {
                runBlocking { api.playStateApi.markPlayedItem(itemId, userId, DateTime.now()) }
            } catch (err: ApiClientException) {
                Log.w(TAG, "markPlayed: ${err.message}", err)
            } catch (err: Exception) {
                Log.w(TAG, "markPlayed: unexpected error: ${err.message}", err)
            }
        }.start()
    }

    // --- Internal request builders (injectable for unit testing) ---

    internal fun buildStartInfo(itemId: UUID, volume: Int): PlaybackStartInfo =
        PlaybackStartInfo(
            itemId = itemId,
            canSeek = true,
            isPaused = false,
            isMuted = false,
            volumeLevel = volume,
            playMethod = PlayMethod.DIRECT_PLAY,
            repeatMode = RepeatMode.REPEAT_NONE,
            playbackOrder = PlaybackOrder.DEFAULT
        )

    internal fun buildProgressInfo(
        itemId: UUID,
        progressMs: Long,
        volume: Int,
        isPaused: Boolean,
        playSessionId: String,
    ): PlaybackProgressInfo =
        PlaybackProgressInfo(
            itemId = itemId,
            positionTicks = progressMs * 10000L,
            canSeek = true,
            isPaused = isPaused,
            isMuted = false,
            volumeLevel = volume,
            playSessionId = playSessionId,
            playMethod = PlayMethod.DIRECT_PLAY,
            repeatMode = RepeatMode.REPEAT_NONE,
            playbackOrder = PlaybackOrder.DEFAULT
        )

    internal fun buildStopInfo(itemId: UUID, progressMs: Long): PlaybackStopInfo =
        PlaybackStopInfo(
            itemId = itemId,
            positionTicks = progressMs * 10000L,
            failed = false
        )
}
