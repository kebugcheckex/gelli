package com.dkanada.gramophone.util

import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.dkanada.gramophone.App
import com.dkanada.gramophone.database.LocalSong
import com.dkanada.gramophone.model.Song
import com.dkanada.gramophone.service.SaveWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Persistent store of songs saved on the device, see docs/offline_cached_music_plan.md.
 *
 * Every lookup is scoped to the current account. Rows live in the local_songs table and
 * files live under <root>/<account>/, where <account> is a hash of the local user id.
 */
object LocalLibrary {
    enum class Availability {
        STORED,
        MISSING,
        NONE
    }

    private const val TAG = "LocalLibrary"

    private const val ACCOUNT_DIR_LENGTH = 16
    private const val QUERY_CHUNK_SIZE = 500

    private val dao get() = App.getDatabase().localSongDao()

    /** App-specific storage that survives cache clearing and is removed on uninstall. */
    @JvmStatic
    fun getRoot(): File {
        val context = App.getInstance()
        val external = context.getExternalFilesDir(null)
        if (external != null && Environment.getExternalStorageState(external) == Environment.MEDIA_MOUNTED) {
            return File(external, "library")
        }

        return File(context.filesDir, "library")
    }

    @JvmStatic
    fun getAudioDir(accountId: String): File = File(getAccountDir(accountId), "audio")

    @JvmStatic
    fun getImageDir(accountId: String): File = File(getAccountDir(accountId), "images")

    @JvmStatic
    fun getTempDir(accountId: String): File = File(getAccountDir(accountId), "tmp")

    private fun getAccountDir(accountId: String): File = File(getRoot(), accountDirName(accountId))

