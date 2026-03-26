package com.example.streetvoicetv.playback

import com.example.streetvoicetv.domain.model.Song

enum class RepeatMode { OFF, ALL, ONE }

data class PlaybackState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val error: String? = null,
    val queueSize: Int = 0,
    val queueIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
) {
    val hasMedia: Boolean get() = song != null
    val hasNext: Boolean get() = when {
        repeatMode != RepeatMode.OFF -> queueSize > 0
        shuffleEnabled -> queueSize > 1
        else -> queueIndex < queueSize - 1
    }
    val hasPrevious: Boolean get() = when {
        repeatMode != RepeatMode.OFF -> queueSize > 0
        shuffleEnabled -> queueSize > 1
        else -> queueIndex > 0
    }
    val progress: Float
        get() = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f

    val formattedPosition: String get() = formatMs(positionMs)
    val formattedDuration: String get() = formatMs(durationMs)
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
