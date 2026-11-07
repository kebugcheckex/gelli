package com.dkanada.gramophone.util

import com.dkanada.gramophone.model.Lyrics
import org.jellyfin.sdk.model.api.LyricDto
import org.jellyfin.sdk.model.api.LyricLine
import org.jellyfin.sdk.model.api.LyricLineCue
import org.jellyfin.sdk.model.api.LyricMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class LyricsRepositoryTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 100 * day
    private val lyrics = Lyrics(listOf(Lyrics.Line("Every night", 1000)))

    // --- toLyrics ---

    @Test
    fun toLyrics_convertsTicksToMilliseconds() {
        val result = LyricsRepository.toLyrics(
            LyricDto(
                metadata = LyricMetadata(),
                lyrics = listOf(
                    LyricLine(
                        text = "Every night",
                        start = 120_000_000,
                        cues = listOf(LyricLineCue(position = 0, endPosition = 5, start = 120_000_000, end = 125_000_000)),
                    ),
                ),
            )
        )

        assertEquals(12_000L, result.lines[0].startMs)
        assertEquals(Lyrics.Cue(0, 5, 12_000, 12_500), result.lines[0].cues[0])
    }

    @Test
    fun toLyrics_keepsUntimedLinesAndEmptyText() {
        val result = LyricsRepository.toLyrics(
            LyricDto(LyricMetadata(), listOf(LyricLine(text = "Plain"), LyricLine(text = "", start = 10_000)))
        )

        assertNull(result.lines[0].startMs)
        assertTrue(result.lines[0].cues.isEmpty())
        assertEquals("", result.lines[1].text)
        assertEquals(1L, result.lines[1].startMs)
    }

    // --- cache policy ---

    @Test
    fun isFresh_foundLyricsLastAWeek() {
        assertTrue(LyricsRepository.isFresh(LyricsRepository.CacheEntry(now - 7 * day + 1, lyrics), now))
        assertFalse(LyricsRepository.isFresh(LyricsRepository.CacheEntry(now - 7 * day, lyrics), now))
    }

    @Test
    fun isFresh_missingLyricsLastADay() {
        assertTrue(LyricsRepository.isFresh(LyricsRepository.CacheEntry(now - day + 1, null), now))
        assertFalse(LyricsRepository.isFresh(LyricsRepository.CacheEntry(now - day, null), now))
    }

    @Test
    fun isFresh_entryFromTheFutureIsNot() {
        assertFalse(LyricsRepository.isFresh(LyricsRepository.CacheEntry(now + 1, lyrics), now))
    }

    @Test
    fun entryFor_failureStoresNothing() {
        assertNull(LyricsRepository.entryFor(LyricsRepository.FetchResult.Failed, now))
    }

    @Test
    fun entryFor_missingStoresNegativeEntry() {
        assertEquals(LyricsRepository.CacheEntry(now, null), LyricsRepository.entryFor(LyricsRepository.FetchResult.Missing, now))
    }

    @Test
    fun entryFor_foundStoresLyrics() {
        assertEquals(LyricsRepository.CacheEntry(now, lyrics), LyricsRepository.entryFor(LyricsRepository.FetchResult.Found(lyrics), now))
    }

    // --- cache file format ---

    @Test
    fun encodeDecode_roundTripsEveryField() {
        val entry = LyricsRepository.CacheEntry(
            now,
            Lyrics(
                listOf(
                    Lyrics.Line("Every \"night\"\nline", 12_000, listOf(Lyrics.Cue(0, 5, 12_000, 12_500), Lyrics.Cue(6, 11, 12_500, null))),
                    Lyrics.Line("", 15_000),
                    Lyrics.Line("Plain", null),
                )
            ),
        )

        assertEquals(entry, LyricsRepository.decode(LyricsRepository.encode(entry)))
    }

    @Test
    fun encodeDecode_roundTripsNegativeEntry() {
        val entry = LyricsRepository.CacheEntry(now, null)

        assertEquals(entry, LyricsRepository.decode(LyricsRepository.encode(entry)))
    }

    @Test
    fun decode_unreadableFileIsNoEntry() {
        assertNull(LyricsRepository.decode("not json"))
        assertNull(LyricsRepository.decode("{}"))
        assertNull(LyricsRepository.decode("""{"fetchedAt":1,"lines":[{"start":1}]}"""))
    }
}
