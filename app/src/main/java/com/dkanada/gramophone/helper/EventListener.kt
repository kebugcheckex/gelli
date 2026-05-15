package com.dkanada.gramophone.helper

import android.util.Log
import com.dkanada.gramophone.helper.MusicPlayerRemote
import com.dkanada.gramophone.util.JellyfinSdkSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateMessage

object EventListener {
    private const val TAG = "EventListener"

    @Volatile
    private var scope: CoroutineScope? = null

    @JvmStatic
    fun start() {
        scope?.cancel()
        val api = JellyfinSdkSession.createApiOrNull() ?: run {
            Log.w(TAG, "start: no SDK session available")
            return
        }
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = newScope

        api.webSocket.subscribe<PlaystateMessage>()
            .onEach { handlePlaystateMessage(it) }
            .launchIn(newScope)

        Log.i(TAG, "WebSocket subscriptions started")
    }

    @JvmStatic
    fun stop() {
        scope?.cancel()
        scope = null
        Log.i(TAG, "WebSocket subscriptions stopped")
    }

    private fun handlePlaystateMessage(message: PlaystateMessage) {
        val request = message.data ?: return
        Log.i(TAG, "onPlaystateCommand: ${request.command}")
        when (request.command) {
            PlaystateCommand.PLAY_PAUSE ->
                if (MusicPlayerRemote.isPlaying()) MusicPlayerRemote.pauseSong()
                else MusicPlayerRemote.resumePlaying()
            PlaystateCommand.PAUSE -> MusicPlayerRemote.pauseSong()
            PlaystateCommand.UNPAUSE -> MusicPlayerRemote.resumePlaying()
            PlaystateCommand.NEXT_TRACK -> MusicPlayerRemote.playNextSong()
            PlaystateCommand.PREVIOUS_TRACK -> MusicPlayerRemote.playPreviousSong()
            PlaystateCommand.SEEK -> {
                val positionMs = (request.seekPositionTicks ?: 0L) / 10000L
                MusicPlayerRemote.seekTo(positionMs.toInt())
            }
            PlaystateCommand.STOP -> MusicPlayerRemote.clearQueue()
            PlaystateCommand.REWIND,
            PlaystateCommand.FAST_FORWARD -> Unit
        }
    }
}
