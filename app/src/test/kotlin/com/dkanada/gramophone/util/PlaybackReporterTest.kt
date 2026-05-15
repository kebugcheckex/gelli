package com.dkanada.gramophone.util

import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PlaybackReporterTest {

    private val itemId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")

    // --- buildStartInfo ---

    @Test
    fun buildStartInfo_setsItemId() {
        val info = PlaybackReporter.buildStartInfo(itemId, volume = 80)
        assertEquals(itemId, info.itemId)
    }

    @Test
    fun buildStartInfo_canSeekAndNotPaused() {
        val info = PlaybackReporter.buildStartInfo(itemId, volume = 80)
        assertTrue(info.canSeek)
        assertFalse(info.isPaused)
        assertFalse(info.isMuted)
    }

    @Test
    fun buildStartInfo_setsVolume() {
        val info = PlaybackReporter.buildStartInfo(itemId, volume = 65)
        assertEquals(65, info.volumeLevel)
    }

    @Test
    fun buildStartInfo_defaultPlaybackEnums() {
        val info = PlaybackReporter.buildStartInfo(itemId, volume = 100)
        assertEquals(PlayMethod.DIRECT_PLAY, info.playMethod)
        assertEquals(RepeatMode.REPEAT_NONE, info.repeatMode)
        assertEquals(PlaybackOrder.DEFAULT, info.playbackOrder)
    }

    // --- buildProgressInfo ---

    @Test
    fun buildProgressInfo_convertsMillisToTicks() {
        val info = PlaybackReporter.buildProgressInfo(itemId, progressMs = 5000L, volume = 80, isPaused = false, playSessionId = "s1")
        assertEquals(5000L * 10000L, info.positionTicks)
    }

    @Test
    fun buildProgressInfo_propagatesIsPaused() {
        val paused = PlaybackReporter.buildProgressInfo(itemId, progressMs = 0, volume = 0, isPaused = true, playSessionId = "s")
        val playing = PlaybackReporter.buildProgressInfo(itemId, progressMs = 0, volume = 0, isPaused = false, playSessionId = "s")
        assertTrue(paused.isPaused)
        assertFalse(playing.isPaused)
    }

    @Test
    fun buildProgressInfo_setsPlaySessionId() {
        val info = PlaybackReporter.buildProgressInfo(itemId, progressMs = 0, volume = 0, isPaused = false, playSessionId = "abc123")
        assertEquals("abc123", info.playSessionId)
    }

    @Test
    fun buildProgressInfo_canSeekAndNotMuted() {
        val info = PlaybackReporter.buildProgressInfo(itemId, progressMs = 0, volume = 50, isPaused = false, playSessionId = "s")
        assertTrue(info.canSeek)
        assertFalse(info.isMuted)
    }

    @Test
    fun buildProgressInfo_defaultPlaybackEnums() {
        val info = PlaybackReporter.buildProgressInfo(itemId, progressMs = 0, volume = 0, isPaused = false, playSessionId = "s")
        assertEquals(PlayMethod.DIRECT_PLAY, info.playMethod)
        assertEquals(RepeatMode.REPEAT_NONE, info.repeatMode)
        assertEquals(PlaybackOrder.DEFAULT, info.playbackOrder)
    }

    // --- buildStopInfo ---

    @Test
    fun buildStopInfo_setsItemId() {
        val info = PlaybackReporter.buildStopInfo(itemId, progressMs = 1000L)
        assertEquals(itemId, info.itemId)
    }

    @Test
    fun buildStopInfo_convertsMillisToTicks() {
        val info = PlaybackReporter.buildStopInfo(itemId, progressMs = 3000L)
        assertEquals(3000L * 10000L, info.positionTicks)
    }

    @Test
    fun buildStopInfo_notFailed() {
        val info = PlaybackReporter.buildStopInfo(itemId, progressMs = 0)
        assertFalse(info.failed)
    }

    @Test
    fun buildStopInfo_zeroProgress() {
        val info = PlaybackReporter.buildStopInfo(itemId, progressMs = 0)
        assertEquals(0L, info.positionTicks)
    }

    // --- Regression: positionTicks must be ticks not millis (1 tick = 10000 ms) ---

    @Test
    fun progressInfo_tickConversionIsNotIdentity() {
        val info = PlaybackReporter.buildProgressInfo(itemId, progressMs = 1L, volume = 0, isPaused = false, playSessionId = "s")
        assertEquals(10000L, info.positionTicks)
    }

    @Test
    fun stopInfo_tickConversionIsNotIdentity() {
        val info = PlaybackReporter.buildStopInfo(itemId, progressMs = 1L)
        assertEquals(10000L, info.positionTicks)
    }
}
