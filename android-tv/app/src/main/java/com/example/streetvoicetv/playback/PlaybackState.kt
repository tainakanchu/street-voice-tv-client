package com.example.streetvoicetv.playback

import com.example.streetvoicetv.domain.model.Song

enum class RepeatMode { OFF, ALL, ONE }

/**
 * App-facing playback state published by [PlaybackManager.state].
 *
 * This flow changes only on discrete events (song change, play / pause, error, seek, queue or
 * mode change) so that everything collecting it does not recompose every progress tick.
 * [positionMs] is a snapshot taken at those events; the live position is
 * [PlaybackManager.positionMs], which only progress UI should collect.
 *
 * [hasNext] / [hasPrevious] come from [PlaybackManager]'s real queue logic ([PlaybackQueue]) via
 * [nextAvailable] / [previousAvailable]; without them they are approximated (previews / tests).
 */
data class PlaybackState(
    val song: Song? = null,
    /** Playback is wanted (play-when-ready, no error, not ended): show the pause button. */
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    /** User-facing (繁中) error message; play / retry re-fetches the stream and resumes. */
    val error: String? = null,
    val queueSize: Int = 0,
    val queueIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** The stream URL is being fetched or the player is buffering. */
    val isLoading: Boolean = false,
    /** Exact next / previous availability from [PlaybackQueue]; null = approximate from the queue fields. */
    val nextAvailable: Boolean? = null,
    val previousAvailable: Boolean? = null,
) {
    val hasMedia: Boolean get() = song != null
    val hasNext: Boolean
        get() = nextAvailable ?: if (repeatMode != RepeatMode.OFF) queueSize > 0 else queueIndex < queueSize - 1
    val hasPrevious: Boolean
        get() = previousAvailable ?: if (repeatMode != RepeatMode.OFF) queueSize > 0 else queueIndex > 0
    val progress: Float get() = progressOf(positionMs, durationMs)

    val formattedPosition: String get() = formatMs(positionMs)
    val formattedDuration: String get() = formatMs(durationMs)
}

/** The slice of [PlaybackState] the app chrome (NowPlayingBar) needs. */
data class NowPlaying(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val error: String? = null,
)

fun progressOf(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f

fun formatMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
