package com.dkanada.gramophone.model

/**
 * The app's own form of a song's lyrics, independent of the server's DTO. All times are in milliseconds.
 * See docs/live_lyrics_plan.md.
 */
data class Lyrics(val lines: List<Line>) {
    /** A timed line with no text is an instrumental break, and is kept so the highlight can clear. */
    data class Line(val text: String, val startMs: Long?, val cues: List<Cue> = emptyList())

    /** Timing for the characters from [position] up to [endPosition] of the line's text. */
    data class Cue(val position: Int, val endPosition: Int, val startMs: Long, val endMs: Long?)

    val isSynced: Boolean = lines.isNotEmpty() && lines.all { it.startMs != null }

    /** The index of the last line that has started by [positionMs], or -1 before the first line and when unsynced. */
    fun lineAt(positionMs: Long): Int {
        if (!isSynced) return -1

        var low = 0
        var high = lines.size - 1
        var found = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (lines[middle].startMs!! <= positionMs) {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }

        return found
    }
}
