package com.example.streetvoicetv.ui.player

import androidx.compose.runtime.Immutable

/** One timed lyric line; [text] may be empty (an instrumental gap). */
@Immutable
data class LrcLine(val timeMs: Long, val text: String)

/** Parsed LRC lyrics: lines sorted by time. */
@Immutable
data class SyncedLyrics(val lines: List<LrcLine>) {

    /** Index of the line being sung at [positionMs] (the last one that started), or -1 before the first. */
    fun indexAt(positionMs: Long): Int {
        var low = 0
        var high = lines.size - 1
        var found = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return found
    }
}

/**
 * LRC parser: `[mm:ss.xx]text` lines, several time tags per line (`[00:12.00][01:30.5]chorus`),
 * fractions of 1-3 digits, an `[offset:+/-ms]` tag (positive = lyrics shown earlier) and
 * metadata tags (`[ar:...]`, ignored). Lines without a time tag and malformed tags are skipped;
 * the result is sorted by time (stable for equal times).
 */
object LrcParser {

    private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET_TAG = Regex("""^\s*\[offset:\s*([+-]?\d+)\s*]\s*$""", RegexOption.IGNORE_CASE)
    private val META_TAG = Regex("""^\s*\[[a-zA-Z#]+:[^\]]*]\s*$""")

    /**
     * Parses [text] as LRC. Returns null when it does not look like synced lyrics (fewer than
     * [MIN_TIMED_LINES] timed lines, or most lines are untimed): show it as plain text then.
     */
    fun parse(text: String?): SyncedLyrics? {
        if (text.isNullOrBlank() || '[' !in text) return null
        var offsetMs = 0L
        val timed = mutableListOf<LrcLine>()
        var untimed = 0
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            OFFSET_TAG.matchEntire(line)?.let { m ->
                offsetMs = m.groupValues[1].toLongOrNull() ?: 0L
                continue
            }
            // Leading run of time tags; the rest is the text.
            var pos = 0
            val times = mutableListOf<Long>()
            while (true) {
                val m = TIME_TAG.matchAt(line, pos) ?: break
                times += toMillis(m) ?: break
                pos = m.range.last + 1
            }
            if (times.isEmpty()) {
                if (!META_TAG.matches(line)) untimed++
                continue
            }
            val lyric = line.substring(pos).trim()
            times.forEach { timed += LrcLine(it, lyric) }
        }
        if (timed.size < MIN_TIMED_LINES || untimed > timed.size) return null
        val lines = timed
            .map { it.copy(timeMs = (it.timeMs - offsetMs).coerceAtLeast(0)) }
            .sortedBy { it.timeMs }
        return SyncedLyrics(lines)
    }

    private fun toMillis(m: MatchResult): Long? {
        val minutes = m.groupValues[1].toLongOrNull() ?: return null
        val seconds = m.groupValues[2].toLongOrNull() ?: return null
        if (seconds >= 60) return null
        val fraction = m.groupValues[3]
        val fractionMs = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }
        return (minutes * 60 + seconds) * 1000 + fractionMs
    }

    /** Fewer timed lines than this is not treated as synced lyrics. */
    const val MIN_TIMED_LINES = 2
}
