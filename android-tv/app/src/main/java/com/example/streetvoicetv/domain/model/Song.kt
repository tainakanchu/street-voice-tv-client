package com.example.streetvoicetv.domain.model

data class Song(
    val id: Int,
    val name: String,
    val artistName: String,
    val artistUsername: String,
    val imageUrl: String?,
    val durationSeconds: Double,
    val playsCount: Int,
    val genre: Int? = null,
    val synopsis: String? = null,
    val lyrics: String? = null,
    /**
     * Whether [lyrics] are synced LRC as the API reports it (`lyrics_is_lrc`); null when unknown
     * (list endpoints, or the field was missing). Detail responses only.
     */
    val lyricsIsLrc: Boolean? = null,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val shareCount: Int = 0,
    val publishAt: String? = null,
    val albumId: Int? = null,
    val albumName: String? = null,
    val albumImageUrl: String? = null,
    val artistProfileImageUrl: String? = null,
    val isLiked: Boolean = false,
) {
    // Computed once per instance (not on every recomposition). Plain String fields keep the
    // class stable for Compose; as body properties they are not part of equals/hashCode/copy.

    /** "m:ss", or "h:mm:ss" from one hour on. Negative / NaN durations show as 0:00. */
    val formattedDuration: String = formatDuration(durationSeconds)

    /** 999 / 1.2K / 3.4M, rolling over to the next unit when rounding reaches 1000. */
    val formattedPlaysCount: String = formatCount(playsCount)

    companion object {
        private val UNITS = arrayOf(1_000L to "K", 1_000_000L to "M", 1_000_000_000L to "B")

        /** Locale independent (plain string building, always ASCII digits and "."). */
        fun formatDuration(seconds: Double): String {
            val total = if (seconds.isNaN()) 0L else seconds.toLong().coerceAtLeast(0L)
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) "$h:${pad2(m)}:${pad2(s)}" else "$m:${pad2(s)}"
        }

        /** Locale independent (plain string building, always ASCII digits and "."). */
        fun formatCount(count: Int): String {
            val n = count.toLong().coerceAtLeast(0L)
            if (n < 1_000) return n.toString()
            for ((i, unit) in UNITS.withIndex()) {
                val (divisor, suffix) = unit
                // Round to tenths first so 999,950 becomes 1.0M rather than "1000.0K".
                val tenths = (n * 10 + divisor / 2) / divisor
                if (tenths < 10_000 || i == UNITS.lastIndex) {
                    return "${tenths / 10}.${tenths % 10}$suffix"
                }
            }
            error("unreachable")
        }

        private fun pad2(v: Long): String = if (v < 10) "0$v" else v.toString()
    }
}
