package com.dkanada.gramophone.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.widget.Toast
import com.dkanada.gramophone.App
import com.dkanada.gramophone.R
import com.dkanada.gramophone.database.Download
import com.dkanada.gramophone.model.Song
import java.io.File

object DownloadUtil {
    // actions that need the server and are hidden while offline
    private val ONLINE_ACTIONS = intArrayOf(
        R.id.action_add_to_playlist,
        R.id.action_go_to_album,
        R.id.action_go_to_artist,
        R.id.action_download,
        R.id.action_toggle_favorite,
        R.id.action_save_queue,
    )

    @JvmStatic
    fun isOfflineMode(): Boolean = PreferenceUtil.getInstance(App.getInstance()).offlineMode

    private fun currentUserId(): String? = PreferenceUtil.getInstance(App.getInstance()).user

    // database only, cheap enough for list badges
    @JvmStatic
    fun isDownloaded(song: Song): Boolean {
        val userId = currentUserId() ?: return false
        return App.getDatabase().downloadDao().isDownloaded(song.id, userId)
    }

    // the downloaded file for the current account, or null when it is missing or incomplete
    @JvmStatic
    fun getLocalFile(song: Song): File? {
        val userId = currentUserId() ?: return null
        val download = App.getDatabase().downloadDao().getDownload(song.id, userId) ?: return null
        return verify(download)
    }

    @JvmStatic
    fun isPlayableOffline(song: Song): Boolean = getLocalFile(song) != null

    // downloaded songs for the current account whose files are still present
    @JvmStatic
    fun getDownloadedSongs(): List<Song> {
        val userId = currentUserId() ?: return emptyList()
        return App.getDatabase().downloadDao().getDownloads(userId)
            .filter { verify(it) != null }
            .map { it.song }
    }

    @JvmStatic
    fun recordDownload(song: Song, userId: String, file: File) {
        App.getDatabase().downloadDao().insertDownload(Download(song, userId, file))
    }

    @JvmStatic
    fun hideOnlineActions(menu: Menu) {
        if (!isOfflineMode()) return
        for (id in ONLINE_ACTIONS) {
            menu.findItem(id)?.isVisible = false
        }
    }

    @JvmStatic
    fun showUnavailableToast(context: Context, count: Int) {
        if (count <= 0) return
        val message = context.getString(R.string.x_songs_unavailable_offline, count)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun verify(download: Download): File? {
        val path = download.filePath ?: return null
        val file = File(path)
        return if (file.isFile && file.length() == download.fileSize && download.fileSize > 0) file else null
    }
}
