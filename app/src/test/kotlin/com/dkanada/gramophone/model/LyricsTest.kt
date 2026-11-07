package com.dkanada.gramophone.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTest {
    private val synced = Lyrics(
        listOf(
            Lyrics.Line("One", 1000),
            Lyrics.Line("", 2000),
            Lyrics.Line("Three", 3000),
            Lyrics.Line("Three again", 3000),
            Lyrics.Line("Five", 5000),
        )
    )

    @Test
    fun isSynced_onlyWhenEveryLineHasAStart() {
        assertTrue(synced.isSynced)
        assertFalse(Lyrics(listOf(Lyrics.Line("One", 1000), Lyrics.Line("Two", null))).isSynced)
        assertFalse(Lyrics(emptyList()).isSynced)
    }

    @Test
    fun lineAt_nothingBeforeTheFirstLine() {
        assertEquals(-1, synced.lineAt(0))
        assertEquals(-1, synced.lineAt(999))
    }

    @Test
    fun lineAt_lineStartsExactlyOnItsTimestamp() {
        assertEquals(0, synced.lineAt(1000))
        assertEquals(0, synced.lineAt(1999))
        assertEquals(1, synced.lineAt(2000))
    }

    @Test
    fun lineAt_lastLineLastsToTheEnd() {
        assertEquals(4, synced.lineAt(5000))
        assertEquals(4, synced.lineAt(Long.MAX_VALUE))
    }

    @Test
    fun lineAt_takesTheLastOfLinesThatShareATime() {
        assertEquals(3, synced.lineAt(3000))
    }

    @Test
    fun lineAt_unsyncedHasNoCurrentLine() {
        assertEquals(-1, Lyrics(listOf(Lyrics.Line("Plain", null))).lineAt(1000))
    }

    @Test
    fun fillAt_runsFromTheLineStartToTheNextLine() {
        assertEquals(0f, synced.fillAt(0, 1000, 10_000), DELTA)
        assertEquals(0.25f, synced.fillAt(0, 1250, 10_000), DELTA)
        assertEquals(1f, synced.fillAt(0, 2000, 10_000), DELTA)
    }

    @Test
    fun fillAt_lastLineEndsWithTheSong() {
        assertEquals(0.5f, synced.fillAt(4, 7500, 10_000), DELTA)
        assertEquals(1f, synced.fillAt(4, 12_000, 10_000), DELTA)
    }

    @Test
    fun fillAt_lineWithNoLengthIsFullOnceStarted() {
        // the player reports an unknown duration as 0, or as C.TIME_UNSET cut down to an int, which is 1
        assertEquals(1f, synced.fillAt(4, 5000, 0), DELTA)
        assertEquals(1f, synced.fillAt(4, 5000, 1), DELTA)
        assertEquals(1f, synced.fillAt(2, 3000, 10_000), DELTA)
    }

    @Test
    fun fillAt_emptyBeforeTheLineStarts() {
        assertEquals(0f, synced.fillAt(4, 4000, 10_000), DELTA)
        assertEquals(0f, synced.fillAt(4, 4000, 0), DELTA)
    }

    @Test
    fun fillAt_nothingOutsideTheLines() {
        assertEquals(0f, synced.fillAt(-1, 500, 10_000), DELTA)
        assertEquals(0f, synced.fillAt(5, 6000, 10_000), DELTA)
        assertEquals(0f, Lyrics(listOf(Lyrics.Line("Plain", null))).fillAt(0, 1000, 10_000), DELTA)
    }

    private companion object {
        const val DELTA = 0.0001f
    }
}