    /** The local user id contains the server url, so it is hashed into a path-safe name. */
    @JvmStatic
    @VisibleForTesting
    fun accountDirName(accountId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(accountId.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(ACCOUNT_DIR_LENGTH)
    }

    @JvmStatic
    fun getCurrentAccountId(): String? = PreferenceUtil.getInstance(App.getInstance()).user

    @JvmStatic
    fun get(song: Song): LocalSong? {
        val accountId = getCurrentAccountId() ?: return null
        return dao.getSong(accountId, song.id)
    }

    /** Returns a playable stored file, marking the row missing if its file is gone, empty or unreadable. */
    @JvmStatic
    fun getFile(song: Song): File? = get(song)?.let { getFile(it) }

    private fun getFile(localSong: LocalSong): File? {
        if (localSong.state != LocalSong.State.COMPLETE) return null

        val file = playableLocalFile(localSong.filePath?.let(::File))
        if (file == null) {
            Log.w(TAG, "getFile: stored file for ${localSong.itemId} is unavailable")
            dao.changeState(localSong.accountId, localSong.itemId, LocalSong.State.COMPLETE, LocalSong.State.MISSING)
            localSong.state = LocalSong.State.MISSING
            return null
        }

        return file
    }

    /**
     * Loads a local-only summary for the supplied account, regardless of the selected account or
     * browsing library. Database reads and file checks always run on IO, including reconciliation.
     */
    suspend fun getSummary(accountId: String): LocalLibrarySummary = withContext(Dispatchers.IO) {
        verify(accountId)
        val rows = dao.getSongs(accountId, listOf(LocalSong.State.COMPLETE, LocalSong.State.MISSING))
        val context = App.getInstance()
        val internalRoot = context.filesDir
        val accountDirectory = accountDirName(accountId)
        val directories = buildList {
            add(File(internalRoot, "library/$accountDirectory/audio"))
            context.getExternalFilesDir(null)?.let { add(File(it, "library/$accountDirectory/audio")) }
            // getRoot() falls back to internal storage when external storage is unavailable.
            // Retain prior locations from saved paths so that fallback doesn't hide their usage.
            for (row in rows) {
                val directory = row.filePath?.let(::File)?.parentFile ?: continue
                if (directory.name == "audio" && directory.parentFile?.name == accountDirectory) {
                    add(directory)
                }
            }
        }
        LocalLibrarySummaryReader.read(accountId, rows, directories) { directory ->
            if (directory.absolutePath.startsWith(internalRoot.absolutePath + File.separator)) {
                true
            } else {
                val state = try {
                    Environment.getExternalStorageState(directory)
                } catch (_: IllegalArgumentException) {
                    Environment.MEDIA_UNKNOWN
                }
                state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY
            }
        }
    }

    /** Like [getFile] for many songs with one query per chunk, keyed by item id. Songs that are not stored are left out. */
    @JvmStatic
    fun getFiles(songs: List<Song>): Map<String, File> {
        val accountId = getCurrentAccountId() ?: return emptyMap()

        val files = HashMap<String, File>()
        // older sqlite versions allow at most 999 variables per statement
        for (itemIds in songs.map { it.id }.distinct().chunked(QUERY_CHUNK_SIZE)) {
            for (localSong in dao.getSongsByIds(accountId, itemIds)) {
                getFile(localSong)?.let { files[localSong.itemId] = it }
            }
        }

        return files
    }

    @JvmStatic
    fun isAvailable(song: Song): Boolean = getFile(song) != null

    /** Whether the song can play right now: stored songs always can, others only while streaming is allowed. */
    @JvmStatic
    fun isPlayable(song: Song): Boolean = NetworkUtil.canStream(App.getInstance()) || isAvailable(song)

    /** Matches [getFile], so the badge shown in lists agrees with what playback does. */
    @JvmStatic
    fun getAvailability(song: Song): Availability {
        val localSong = get(song)
        return when {
            localSong != null && getFile(localSong) != null -> Availability.STORED
            localSong?.state == LocalSong.State.MISSING -> Availability.MISSING
            else -> Availability.NONE
        }
    }

    /**
     * Queues songs for saving. Songs that are already queued, downloading or stored are left alone,
     * except that an explicit request takes over a song that was only queued by playback.
     */
    @JvmStatic
    fun save(songs: List<Song>, source: LocalSong.Source) {
        val accountId = getCurrentAccountId() ?: return

        Thread {
            try {
                var pending = false
                for (song in songs) {
                    if (enqueue(accountId, song, source)) pending = true
                }

                // playback saves every song it plays, most of which are stored already
                if (pending) SaveWorker.start(App.getInstance())
            } catch (err: Exception) {
                Log.w(TAG, "save: ${err.message}", err)
            }
        }.start()
    }

    /** Returns whether the song still has to be saved, i.e. it is not stored yet. */
    private fun enqueue(accountId: String, song: Song, source: LocalSong.Source): Boolean {
        val existing = dao.getSong(accountId, song.id)
        when (existing?.state) {
            null -> dao.insertSong(LocalSong(accountId, song, source))
            LocalSong.State.QUEUED, LocalSong.State.DOWNLOADING, LocalSong.State.COMPLETE -> {
                if (source == LocalSong.Source.EXPLICIT && existing.source == LocalSong.Source.PLAYED) {
                    existing.source = LocalSong.Source.EXPLICIT
                    dao.updateSong(existing)
                }
            }
            LocalSong.State.FAILED, LocalSong.State.MISSING -> {
                // start over from the server item, since the row may describe a transcode stored earlier
                val row = LocalSong(accountId, song, mergedSource(existing.source, source))
                row.addedAt = existing.addedAt
                dao.insertSong(row)
            }
        }

        return existing?.state != LocalSong.State.COMPLETE
    }

    /** Drops every song waiting to be saved and stops the save in progress. Call off the main thread. */
    @JvmStatic
    fun cancelSaves() {
        val accountId = getCurrentAccountId() ?: return

        // the worker discards a song whose row is gone, so deleting the rows first never leaves a song half saved
        dao.deleteSongs(accountId, listOf(LocalSong.State.QUEUED, LocalSong.State.DOWNLOADING))
        SaveWorker.cancel(App.getInstance())
    }

    /** Playback never takes over a song the user downloaded explicitly. */
    @JvmStatic
    @VisibleForTesting
    fun mergedSource(existing: LocalSong.Source, requested: LocalSong.Source): LocalSong.Source =
        if (existing == LocalSong.Source.EXPLICIT) existing else requested

    /**
     * Queues the song again if its stored file is gone, keeping why it was saved. Called when the song
     * is played, so a missing song comes back without waiting for the save trigger.
     */
    @JvmStatic
    fun restoreMissing(song: Song) {
        val accountId = getCurrentAccountId() ?: return

        Thread {
            try {
                val localSong = dao.getSong(accountId, song.id) ?: return@Thread

                // getFile marks a complete row whose file just disappeared as missing
                if (getFile(localSong) != null || localSong.state != LocalSong.State.MISSING) return@Thread

                enqueue(accountId, song, LocalSong.Source.PLAYED)
                SaveWorker.start(App.getInstance())
                notifyChanged()
            } catch (err: Exception) {
                Log.w(TAG, "restoreMissing: ${err.message}", err)
            }
        }.start()
    }

    /**
     * Removes the stored files and rows of the songs off the main thread, then calls back on the main
     * thread with the number of songs that were removed. Songs without a row are ignored, and queued
     * or failed songs are dropped from the save queue.
     */
    @JvmStatic
    fun delete(itemIds: List<String>, callback: DeleteCallback?) {
        val accountId = getCurrentAccountId() ?: return

        Thread {
            var removed = 0
            try {
                for (itemId in itemIds) {
                    if (delete(accountId, itemId)) removed++
                }
            } catch (err: Exception) {
                Log.w(TAG, "delete: ${err.message}", err)
            }

            if (removed > 0) notifyChanged()
            mainHandler.post { callback?.onDeleted(removed) }
        }.start()
    }

    fun interface DeleteCallback {
        fun onDeleted(count: Int)
    }

    /** Removes the stored file and the row together, and the album cover once no song of the album is left. */
    private fun delete(accountId: String, itemId: String): Boolean {
        val localSong = dao.getSong(accountId, itemId) ?: return false

        localSong.filePath?.let { File(it).delete() }
        File(getTempDir(accountId), "$itemId.part").delete()
        dao.deleteSong(accountId, itemId)

        val albumId = localSong.albumId
        if (albumId != null && dao.countAlbumSongs(accountId, albumId) == 0) {
            File(getImageDir(accountId), "$albumId.jpg").delete()
        }

        return true
    }

    /** Called when songs are stored, removed or found missing, so lists can refresh their badges. */
    fun interface Listener {
        fun onLibraryChanged()
    }

    private val listeners = CopyOnWriteArraySet<Listener>()

    private val mainHandler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    @JvmStatic
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /** Notifies listeners on the main thread, from any thread. */
    @JvmStatic
    fun notifyChanged() {
        mainHandler.post { listeners.forEach { it.onLibraryChanged() } }
    }

    /**
     * Reconciles stored rows with the files on disk for the current account. Returns the
     * number of rows whose state changed.
     */
    @JvmStatic
    @WorkerThread
    fun verify(): Int {
        val accountId = getCurrentAccountId() ?: return 0
        return verify(accountId)
    }

    private fun verify(accountId: String): Int {
        val rows = dao.getSongs(accountId, listOf(LocalSong.State.COMPLETE, LocalSong.State.MISSING))

        var changed = 0
        for (row in rows) {
            val state = verifiedState(row.state, playableLocalFile(row.filePath?.let(::File)) != null)
            if (state != row.state) {
                // Do not overwrite a row that was removed or re-queued during the file check.
                changed += dao.changeState(row.accountId, row.itemId, row.state, state)
            }
        }

        if (changed > 0) {
            Log.i(TAG, "verify: updated $changed of ${rows.size} rows")
            notifyChanged()
        }

        return changed
    }

    /** Runs the consistency check and restarts saving for rows left queued, e.g. by an account switch. */
    @JvmStatic
    fun verifyAsync() {
        Thread {
            try {
                verify()

                val accountId = getCurrentAccountId() ?: return@Thread
                if (dao.countSongs(accountId, listOf(LocalSong.State.QUEUED, LocalSong.State.DOWNLOADING)) > 0) {
                    SaveWorker.start(App.getInstance())
                }
            } catch (err: Exception) {
                Log.w(TAG, "verify: ${err.message}", err)
            }
        }.start()
    }

    /** Deletes folders left behind by retired storage (old downloads, the streaming cache), off the main thread. */
    @JvmStatic
    fun deleteLegacyFiles(dirs: List<File>) {
        Thread {
            for (dir in dirs) {
                if (dir.exists() && !dir.deleteRecursively()) {
                    Log.w(TAG, "deleteLegacyFiles: could not delete all of $dir")
                }
            }
        }.start()
    }

    /** A missing file marks a complete row missing, and a file that reappears (e.g. remounted storage) restores it. */
    @JvmStatic
    @VisibleForTesting
    fun verifiedState(state: LocalSong.State, fileExists: Boolean): LocalSong.State = when {
        state == LocalSong.State.COMPLETE && !fileExists -> LocalSong.State.MISSING
        state == LocalSong.State.MISSING && fileExists -> LocalSong.State.COMPLETE
        else -> state
    }
}
