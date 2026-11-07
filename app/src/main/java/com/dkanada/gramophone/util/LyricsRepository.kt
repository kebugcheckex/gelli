package com.dkanada.gramophone.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import com.dkanada.gramophone.App
import com.dkanada.gramophone.model.Lyrics
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.lyricsApi
import org.jellyfin.sdk.model.api.LyricDto
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Loads a song's lyrics from the server through a file cache, one JSON file per account and item.
 *
 * Found lyrics show from the cache at once and refresh in the background after a week. A 404 is cached for a day,
 * so a lyric added on the server shows up later. Any other failure stores nothing, so a network error never looks
 * like a song with no lyrics and never replaces a good copy.
 */
object LyricsRepository {
    private const val TAG = "LyricsRepository"

    private const val TICKS_PER_MILLISECOND = 10_000L
    private const val HTTP_NOT_FOUND = 404

    private val REFRESH_AFTER = TimeUnit.DAYS.toMillis(7)
    private val MISSING_EXPIRES_AFTER = TimeUnit.DAYS.toMillis(1)

    private const val KEY_FETCHED_AT = "fetchedAt"
    private const val KEY_LINES = "lines"
    private const val KEY_TEXT = "text"
    private const val KEY_START = "start"
    private const val KEY_END = "end"
    private const val KEY_CUES = "cues"
    private const val KEY_POSITION = "position"
    private const val KEY_END_POSITION = "endPosition"

    private val mainHandler = Handler(Looper.getMainLooper())

    fun interface Callback {
        /** Called once on the main thread, with null when the song has no lyrics or they could not be loaded. */
        @MainThread
        fun onLyricsLoaded(itemId: String, lyrics: Lyrics?)
    }

    /** One cache file. Null lyrics record that the server had none. */
    @VisibleForTesting
    internal data class CacheEntry(val fetchedAt: Long, val lyrics: Lyrics?)

    @VisibleForTesting
    internal sealed interface FetchResult {
        data class Found(val lyrics: Lyrics) : FetchResult
        data object Missing : FetchResult
        data object Failed : FetchResult
    }

    @JvmStatic
    fun load(itemId: String, callback: Callback) {
        Thread {
            val file = LocalLibrary.getCurrentAccountId()?.let { File(LocalLibrary.getLyricsDir(it), "$itemId.json") }
            val cached = file?.let(::read)
            val now = System.currentTimeMillis()

            // a refresh that follows updates the file only, so a song already showing doesn't change under the user
            cached?.lyrics?.let { deliver(callback, itemId, it) }

            if (cached != null && isFresh(cached, now)) {
                if (cached.lyrics == null) deliver(callback, itemId, null)
                return@Thread
            }

            val result = fetch(itemId)
            if (file != null) entryFor(result, now)?.let { write(file, it) }
            if (cached?.lyrics == null) deliver(callback, itemId, (result as? FetchResult.Found)?.lyrics)
        }.start()
    }

    private fun deliver(callback: Callback, itemId: String, lyrics: Lyrics?) {
        mainHandler.post { callback.onLyricsLoaded(itemId, lyrics) }
    }

    private fun fetch(itemId: String): FetchResult {
        if (NetworkUtil.isOffline(App.getInstance())) return FetchResult.Failed
        val api = JellyfinSdkSession.createApiOrNull() ?: return FetchResult.Failed
        val uuid = QueryUtil.toUuidOrNull(itemId) ?: return FetchResult.Failed

        return try {
            val lyrics = toLyrics(runBlocking { api.lyricsApi.getLyrics(uuid) }.content)
            if (lyrics.lines.isEmpty()) FetchResult.Missing else FetchResult.Found(lyrics)
        } catch (err: InvalidStatusException) {
            if (err.status == HTTP_NOT_FOUND) {
                FetchResult.Missing
            } else {
                Log.w(TAG, "fetch: ${err.message}", err)
                FetchResult.Failed
            }
        } catch (err: Exception) {
            Log.w(TAG, "fetch: ${err.message}", err)
            FetchResult.Failed
        }
    }

