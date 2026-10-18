package com.dkanada.gramophone.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.VisibleForTesting
import com.dkanada.gramophone.database.LocalSong
import com.dkanada.gramophone.model.Song
import com.dkanada.gramophone.util.LocalLibrary
import com.dkanada.gramophone.util.PreferenceUtil

/**
 * Saves played songs to the local library according to the save trigger setting. Listening time
 * only counts while the player is actually playing, so pauses and buffering are left out.
 *
 * Fires at most once per track. Saving a song that is already stored or queued does nothing, so
 * this never checks the library itself. Must be called on the main thread.
 */
class SaveTrigger @VisibleForTesting constructor(
    private val clock: () -> Long,
    private val handler: Handler,
    private val settings: () -> Settings,
    private val save: (Song) -> Unit
) {
    enum class Mode {
        IMMEDIATELY,
        DELAY,
        COMPLETION,
        NEVER
    }

    data class Settings(val mode: Mode, val delayMs: Long)

    constructor(context: Context) : this(
        SystemClock::elapsedRealtime,
        Handler(Looper.getMainLooper()),
        {
            val preferences = PreferenceUtil.getInstance(context)
            Settings(preferences.librarySaveTrigger, preferences.librarySaveTriggerDelay)
        },
        { LocalLibrary.save(listOf(it), LocalSong.Source.PLAYED) }
    )

    private var song: Song? = null
    private var saved = false

    private var playing = false
    private var listenedMs = 0L
    private var resumedAt = 0L

    private val check = Runnable { evaluate() }

    /** Starts tracking a new track, including a repeat of the same one. */
    fun onTrackChanged(song: Song?) {
        this.song = song
        saved = false
        listenedMs = 0
        resumedAt = clock()

        evaluate()
    }

    fun onPlayingChanged(playing: Boolean) {
        if (playing == this.playing) return

        if (playing) {
            resumedAt = clock()
        } else {
            listenedMs += clock() - resumedAt
        }

        this.playing = playing
        evaluate()
    }

    /** Called when the current track reached its natural end, before the next track starts. */
    fun onCompleted() {
        if (settings().mode == Mode.COMPLETION) fire()
    }

    fun onSettingsChanged() {
        evaluate()
    }

    fun release() {
        handler.removeCallbacks(check)
    }

    @VisibleForTesting
    fun listenedMs(): Long = if (playing) listenedMs + clock() - resumedAt else listenedMs

    @VisibleForTesting
    fun evaluate() {
        handler.removeCallbacks(check)
        if (saved || !playing) return

        val settings = settings()
        when (settings.mode) {
            Mode.IMMEDIATELY -> fire()
            Mode.DELAY -> {
                val remaining = settings.delayMs - listenedMs()
                if (remaining <= 0) fire() else handler.postDelayed(check, remaining)
            }
            Mode.COMPLETION, Mode.NEVER -> {}
        }
    }

    private fun fire() {
        val song = song ?: return
        if (saved) return

        saved = true
        save(song)
    }
}
