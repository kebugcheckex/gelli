package com.dkanada.gramophone.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.dkanada.gramophone.App
import com.dkanada.gramophone.R
import com.dkanada.gramophone.activities.MainActivity
import com.dkanada.gramophone.database.LocalSong
import com.dkanada.gramophone.glide.JellyfinImageUrls
import com.dkanada.gramophone.util.LocalLibrary
import com.dkanada.gramophone.util.MusicUtil
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Drains the save queue for the current account, one song at a time. The local_songs rows are the
 * queue, so this worker only has to be started; it picks up whatever is QUEUED, oldest first.
 *
 * Files are written to tmp/ and renamed into audio/ once complete, so audio/ never holds a partial file.
 */
class SaveWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private class HttpException(val code: Int) : IOException("HTTP $code")

    private class StoppedException : IOException("stopped")

    private val dao = App.getDatabase().localSongDao()
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var lastNotified = 0L

    override fun doWork(): Result {
        val accountId = LocalLibrary.getCurrentAccountId() ?: return Result.success()

        // saves run one at a time, so a song still marked downloading was interrupted
        dao.replaceState(accountId, LocalSong.State.DOWNLOADING, LocalSong.State.QUEUED)
        if (dao.getNextSong(accountId, LocalSong.State.QUEUED) == null) return Result.success()

        createNotificationChannel()

        try {
            setForegroundAsync(createForegroundInfo(createNotification(null, 0, 0, 0))).get()
        } catch (err: Exception) {
            // newer versions refuse to start a foreground service while the app is in the background
            Log.w(TAG, "doWork: continuing without a foreground service", err)
        }

        try {
            return drain(accountId)
        } finally {
            notificationManager.cancel(NOTIFICATION_ID)
        }
    }

    private fun drain(accountId: String): Result {
        // stop when the account changes, its rows stay queued until it is selected again
        while (!isStopped && LocalLibrary.getCurrentAccountId() == accountId) {
            val song = dao.getNextSong(accountId, LocalSong.State.QUEUED) ?: return Result.success()
            val remaining = dao.countSongs(accountId, listOf(LocalSong.State.QUEUED))

            dao.setState(accountId, song.itemId, LocalSong.State.DOWNLOADING)
            notify(song, remaining, 0, 0, true)

            try {
                saveAudio(song, remaining)
                saveImage(song)
            } catch (err: StoppedException) {
                break
            } catch (err: Exception) {
                if (fail(song, err)) return Result.retry()
            }
        }

        if (isStopped && stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP) {
            // cancelled from the notification, so drop everything that was waiting
            dao.deleteSongs(accountId, listOf(LocalSong.State.QUEUED, LocalSong.State.DOWNLOADING))
        } else {
            dao.replaceState(accountId, LocalSong.State.DOWNLOADING, LocalSong.State.QUEUED)
        }

        return Result.success()
    }

    /** Records the failure and returns whether the whole queue should back off and retry later. */
    private fun fail(song: LocalSong, err: Exception): Boolean {
        Log.w(TAG, "save ${song.itemId}: ${err.message}", err)

        song.failureCount++
        song.lastError = err.message ?: err.javaClass.simpleName

        val retry = isRetryable(err) && song.failureCount < MAX_ATTEMPTS
        song.state = if (retry) LocalSong.State.QUEUED else LocalSong.State.FAILED
        dao.updateSong(song)

        return retry
    }

    private fun saveAudio(song: LocalSong, remaining: Int) {
        val temp = File(LocalLibrary.getTempDir(song.accountId), "${song.itemId}.part")
        val audio = File(LocalLibrary.getAudioDir(song.accountId), "${song.itemId}.${fileExtension(song.container)}")

        temp.parentFile?.mkdirs()
        audio.parentFile?.mkdirs()

        try {
            download(MusicUtil.getDownloadUri(song.itemId), temp) { received, total ->
                notify(song, remaining, received, total, false)
            }

            if (!temp.renameTo(audio)) throw IOException("could not move ${temp.name} into the library")
        } finally {
            temp.delete()
        }

        song.state = LocalSong.State.COMPLETE
        song.variant = LocalSong.Variant.ORIGINAL
        song.filePath = audio.absolutePath
        song.fileSize = audio.length()
        song.failureCount = 0
        song.lastError = null

        // the row is gone if the queue was cancelled while this song was finishing
        if (dao.updateSong(song) == 0) audio.delete()
    }

    /** Cover art is stored once per album. A failure here does not fail the song. */
    private fun saveImage(song: LocalSong) {
        val albumId = song.albumId ?: return
        val imageId = song.imageTag ?: return

        val image = File(LocalLibrary.getImageDir(song.accountId), "$albumId.jpg")
        if (image.isFile) return

        val temp = File(LocalLibrary.getTempDir(song.accountId), "$albumId.jpg.part")
        image.parentFile?.mkdirs()

        try {
            download(JellyfinImageUrls.buildPrimaryImageUrl(imageId), temp) { _, _ -> }
            if (!temp.renameTo(image)) throw IOException("could not move ${temp.name} into the library")
        } catch (err: StoppedException) {
            throw err
        } catch (err: Exception) {
            Log.w(TAG, "saveImage $albumId: ${err.message}")
        } finally {
            temp.delete()
        }
    }

    private fun download(url: String, file: File, onProgress: (Long, Long) -> Unit) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MS
        connection.readTimeout = TIMEOUT_MS

        try {
            val code = connection.responseCode
            if (code !in 200..299) throw HttpException(code)

            // getContentLengthLong needs api 24
            val total = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            var received = 0L

            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (isStopped) throw StoppedException()

                        val count = input.read(buffer)
                        if (count == -1) break

                        output.write(buffer, 0, count)
                        received += count
                        onProgress(received, total)
                    }

                    output.fd.sync()
                }
            }

            if (total >= 0 && received != total) throw IOException("expected $total bytes but received $received")
        } finally {
            connection.disconnect()
        }
    }

    private fun notify(song: LocalSong, remaining: Int, received: Long, total: Long, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastNotified < NOTIFY_INTERVAL_MS) return

        lastNotified = now
        notificationManager.notify(NOTIFICATION_ID, createNotification(song, remaining, received, total))
    }

    private fun createNotification(song: LocalSong?, remaining: Int, received: Long, total: Long): Notification {
        val context = applicationContext

        val action = Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val clickIntent = PendingIntent.getActivity(context, 0, action, PendingIntent.FLAG_IMMUTABLE)
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(clickIntent)
            .setContentTitle(context.getString(R.string.downloading_x_songs, remaining))
            .setContentText(song?.title)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_close_white_24dp, context.getString(R.string.action_cancel), cancelIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        if (total > 0) {
            builder.setProgress(PROGRESS_MAX, (received * PROGRESS_MAX / total).toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun createForegroundInfo(notification: Notification): ForegroundInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.action_download), NotificationManager.IMPORTANCE_LOW)
        channel.enableLights(false)
        channel.enableVibration(false)

        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "SaveWorker"
        private const val WORK_NAME = "library_save"
        private const val CHANNEL_ID = "library_save"
        private const val NOTIFICATION_ID = 2

        private const val MAX_ATTEMPTS = 3
        private const val TIMEOUT_MS = 30_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val NOTIFY_INTERVAL_MS = 500L
        private const val PROGRESS_MAX = 1000

        /**
         * Makes sure the queue is being drained. A worker that is already running finishes first, and the
         * appended one picks up anything it missed.
         */
        @JvmStatic
        fun start(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequest.Builder(SaveWorker::class.java)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /** Server and network errors are retried, a client error such as a removed item is not. */
        @JvmStatic
        @VisibleForTesting
        fun isRetryable(code: Int): Boolean = code == 408 || code == 429 || code >= 500

        private fun isRetryable(err: Exception): Boolean = when (err) {
            is HttpException -> isRetryable(err.code)
            is IOException -> true
            else -> false
        }

        /** Jellyfin reports some containers as a list such as "mov,mp4,m4a", so only the first entry is used. */
        @JvmStatic
        @VisibleForTesting
        fun fileExtension(container: String?): String =
            container?.substringBefore(',')?.trim()?.lowercase()?.takeIf { it.matches(Regex("[a-z0-9]+")) } ?: "bin"
    }
}
