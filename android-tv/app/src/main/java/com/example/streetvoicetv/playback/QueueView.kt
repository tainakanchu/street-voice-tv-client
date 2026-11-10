package com.example.streetvoicetv.playback

import androidx.compose.runtime.Immutable
import com.example.streetvoicetv.domain.model.Song

/** One song of the play queue; [queueIndex] is its index in the queue as it was set (stable under shuffle). */
@Immutable
data class QueueItem(val queueIndex: Int, val song: Song)

/**
 * The play queue in the order it will actually be played (published by [PlaybackManager.upNext]).
 *
 * - [items] are in play order: with shuffle on, the shuffled order.
 * - [currentPosition] is the position of the current song in [items] (-1 when empty).
 *   Items before it have already been played; items after it are up next.
 * - When the queue wraps around (repeat ALL / ONE) without shuffle, [items] is rotated so the
 *   current song comes first and everything else follows in the order it will play again.
 *   With shuffle the remaining part of the shuffled order is shown; after it the queue is
 *   shuffled anew ([reshufflesAtEnd]).
 */
@Immutable
data class QueueView(
    val items: List<QueueItem> = emptyList(),
    val currentPosition: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val current: QueueItem? get() = items.getOrNull(currentPosition)

    /** The song "next" (manual skip / auto-advance unless repeat ONE) moves to, if known. */
    val next: QueueItem? get() = items.getOrNull(currentPosition + 1)

    /** Number of songs after the current one in [items]. */
    val upcomingCount: Int get() = if (currentPosition < 0) 0 else items.size - currentPosition - 1

    /** After the last item, playback starts over (repeat ALL / ONE skips). */
    val wrapsAround: Boolean get() = repeatMode != RepeatMode.OFF && items.isNotEmpty()

    /** After the last item a fresh shuffled order begins. */
    val reshufflesAtEnd: Boolean get() = wrapsAround && shuffleEnabled

    fun isPlayed(position: Int): Boolean = position < currentPosition
}
