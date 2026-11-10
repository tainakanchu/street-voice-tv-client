package com.example.streetvoicetv.playback

import com.example.streetvoicetv.domain.model.Song
import kotlin.random.Random

/**
 * Pure queue / shuffle / repeat logic (no Android, no player).
 *
 * Invariants: [currentIndex] is in [songs].indices (or -1 when empty); when shuffle is on,
 * [shuffleOrder] is a permutation of [songs].indices and `shuffleOrder[shufflePosition] ==
 * currentIndex`. Every queue replacement goes through [replace], which regenerates the order.
 *
 * Skipping manually with repeat ONE behaves like repeat ALL (wraps); replaying the same song
 * when it ends is handled by the player side.
 */
class PlaybackQueue(private val random: Random = Random.Default) {

    var songs: List<Song> = emptyList()
        private set
    var currentIndex: Int = -1
        private set
    var shuffleEnabled: Boolean = false
        private set
    var repeatMode: RepeatMode = RepeatMode.OFF

    private var shuffleOrder: List<Int> = emptyList()
    private var shufflePosition: Int = -1

    val current: Song? get() = songs.getOrNull(currentIndex)
    val size: Int get() = songs.size

    /** Read-only view of the shuffle order (for tests). */
    val order: List<Int> get() = shuffleOrder

    private val wraps: Boolean get() = repeatMode != RepeatMode.OFF

    /** Replaces the whole queue and makes [startIndex] current (coerced into range). */
    fun replace(newSongs: List<Song>, startIndex: Int) {
        songs = newSongs
        currentIndex = if (newSongs.isEmpty()) -1 else startIndex.coerceIn(newSongs.indices)
        regenerateShuffle()
    }

    fun clear() = replace(emptyList(), -1)

    /** Replaces the current song object (e.g. with full details) if it has the same id. */
    fun updateCurrent(song: Song): Boolean {
        val index = currentIndex
        if (index !in songs.indices || songs[index].id != song.id) return false
        songs = songs.toMutableList().also { it[index] = song }
        return true
    }

    fun setShuffle(enabled: Boolean) {
        shuffleEnabled = enabled
        regenerateShuffle()
    }

    val hasNext: Boolean
        get() = songs.isNotEmpty() && (wraps || if (shuffleEnabled) {
            shufflePosition < shuffleOrder.size - 1
        } else {
            currentIndex < songs.size - 1
        })

    val hasPrevious: Boolean
        get() = songs.isNotEmpty() && (wraps || if (shuffleEnabled) {
            shufflePosition > 0
        } else {
            currentIndex > 0
        })

    /** Moves to the next song. Returns false (and changes nothing) when there is none. */
    fun moveToNext(): Boolean {
        if (!hasNext) return false
        if (shuffleEnabled) {
            if (shufflePosition + 1 < shuffleOrder.size) {
                shufflePosition++
            } else {
                // Wrap: a fresh order that does not start with the song that just played.
                shuffleOrder = shuffledIndices(avoidFirst = currentIndex)
                shufflePosition = 0
            }
            currentIndex = shuffleOrder[shufflePosition]
        } else {
            currentIndex = if (currentIndex + 1 < songs.size) currentIndex + 1 else 0
        }
        return true
    }

    /** Moves to the previous song. Returns false (and changes nothing) when there is none. */
    fun moveToPrevious(): Boolean {
        if (!hasPrevious) return false
        if (shuffleEnabled) {
            shufflePosition = if (shufflePosition > 0) shufflePosition - 1 else shuffleOrder.size - 1
            currentIndex = shuffleOrder[shufflePosition]
        } else {
            currentIndex = if (currentIndex > 0) currentIndex - 1 else songs.size - 1
        }
        return true
    }

    private fun regenerateShuffle() {
        if (!shuffleEnabled || currentIndex !in songs.indices) {
            shuffleOrder = emptyList()
            shufflePosition = -1
            return
        }
        val rest = songs.indices.filter { it != currentIndex }.shuffled(random)
        shuffleOrder = listOf(currentIndex) + rest
        shufflePosition = 0
    }

    private fun shuffledIndices(avoidFirst: Int): List<Int> {
        val order = songs.indices.shuffled(random).toMutableList()
        if (order.size > 1 && order[0] == avoidFirst) {
            val swapWith = 1 + random.nextInt(order.size - 1)
            order[0] = order[swapWith].also { order[swapWith] = order[0] }
        }
        return order
    }
}
