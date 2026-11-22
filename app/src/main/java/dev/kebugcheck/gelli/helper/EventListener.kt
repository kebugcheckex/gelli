package dev.kebugcheck.gelli.helper

import android.util.Log
import dev.kebugcheck.gelli.util.JellyfinSdkSession
import dev.kebugcheck.gelli.util.ServerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import org.jellyfin.sdk.api.sockets.SocketApiState
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateMessage

object EventListener {
    private const val TAG = "EventListener"

    @Volatile
    private var scope: CoroutineScope? = null

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

        watchConnection(api.webSocket.state, api.baseUrl).launchIn(newScope)

        Log.i(TAG, "WebSocket subscriptions started")
    }

    /** Reconnects the socket after the server address changed, if it was running. */
    fun restart() {
        if (scope != null) start()
    }

    @JvmStatic
    fun stop() {
        scope?.cancel()
        scope = null
        Log.i(TAG, "WebSocket subscriptions stopped")
    }

    /**
     * The server sends keep-alives, so a socket that drops is the first sign of a lost address, even while
     * nothing else talks to the server.
     */
    private fun watchConnection(states: Flow<SocketApiState>, address: String?): Flow<SocketApiState> {
        var connected = false

        return states.onEach { state ->
            if (state is SocketApiState.Connected) {
                connected = true
                return@onEach
            }

            // an error-free disconnect is the socket being closed on purpose
            val dropped = state is SocketApiState.Connecting || (state as? SocketApiState.Disconnected)?.error != null
            if (connected && dropped) {
                Log.i(TAG, "WebSocket dropped: $state")
                ServerConnection.reportFailure(address)
            }
            connected = false
        }
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
