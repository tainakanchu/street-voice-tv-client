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
    val formattedDuration: String
        get() {
            val totalSeconds = durationSeconds.toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val formattedPlaysCount: String
        get() = when {
            playsCount >= 1_000_000 -> "%.1fM".format(playsCount / 1_000_000.0)
            playsCount >= 1_000 -> "%.1fK".format(playsCount / 1_000.0)
            else -> playsCount.toString()
        }
}
