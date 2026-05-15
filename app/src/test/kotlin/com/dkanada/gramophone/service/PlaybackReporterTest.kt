package com.dkanada.gramophone.service

import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PlaybackReporterTest {

    private val itemUuid: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")

    // --- buildStartInfo ---

    @Test
    fun buildStartInfo_itemId_passedThrough() {
        val info = PlaybackReporter.buildStartInfo(itemUuid, volume = 80)
        assertEquals(itemUuid, info.itemId)
    }

    @Test
    fun buildStartInfo_canSeek_isTrue() {
        val info = PlaybackReporter.buildStartInfo(itemUuid, volume = 80)
        assertTrue("legacy setCanSeek(true) must be preserved", info.canSeek)
    }

    @Test
    fun buildStartInfo_isPaused_isFalse() {
        val info = PlaybackReporter.buildStartInfo(itemUuid, volume = 80)
        assertTrue("legacy setIsPaused(false) must be preserved", !info.isPaused)
    }

    @Test
    fun buildStartInfo_volumeLevel_matchesPassedValue() {
        val info = PlaybackReporter.buildStartInfo(itemUuid, volume = 75)
        assertEquals(75, info.volumeLevel)
    }

    @Test
    fun buildStartInfo_playMethod_isDirectPlay() {
        val info = PlaybackReporter.buildStartInfo(itemUuid, volume = 80)
        assertEquals(PlayMethod.DIRECT_PLAY, info.playMethod)
    }

    @Test
    fun buildStartInfo_repeatMode_isRepeatNone() {
        val info = PlaybackReporter.buildStartInfo(itemUuid, volume = 80)
        assertEquals(RepeatMode.REPEAT_NONE, info.repeatMode)
    }

    @Test
    fun buildStartInfo_playbackOrder_isDefault() {
        val info = PlaybackReporter.buildStartInfo(itemUuid, volume = 80)
        assertEquals(PlaybackOrder.DEFAULT, info.playbackOrder)
    }

    // --- buildProgressInfo ---

    @Test
    fun buildProgressInfo_positionTicks_isMillisTimesten() {
        val info = PlaybackReporter.buildProgressInfo(itemUuid, positionMillis = 1234L, volume = 50, isPaused = false, playSessionId = "sess")
        // Legacy: progressInfo.setPositionTicks(progress * 10000)
        assertEquals(12340000L, info.positionTicks)
    }

    @Test
    fun buildProgressInfo_itemId_passedThrough() {
        val info = PlaybackReporter.buildProgressInfo(itemUuid, positionMillis = 0L, volume = 50, isPaused = false, playSessionId = "sess")
        assertEquals(itemUuid, info.itemId)
    }

    @Test
    fun buildProgressInfo_volumeLevel_matchesPassedValue() {
        val info = PlaybackReporter.buildProgressInfo(itemUuid, positionMillis = 0L, volume = 42, isPaused = false, playSessionId = "sess")
        assertEquals(42, info.volumeLevel)
    }

    @Test
    fun buildProgressInfo_isPaused_propagatesTrue() {
        val info = PlaybackReporter.buildProgressInfo(itemUuid, positionMillis = 0L, volume = 50, isPaused = true, playSessionId = "sess")
        assertTrue("isPaused=true must propagate to the DTO", info.isPaused)
    }

    @Test
    fun buildProgressInfo_isPaused_propagatesFalse() {
        val info = PlaybackReporter.buildProgressInfo(itemUuid, positionMillis = 0L, volume = 50, isPaused = false, playSessionId = "sess")
        assertTrue("isPaused=false must propagate to the DTO", !info.isPaused)
    }

    @Test
    fun buildProgressInfo_playSessionId_passedThrough() {
        val sessionId = Integer.toString("abc123".hashCode())
        val info = PlaybackReporter.buildProgressInfo(itemUuid, positionMillis = 0L, volume = 50, isPaused = false, playSessionId = sessionId)
        assertEquals(sessionId, info.playSessionId)
    }

    @Test
    fun buildProgressInfo_canSeek_isTrue() {
        val info = PlaybackReporter.buildProgressInfo(itemUuid, positionMillis = 0L, volume = 50, isPaused = false, playSessionId = "sess")
        assertTrue("legacy setCanSeek(true) must be preserved", info.canSeek)
    }
}
