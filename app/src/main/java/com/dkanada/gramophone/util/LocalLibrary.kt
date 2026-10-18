package com.dkanada.gramophone.util

import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import com.dkanada.gramophone.App
import com.dkanada.gramophone.database.LocalSong
import com.dkanada.gramophone.model.Song
import com.dkanada.gramophone.service.SaveWorker
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

    /** Returns the stored file if the song is available locally, marking the row missing if its file is gone. */
    @JvmStatic
    fun getFile(song: Song): File? = get(song)?.let { getFile(it) }

    private fun getFile(localSong: LocalSong): File? {
        if (localSong.state != LocalSong.State.COMPLETE) return null

        val file = localSong.filePath?.let { File(it) }
        if (file == null || !file.isFile) {
            Log.w(TAG, "getFile: stored file for ${localSong.itemId} is gone")
            dao.setState(localSong.accountId, localSong.itemId, LocalSong.State.MISSING)
            localSong.state = LocalSong.State.MISSING
            return null
        }

        return file
    }

    @JvmStatic
    fun isAvailable(song: Song): Boolean = getFile(song) != null

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
                for (song in songs) {
                    enqueue(accountId, song, source)
                }

                SaveWorker.start(App.getInstance())
            } catch (err: Exception) {
                Log.w(TAG, "save: ${err.message}", err)
            }
        }.start()
    }

    private fun enqueue(accountId: String, song: Song, source: LocalSong.Source) {
        val existing = dao.getSong(accountId, song.id)
        when (existing?.state) {
            LocalSong.State.QUEUED, LocalSong.State.DOWNLOADING, LocalSong.State.COMPLETE -> {
                if (source == LocalSong.Source.EXPLICIT && existing.source == LocalSong.Source.PLAYED) {
                    existing.source = LocalSong.Source.EXPLICIT
                    dao.updateSong(existing)
                }
            }
            else -> dao.insertSong(LocalSong(accountId, song, source))
        }
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
    fun verify(): Int {
        val accountId = getCurrentAccountId() ?: return 0
        val rows = dao.getSongs(accountId, listOf(LocalSong.State.COMPLETE, LocalSong.State.MISSING))

        var changed = 0
        for (row in rows) {
            val exists = row.filePath?.let { File(it).isFile } ?: false
            val state = verifiedState(row.state, exists)
            if (state != row.state) {
                dao.setState(row.accountId, row.itemId, state)
                changed++
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

    /** Deletes the folders the old download service saved songs to, off the main thread. */
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
