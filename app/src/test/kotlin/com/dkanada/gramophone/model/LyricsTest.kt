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
}
