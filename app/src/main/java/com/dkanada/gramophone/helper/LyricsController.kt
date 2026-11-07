package com.dkanada.gramophone.helper

import android.os.Handler
import android.os.Looper
import com.dkanada.gramophone.App
import com.dkanada.gramophone.model.Lyrics
import com.dkanada.gramophone.util.LyricsRepository
import com.dkanada.gramophone.util.PreferenceUtil

/**
 * Owns the current song's lyrics for one player fragment: whether they are on, loading them for the song that is
 * playing, and following the playback position. Nothing is fetched while they are off, and the position is only
 * read while the expanded player is on screen.
 *
 * Every open activity has its own player, and all of them hear the service's events. Only the resumed one fetches,
 * and the others catch up on the preference and the song when they come back.
 */
class LyricsController(private val listener: Listener) {
    enum class State {
        OFF,
        LOADING,
        MISSING,
        READY,
    }

    interface Listener {
        fun onLyricsStateChanged()

        /** The line is -1 before the first line starts. */
        fun onCurrentLyricLineChanged(line: Int)
    }

    private val preferences = PreferenceUtil.getInstance(App.getInstance())
    private val handler = Handler(Looper.getMainLooper())

    private var enabled = preferences.lyricsEnabled

    // the song that lyrics were last requested for, which a late response is checked against
    private var songId: String? = null
    private var playerVisible = false
    private var resumed = false
    private var released = false

    var state = State.OFF
        private set
    var lyrics: Lyrics? = null
        private set
    var currentLine = -1
        private set

    private val clock = object : Runnable {
        override fun run() {
            resample()
            handler.postDelayed(this, if (MusicPlayerRemote.isPlaying()) PLAYING_INTERVAL else PAUSED_INTERVAL)
        }
    }

    fun isEnabled() = enabled

    fun setEnabled(value: Boolean) {
        preferences.lyricsEnabled = value
        applyEnabled(value)
    }

    private fun applyEnabled(value: Boolean) {
        if (enabled != value) {
            enabled = value

            if (!value) {
                songId = null
                setState(State.OFF, null)
            }
        }

        onSongChanged()
    }

    /** Safe to call for any service event: a song that already has its lyrics only has its position checked. */
    fun onSongChanged() {
        if (!enabled || !resumed) return

        val id = MusicPlayerRemote.getCurrentSong()?.id
        if (id.isNullOrEmpty()) {
            songId = null
            setState(State.MISSING, null)
            return
        }

        if (id == songId) {
            resample()
            return
        }

        songId = id
        setState(State.LOADING, null)
        LyricsRepository.load(id) { loadedId, loaded ->
            // the queue can move on before the response arrives
            if (!released && loadedId == songId) {
                setState(if (loaded != null) State.READY else State.MISSING, loaded)
            }
        }
    }

    /** Reads the position now, for events that can move it, such as a seek or a change of play state. */
    fun resample() {
        val current = lyrics ?: return
        if (MusicPlayerRemote.getCurrentSong()?.id != songId) return

        val line = current.lineAt(MusicPlayerRemote.getSongProgressMillis().toLong())
        if (line != currentLine) {
            currentLine = line
            listener.onCurrentLyricLineChanged(line)
        }
    }

    fun setPlayerVisible(visible: Boolean) {
        playerVisible = visible
        updateClock()
    }

    fun setResumed(value: Boolean) {
        resumed = value
        if (value) applyEnabled(preferences.lyricsEnabled)
        updateClock()
    }

    fun release() {
        released = true
        handler.removeCallbacks(clock)
    }

    private fun setState(state: State, lyrics: Lyrics?) {
        this.state = state
        this.lyrics = lyrics
        currentLine = -1

        listener.onLyricsStateChanged()
        updateClock()
    }

    private fun updateClock() {
        handler.removeCallbacks(clock)
        if (playerVisible && resumed && lyrics?.isSynced == true) handler.post(clock)
    }

    private companion object {
        const val PLAYING_INTERVAL = 100L
        const val PAUSED_INTERVAL = 250L
    }
}
