package com.dkanada.gramophone.util

import com.dkanada.gramophone.database.LocalSong.Source
import com.dkanada.gramophone.database.LocalSong.State
import com.dkanada.gramophone.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLibraryTest {

    private val accountId = "https://jellyfin.example.com:8096" + "0123456789abcdef0123456789abcdef"

    // --- accountDirName ---

    @Test
    fun accountDirName_isShortLowercaseHex() {
        val name = LocalLibrary.accountDirName(accountId)
        assertEquals(16, name.length)
        assertTrue(name.matches(Regex("[0-9a-f]+")))
    }

    @Test
    fun accountDirName_isStable() {
        assertEquals(LocalLibrary.accountDirName(accountId), LocalLibrary.accountDirName(accountId))
    }

    @Test
    fun accountDirName_matchesSha256Prefix() {
        // sha256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
        assertEquals("ba7816bf8f01cfea", LocalLibrary.accountDirName("abc"))
    }

    @Test
    fun accountDirName_differsAcrossServers() {
        val other = "https://other.example.com" + "0123456789abcdef0123456789abcdef"
        assertNotEquals(LocalLibrary.accountDirName(accountId), LocalLibrary.accountDirName(other))
    }

    // --- verifiedState ---

    @Test
    fun verifiedState_completeWithoutFile_becomesMissing() {
        assertEquals(State.MISSING, LocalLibrary.verifiedState(State.COMPLETE, fileExists = false))
    }

    @Test
    fun verifiedState_completeWithFile_staysComplete() {
        assertEquals(State.COMPLETE, LocalLibrary.verifiedState(State.COMPLETE, fileExists = true))
    }

    @Test
    fun verifiedState_missingWithFile_becomesComplete() {
        assertEquals(State.COMPLETE, LocalLibrary.verifiedState(State.MISSING, fileExists = true))
    }

    @Test
    fun verifiedState_missingWithoutFile_staysMissing() {
        assertEquals(State.MISSING, LocalLibrary.verifiedState(State.MISSING, fileExists = false))
    }

    @Test
    fun verifiedState_otherStates_unchanged() {
        for (state in listOf(State.QUEUED, State.DOWNLOADING, State.FAILED)) {
            assertEquals(state, LocalLibrary.verifiedState(state, fileExists = false))
            assertEquals(state, LocalLibrary.verifiedState(state, fileExists = true))
        }
    }

    // --- mergedSource ---

    @Test
    fun mergedSource_explicitIsKeptOverPlayed() {
        assertEquals(Source.EXPLICIT, LocalLibrary.mergedSource(Source.EXPLICIT, Source.PLAYED))
    }

    @Test
    fun mergedSource_explicitTakesOverPlayed() {
        assertEquals(Source.EXPLICIT, LocalLibrary.mergedSource(Source.PLAYED, Source.EXPLICIT))
    }

    @Test
    fun mergedSource_sameSourceUnchanged() {
        assertEquals(Source.PLAYED, LocalLibrary.mergedSource(Source.PLAYED, Source.PLAYED))
        assertEquals(Source.EXPLICIT, LocalLibrary.mergedSource(Source.EXPLICIT, Source.EXPLICIT))
    }

    // --- playablePosition ---

    private val songs = List(5) { Song() }

    @Test
    fun playablePosition_allPlayable_unchanged() {
        for (position in songs.indices) {
            assertEquals(position, LocalLibrary.playablePosition(songs, songs, position))
        }
    }

    @Test
    fun playablePosition_keptSong_countsKeptSongsBefore() {
        val playable = listOf(songs[1], songs[3], songs[4])
        assertEquals(0, LocalLibrary.playablePosition(songs, playable, 1))
        assertEquals(1, LocalLibrary.playablePosition(songs, playable, 3))
        assertEquals(2, LocalLibrary.playablePosition(songs, playable, 4))
    }

    @Test
    fun playablePosition_droppedSong_mapsToNextKept() {
        val playable = listOf(songs[1], songs[3])
        assertEquals(0, LocalLibrary.playablePosition(songs, playable, 0))
        assertEquals(1, LocalLibrary.playablePosition(songs, playable, 2))
    }

    @Test
    fun playablePosition_droppedTail_mapsToLastKept() {
        val playable = listOf(songs[0], songs[1])
        assertEquals(1, LocalLibrary.playablePosition(songs, playable, 4))
    }

    @Test
    fun playablePosition_invalid_returnsMinusOne() {
        assertEquals(-1, LocalLibrary.playablePosition(songs, songs, -1))
        assertEquals(-1, LocalLibrary.playablePosition(songs, songs, songs.size))
        assertEquals(-1, LocalLibrary.playablePosition(songs, emptyList(), 0))
    }
}