    @VisibleForTesting
    internal fun toLyrics(dto: LyricDto): Lyrics = Lyrics(
        dto.lyrics.map { line ->
            Lyrics.Line(
                text = line.text,
                startMs = line.start?.div(TICKS_PER_MILLISECOND),
                cues = line.cues.orEmpty().map { cue ->
                    Lyrics.Cue(
                        position = cue.position,
                        endPosition = cue.endPosition,
                        startMs = cue.start / TICKS_PER_MILLISECOND,
                        endMs = cue.end?.div(TICKS_PER_MILLISECOND),
                    )
                },
            )
        }
    )

    @VisibleForTesting
    internal fun isFresh(entry: CacheEntry, now: Long): Boolean {
        val age = now - entry.fetchedAt
        return age >= 0 && age < if (entry.lyrics != null) REFRESH_AFTER else MISSING_EXPIRES_AFTER
    }

    /** What to store for a fetch, or null to leave the cache as it is. */
    @VisibleForTesting
    internal fun entryFor(result: FetchResult, now: Long): CacheEntry? = when (result) {
        is FetchResult.Found -> CacheEntry(now, result.lyrics)
        FetchResult.Missing -> CacheEntry(now, null)
        FetchResult.Failed -> null
    }

    private fun read(file: File): CacheEntry? {
        if (!file.exists()) return null

        return try {
            decode(file.readText()).also { if (it == null) Log.w(TAG, "read: ignoring unreadable $file") }
        } catch (err: IOException) {
            Log.w(TAG, "read: ${err.message}", err)
            null
        }
    }

    private fun write(file: File, entry: CacheEntry) {
        try {
            file.parentFile?.mkdirs()

            // a reader never sees half a file, and two loads of one song can overlap
            val temp = File.createTempFile(file.name, ".tmp", file.parentFile)
            temp.writeText(encode(entry))
            if (!temp.renameTo(file)) {
                temp.delete()
                Log.w(TAG, "write: could not replace $file")
            }
        } catch (err: IOException) {
            Log.w(TAG, "write: ${err.message}", err)
        }
    }

    @VisibleForTesting
    internal fun encode(entry: CacheEntry): String {
        val json = JSONObject().put(KEY_FETCHED_AT, entry.fetchedAt)
        val lyrics = entry.lyrics ?: return json.toString()

        val lines = JSONArray()
        for (line in lyrics.lines) {
            val lineJson = JSONObject().put(KEY_TEXT, line.text)
            line.startMs?.let { lineJson.put(KEY_START, it) }

            if (line.cues.isNotEmpty()) {
                val cues = JSONArray()
                for (cue in line.cues) {
                    val cueJson = JSONObject()
                        .put(KEY_POSITION, cue.position)
                        .put(KEY_END_POSITION, cue.endPosition)
                        .put(KEY_START, cue.startMs)
                    cue.endMs?.let { cueJson.put(KEY_END, it) }
                    cues.put(cueJson)
                }
                lineJson.put(KEY_CUES, cues)
            }

            lines.put(lineJson)
        }

        return json.put(KEY_LINES, lines).toString()
    }

    @VisibleForTesting
    internal fun decode(text: String): CacheEntry? = try {
        val json = JSONObject(text)
        val lines = json.optJSONArray(KEY_LINES)

        CacheEntry(json.getLong(KEY_FETCHED_AT), lines?.let { Lyrics(it.objects().map(::decodeLine)) })
    } catch (err: JSONException) {
        null
    }

    private fun decodeLine(json: JSONObject) = Lyrics.Line(
        text = json.getString(KEY_TEXT),
        startMs = json.longOrNull(KEY_START),
        cues = json.optJSONArray(KEY_CUES)?.objects()?.map { cue ->
            Lyrics.Cue(
                position = cue.getInt(KEY_POSITION),
                endPosition = cue.getInt(KEY_END_POSITION),
                startMs = cue.getLong(KEY_START),
                endMs = cue.longOrNull(KEY_END),
            )
        }.orEmpty(),
    )

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun JSONObject.longOrNull(key: String): Long? = if (has(key)) getLong(key) else null
}
