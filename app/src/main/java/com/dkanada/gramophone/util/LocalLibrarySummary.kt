package com.dkanada.gramophone.util

import com.dkanada.gramophone.database.LocalSong
import java.io.File

/** A local-only snapshot. [audioBytes] is unknown if any known audio storage cannot be measured. */
data class LocalLibrarySummary(
    val accountId: String,
    val availableSongIds: Set<String>,
    val audioBytes: Long?
) {
    val availableSongCount: Int get() = availableSongIds.size
}

/** Shared by playback, verification and summaries. Partial or unreadable files cannot play offline. */
internal fun playableLocalFile(file: File?): File? = try {
    file?.takeIf { it.isFile && it.canRead() && it.length() > 0 }
} catch (_: SecurityException) {
    null
}

/** Reads the flat audio directories used by SaveWorker; artwork and temporary files are separate. */
internal object LocalLibrarySummaryReader {
    fun read(
        accountId: String,
        songs: List<LocalSong>,
        audioDirectories: List<File>,
        isStorageAvailable: (File) -> Boolean
    ): LocalLibrarySummary {
        val ids = songs.asSequence()
            .filter { it.accountId == accountId && it.state == LocalSong.State.COMPLETE }
            .filter { playableLocalFile(it.filePath?.let(::File)) != null }
            .map { it.itemId }
            .toSet()

        var bytes = 0L
        var measured = true
        for (directory in audioDirectories.distinctBy { it.absoluteFile.normalize().path }) {
            val size = measureAudio(directory, isStorageAvailable)
            if (size == null) measured = false else bytes += size
        }

        return LocalLibrarySummary(accountId, ids, if (measured) bytes else null)
    }

    private fun measureAudio(directory: File, isStorageAvailable: (File) -> Boolean): Long? = try {
        if (!isStorageAvailable(directory)) {
            null
        } else {
            // An account with no downloads need not have an audio directory yet. Check the
            // nearest existing parent so access failures aren't mistaken for an empty directory.
            val ancestor = generateSequence(directory) { it.parentFile }.firstOrNull { it.exists() }
            when {
                ancestor == null || !ancestor.isDirectory || !ancestor.canRead() -> null
                !directory.exists() -> 0L
                else -> directory.listFiles()?.let { files ->
                    if (files.any { !it.isFile || !it.canRead() }) null else files.sumOf { it.length() }
                }
            }
        }
    } catch (_: SecurityException) {
        null
    }
}
