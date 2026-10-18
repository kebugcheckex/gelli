package com.dkanada.gramophone.service

import android.os.Handler
import com.dkanada.gramophone.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveTriggerTest {
    private var now = 0L
    private var mode = SaveTrigger.Mode.DELAY
    private val saved = mutableListOf<Song>()

    // the handler is a stub here, so tests run the scheduled check by calling evaluate()
    private val trigger = SaveTrigger(
        { now },
        Handler(),
        { SaveTrigger.Settings(mode, 30_000) },
        { saved.add(it) }
    )

    private fun listen(ms: Long) {
        now += ms
        trigger.evaluate()
    }

    // --- delay ---

    @Test
    fun delay_savesAfterListeningTime() {
        val song = Song()
        trigger.onTrackChanged(song)
        trigger.onPlayingChanged(true)

        listen(29_999)
        assertTrue(saved.isEmpty())

        listen(1)
        assertEquals(listOf(song), saved)
    }

    @Test
    fun delay_pausedTimeDoesNotCount() {
        trigger.onTrackChanged(Song())
        trigger.onPlayingChanged(true)
        listen(20_000)

        trigger.onPlayingChanged(false)
        listen(60_000)
        assertTrue(saved.isEmpty())

        trigger.onPlayingChanged(true)
        listen(10_000)
        assertEquals(1, saved.size)
    }

    @Test
    fun delay_trackChangeResetsListeningTime() {
        trigger.onTrackChanged(Song())
        trigger.onPlayingChanged(true)
        listen(20_000)

        val next = Song()
        trigger.onTrackChanged(next)
        listen(20_000)
        assertTrue(saved.isEmpty())

        listen(10_000)
        assertEquals(listOf(next), saved)
    }

    @Test
    fun delay_savesOncePerTrack() {
        trigger.onTrackChanged(Song())
        trigger.onPlayingChanged(true)
        listen(30_000)
        listen(30_000)
        trigger.onCompleted()

        assertEquals(1, saved.size)
    }

    @Test
    fun delay_repeatOfSameTrackCountsAgain() {
        val song = Song()
        trigger.onTrackChanged(song)
        trigger.onPlayingChanged(true)
        listen(30_000)

        trigger.onTrackChanged(song)
        listen(30_000)

        assertEquals(listOf(song, song), saved)
    }

    @Test
    fun delay_playingAcrossTrackChangeKeepsCounting() {
        trigger.onPlayingChanged(true)
        trigger.onTrackChanged(Song())
        listen(30_000)

        assertEquals(1, saved.size)
    }

    // --- other modes ---

    @Test
    fun immediately_savesWhenPlaybackStarts() {
        mode = SaveTrigger.Mode.IMMEDIATELY
        trigger.onTrackChanged(Song())
        assertTrue(saved.isEmpty())

        trigger.onPlayingChanged(true)
        assertEquals(1, saved.size)
    }

    @Test
    fun completion_savesOnlyWhenCompleted() {
        mode = SaveTrigger.Mode.COMPLETION
        trigger.onTrackChanged(Song())
        trigger.onPlayingChanged(true)
        listen(600_000)
        assertTrue(saved.isEmpty())

        trigger.onCompleted()
        assertEquals(1, saved.size)
    }

    @Test
    fun never_doesNotSave() {
        mode = SaveTrigger.Mode.NEVER
        trigger.onTrackChanged(Song())
        trigger.onPlayingChanged(true)
        listen(600_000)
        trigger.onCompleted()

        assertTrue(saved.isEmpty())
    }

    @Test
    fun settingsChange_appliesToCurrentTrack() {
        mode = SaveTrigger.Mode.NEVER
        trigger.onTrackChanged(Song())
        trigger.onPlayingChanged(true)
        now += 40_000

        mode = SaveTrigger.Mode.DELAY
        trigger.onSettingsChanged()
        assertEquals(1, saved.size)
    }

    @Test
    fun noTrack_doesNotSave() {
        mode = SaveTrigger.Mode.IMMEDIATELY
        trigger.onTrackChanged(null)
        trigger.onPlayingChanged(true)

        assertTrue(saved.isEmpty())
    }
}
