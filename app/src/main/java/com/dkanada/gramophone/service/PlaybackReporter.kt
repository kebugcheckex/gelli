package com.dkanada.gramophone.service

import android.util.Log
import com.dkanada.gramophone.util.JellyfinSdkSession
import com.dkanada.gramophone.util.QueryUtil
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.RepeatMode
import java.util.UUID

object PlaybackReporter {
    private const val TAG = "PlaybackReporter"

    @JvmStatic
    fun reportStart(itemId: String, volume: Int) {
        val itemUuid = QueryUtil.toUuidOrNull(itemId) ?: return logSkip("reportStart", "invalid itemId: $itemId")
        val api = JellyfinSdkSession.createApiOrNull() ?: return logSkip("reportStart", "missing SDK session state")
        Thread {
            try {
                runBlocking { api.playStateApi.reportPlaybackStart(buildStartInfo(itemUuid, volume)) }
            } catch (err: Exception) {
                Log.w(TAG, "reportStart: error: ${err.message}", err)
            }
        }.start()
    }

    @JvmStatic
    fun reportProgress(itemId: String, positionMillis: Long, volume: Int, isPaused: Boolean, playSessionId: String) {
        val itemUuid = QueryUtil.toUuidOrNull(itemId) ?: return logSkip("reportProgress", "invalid itemId: $itemId")
        val api = JellyfinSdkSession.createApiOrNull() ?: return logSkip("reportProgress", "missing SDK session state")
        Thread {
            try {
                runBlocking {
                    api.playStateApi.reportPlaybackProgress(
                        buildProgressInfo(itemUuid, positionMillis, volume, isPaused, playSessionId)
                    )
                }
            } catch (err: Exception) {
                Log.w(TAG, "reportProgress: error: ${err.message}", err)
            }
        }.start()
    }

    @JvmStatic
    fun markPlayed(itemId: String) {
        val itemUuid = QueryUtil.toUuidOrNull(itemId) ?: return logSkip("markPlayed", "invalid itemId: $itemId")
        val api = JellyfinSdkSession.createApiOrNull() ?: return logSkip("markPlayed", "missing SDK session state")
        Thread {
            try {
                runBlocking { api.playStateApi.markPlayedItem(itemUuid) }
            } catch (err: Exception) {
                Log.w(TAG, "markPlayed: error: ${err.message}", err)
            }
        }.start()
    }

    private fun logSkip(method: String, reason: String) {
        Log.w(TAG, "$method: $reason")
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
            playbackOrder = PlaybackOrder.DEFAULT,
        )

    internal fun buildProgressInfo(
        itemId: UUID,
        positionMillis: Long,
        volume: Int,
        isPaused: Boolean,
        playSessionId: String,
    ): PlaybackProgressInfo =
        PlaybackProgressInfo(
            itemId = itemId,
            canSeek = true,
            isPaused = isPaused,
            isMuted = false,
            positionTicks = positionMillis * 10000,
            volumeLevel = volume,
            playSessionId = playSessionId,
            playMethod = PlayMethod.DIRECT_PLAY,
            repeatMode = RepeatMode.REPEAT_NONE,
            playbackOrder = PlaybackOrder.DEFAULT,
        )
}
