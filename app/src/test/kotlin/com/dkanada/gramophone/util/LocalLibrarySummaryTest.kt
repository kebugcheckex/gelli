package com.dkanada.gramophone.util

import com.dkanada.gramophone.database.LocalSong
import com.dkanada.gramophone.database.LocalSong.Source
import com.dkanada.gramophone.database.LocalSong.State
import com.dkanada.gramophone.database.LocalSong.Variant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalLibrarySummaryTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val accountId = "server/user"

    private fun audioDirectory(account: String = accountId, volume: String = "internal"): File =
        File(temporary.root, "$volume/library/${LocalLibrary.accountDirName(account)}/audio").apply { mkdirs() }

    private fun song(
        id: String,
        file: File?,
        state: State = State.COMPLETE,
        account: String = accountId,
        source: Source = Source.EXPLICIT
    ) = LocalSong(account, id, state, source).apply {
        filePath = file?.absolutePath
        // Deliberately stale: usage must come from disk, not this metadata field.
        fileSize = 999_999
    }

    private fun audio(directory: File, name: String, size: Int): File =
        File(directory, name).apply { writeBytes(ByteArray(size)) }

    @Test
    fun availability_rejectsAbsentEmptyDirectoryAndUnreadableFiles() {
        assertNull(playableLocalFile(null))
        assertNull(playableLocalFile(File(temporary.root, "absent.mp3")))
        assertNull(playableLocalFile(temporary.newFile("empty.mp3")))
        assertNull(playableLocalFile(temporary.newFolder("directory")))

        val file = temporary.newFile("valid.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(file, playableLocalFile(file))
        // Windows does not reliably support removing read permission with File.setReadable.
        val unreadable = object : File(file.absolutePath) {
            override fun canRead() = false
        }
        assertNull(playableLocalFile(unreadable))
        val denied = object : File(file.absolutePath) {
            override fun isFile(): Boolean = throw SecurityException("access denied")
        }
        assertNull(playableLocalFile(denied))
    }

    @Test
    fun summary_countsBothSourcesAndVariantsUsingMeasuredSizes() {
        val directory = audioDirectory()
        val explicit = song("explicit", audio(directory, "explicit.flac", 13)).apply { variant = Variant.ORIGINAL }
        val played = song("played", audio(directory, "played.mp3", 7), source = Source.PLAYED)
            .apply { variant = Variant.TRANSCODED }

        val summary = LocalLibrarySummaryReader.read(accountId, listOf(explicit, played), listOf(directory)) { true }

        assertEquals(accountId, summary.accountId)
        assertEquals(setOf("explicit", "played"), summary.availableSongIds)
        assertEquals(2, summary.availableSongCount)
        assertEquals(20L, summary.audioBytes)
    }

    @Test
    fun summary_excludesEveryNonCompleteStateAndInvalidCompleteFiles() {
        val directory = audioDirectory()
        val rows = State.entries.map { state ->
            song(state.name, audio(directory, "${state.name}.mp3", 2), state)
        } + listOf(
            song("empty", audio(directory, "empty.mp3", 0)),
            song("absent", File(directory, "absent.mp3")),
            song("null", null),
            song("directory", directory)
        )

        val summary = LocalLibrarySummaryReader.read(accountId, rows, listOf(directory)) { true }

        assertEquals(setOf("COMPLETE"), summary.availableSongIds)
        // Disk usage includes real audio files even if the database no longer marks them complete.
        assertEquals(10L, summary.audioBytes)
    }

    @Test
    fun summary_keepsAccountsSeparateAndDoesNotDuplicateSongIds() {
        val directory = audioDirectory()
        val otherDirectory = audioDirectory("other/user")
        val ownSong = song("same-id", audio(directory, "own.mp3", 3))
        val otherSong = song("other-id", audio(otherDirectory, "other.mp3", 100), account = "other/user")

        val summary = LocalLibrarySummaryReader.read(
            accountId, listOf(ownSong, ownSong, otherSong), listOf(directory)
        ) { true }

        assertEquals(setOf("same-id"), summary.availableSongIds)
        assertEquals(1, summary.availableSongCount)
        assertEquals(3L, summary.audioBytes)
    }

    @Test
    fun summary_measuresBothStorageLocationsAndUntrackedAudioWithoutCountingArtworkOrTemp() {
        val internal = audioDirectory()
        val external = audioDirectory(volume = "external")
        audio(internal, "untracked.mp3", 11)
        val stored = song("saved", audio(external, "saved.mp3", 17))
        val accountDirectory = internal.parentFile!!
        audio(File(accountDirectory, "images").apply { mkdirs() }, "cover.jpg", 100)
        audio(File(accountDirectory, "tmp").apply { mkdirs() }, "saving.part", 200)

        val summary = LocalLibrarySummaryReader.read(
            accountId, listOf(stored), listOf(internal, external, File(internal, "."))
        ) { true }

        assertEquals(setOf("saved"), summary.availableSongIds)
        assertEquals(28L, summary.audioBytes)
    }

    @Test
    fun summary_newAccountOnAccessibleStorageHasZeroUsage() {
        val directory = File(temporary.root, "not-created-yet/audio")

        val summary = LocalLibrarySummaryReader.read(accountId, emptyList(), listOf(directory)) { true }

        assertEquals(0, summary.availableSongCount)
        assertEquals(0L, summary.audioBytes)
    }

    @Test
    fun summary_unmountedStorageMakesTotalUnknownWhileInternalSongsRemainAvailable() {
        val internal = audioDirectory()
        val external = File(temporary.root, "unmounted/library/audio")
        val rows = listOf(
            song("internal", audio(internal, "saved.mp3", 13)),
            song("external", File(external, "missing.mp3"), State.MISSING)
        )

        val summary = LocalLibrarySummaryReader.read(accountId, rows, listOf(internal, external)) { it != external }

        assertEquals(setOf("internal"), summary.availableSongIds)
        assertNull(summary.audioBytes)
    }

    @Test
    fun summary_failedDirectoryListingIsUnknownRatherThanZero() {
        val actual = audioDirectory()
        audio(actual, "saved.mp3", 10)
        val unreadable = object : File(actual.absolutePath) {
            override fun listFiles(): Array<File>? = null
        }

        val summary = LocalLibrarySummaryReader.read(accountId, emptyList(), listOf(unreadable)) { true }

        assertNull(summary.audioBytes)
    }

    @Test
    fun summary_rechecksFilesAfterRemovalAndRestoration() {
        val directory = audioDirectory()
        val file = audio(directory, "saved.mp3", 5)
        val row = song("saved", file)
        fun read() = LocalLibrarySummaryReader.read(accountId, listOf(row), listOf(directory)) { true }

        assertEquals(1, read().availableSongCount)
        assertNotNull(playableLocalFile(file))
        check(file.delete())
        assertEquals(0, read().availableSongCount)
        assertEquals(0L, read().audioBytes)
        file.writeBytes(byteArrayOf(1, 2))
        assertEquals(1, read().availableSongCount)
        assertEquals(2L, read().audioBytes)
    }
}
