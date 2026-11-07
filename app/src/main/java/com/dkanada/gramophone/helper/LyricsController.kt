package com.dkanada.gramophone.helper

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
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

        /**
         * Called when the current line or its fill changes, which is every frame while a synced lyric plays. The
         * line is -1 before the first line starts, and the fill runs from 0 at the line's start to 1 at its end.
         */
        fun onLyricsPositionChanged(line: Int, fill: Float)
    }

    private val preferences = PreferenceUtil.getInstance(App.getInstance())
    private val handler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()

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
    var currentFill = 0f
        private set

    // every frame while playing, so the fill moves smoothly, and a slow poll while paused, so a seek still shows
    private val frameTick = Choreographer.FrameCallback { tick() }
    private val pausedTick = Runnable { tick() }

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

        val position = MusicPlayerRemote.getSongProgressMillis().toLong()
        val line = current.lineAt(position)
        val fill = current.fillAt(line, position, MusicPlayerRemote.getSongDurationMillis().toLong())
        if (line != currentLine || fill != currentFill) {
            currentLine = line
            currentFill = fill
            listener.onLyricsPositionChanged(line, fill)
        }
    }

    /** Switches the clock between frames and the slow poll. */
    fun onPlayStateChanged() {
        updateClock()
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
        stopClock()
    }

    private fun setState(state: State, lyrics: Lyrics?) {
        this.state = state
        this.lyrics = lyrics
        currentLine = -1
        currentFill = 0f

        listener.onLyricsStateChanged()
        updateClock()
    }

    private fun updateClock() {
        stopClock()
        if (playerVisible && resumed && !released && lyrics?.isSynced == true) tick()
    }

    private fun tick() {
        resample()
        if (MusicPlayerRemote.isPlaying()) {
            choreographer.postFrameCallback(frameTick)
        } else {
            handler.postDelayed(pausedTick, PAUSED_INTERVAL)
        }
    }

    private fun stopClock() {
        choreographer.removeFrameCallback(frameTick)
        handler.removeCallbacks(pausedTick)
    }

    private companion object {
        const val PAUSED_INTERVAL = 250L
    }
}
