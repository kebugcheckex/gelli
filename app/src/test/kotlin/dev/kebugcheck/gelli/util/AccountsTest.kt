package dev.kebugcheck.gelli.util

import dev.kebugcheck.gelli.database.AddressConverter
import dev.kebugcheck.gelli.database.LocalSong.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AccountsTest {

    private val serverId = "0123456789abcdef0123456789abcdef"
    private val userId = "fedcba9876543210fedcba9876543210"

    // --- identity ---

    @Test
    fun accountId_doesNotDependOnAnAddress() {
        assertEquals("$serverId:$userId", Accounts.accountId(serverId, userId))
    }

    @Test
    fun normalizeServerId_ignoresDashesAndCase() {
        assertEquals(serverId, Accounts.normalizeServerId("01234567-89AB-CDEF-0123-456789ABCDEF"))
    }

    // --- addresses ---

    @Test
    fun withAddress_appendsANewAddress() {
        assertEquals(
            listOf("http://192.168.1.10:8096", "https://music.example.com"),
            Accounts.withAddress(listOf("http://192.168.1.10:8096"), "https://music.example.com")
        )
    }

    @Test
    fun withAddress_keepsAKnownAddressInPlace() {
        val addresses = listOf("http://192.168.1.10:8096", "https://music.example.com")
        assertEquals(addresses, Accounts.withAddress(addresses, "http://192.168.1.10:8096"))
    }

    @Test
    fun addressConverter_roundTrips() {
        val addresses = listOf("http://192.168.1.10:8096", "https://music.example.com/jellyfin")
        assertEquals(addresses, AddressConverter.toList(AddressConverter.fromList(addresses)))
    }

    @Test
    fun addressConverter_readsAnEmptyColumnAsNoAddresses() {
        assertEquals(emptyList<String>(), AddressConverter.toList(""))
        assertEquals(emptyList<String>(), AddressConverter.toList(null))
    }

    // --- folding stored songs ---

    @Test
    fun replaces_onlyWhenTheIncomingSongIsStoredAndTheCurrentIsNot() {
        assertTrue(Accounts.replaces(State.COMPLETE, State.MISSING))
        assertTrue(Accounts.replaces(State.COMPLETE, State.QUEUED))
        assertFalse(Accounts.replaces(State.COMPLETE, State.COMPLETE))
        assertFalse(Accounts.replaces(State.QUEUED, State.FAILED))
        assertFalse(Accounts.replaces(State.MISSING, State.QUEUED))
    }

    @Test
    fun movedPath_swapsTheAccountFolder() {
        val path = File("/storage/library/aaaa/audio/song.flac").path
        assertEquals(File("/storage/library/bbbb/audio/song.flac").path, Accounts.movedPath(path, "aaaa", "bbbb"))
    }

    @Test
    fun movedPath_leavesFilesOutsideTheAccountFolder() {
        assertNull(Accounts.movedPath(File("/storage/library/cccc/audio/song.flac").path, "aaaa", "bbbb"))
        assertNull(Accounts.movedPath(File("/song.flac").path, "aaaa", "bbbb"))
    }
}
