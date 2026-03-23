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
    val albumName: String? = null,
    val albumImageUrl: String? = null,
    val artistProfileImageUrl: String? = null,
) {
    val formattedDuration: String
        get() {
            val totalSeconds = durationSeconds.toInt()
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}
