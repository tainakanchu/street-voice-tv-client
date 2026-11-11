package com.example.streetvoicetv.playback

import com.example.streetvoicetv.domain.model.Song
import kotlin.random.Random

/**
 * Pure queue / shuffle / repeat logic (no Android, no player).
 *
 * Invariants: [currentIndex] is in [songs].indices (or -1 when empty); when shuffle is on,
 * [shuffleOrder] is a permutation of [songs].indices and `shuffleOrder[shufflePosition] ==
 * currentIndex`. [autoplayFlags] has one entry per song (true = appended by 自動播放).
 *
 * Skipping manually with repeat ONE behaves like repeat ALL (wraps); replaying the same song
 * when it ends is handled by the player side.
 *
 * Edits ([insertNext], [insertIntoQueue], [remove], [appendAutoplay]) keep the shuffled order:
 * indices are remapped, and new songs are placed at the matching position of the play order.
 */
class PlaybackQueue(private val random: Random = Random.Default) {

    var songs: List<Song> = emptyList()
        private set
    var currentIndex: Int = -1
        private set
    var shuffleEnabled: Boolean = false
        private set
    var repeatMode: RepeatMode = RepeatMode.OFF
        set(value) {
            if (field != value) revision++
            field = value
        }

    /** Incremented on every change that can change [view] (cheap change detection for publishers). */
    var revision: Int = 0
        private set
    private var shuffleOrder: List<Int> = emptyList()
    private var shufflePosition: Int = -1
    private var autoplayFlags: List<Boolean> = emptyList()

    val current: Song? get() = songs.getOrNull(currentIndex)
    val size: Int get() = songs.size

    /** Read-only view of the shuffle order (for tests). */
    val order: List<Int> get() = shuffleOrder

    /** Whether the song at [index] was appended by autoplay. */
    fun isAutoplay(index: Int): Boolean = autoplayFlags.getOrElse(index) { false }

    private val wraps: Boolean get() = repeatMode != RepeatMode.OFF

    /** Indices of [songs] in play order (ignoring wrap-around) and the current position in it. */
    private fun playOrder(): Pair<List<Int>, Int> =
        if (shuffleEnabled) shuffleOrder to shufflePosition else songs.indices.toList() to currentIndex

    /** Songs after the current one in play order (before any wrap-around). */
    val upcomingCount: Int
        get() = when {
            currentIndex !in songs.indices -> 0
            shuffleEnabled -> shuffleOrder.size - 1 - shufflePosition
            else -> songs.size - 1 - currentIndex
        }

    /** Replaces the whole queue and makes [startIndex] current (coerced into range). */
    fun replace(newSongs: List<Song>, startIndex: Int) {
        songs = newSongs
        autoplayFlags = List(newSongs.size) { false }
        currentIndex = if (newSongs.isEmpty()) -1 else startIndex.coerceIn(newSongs.indices)
        regenerateShuffle()
        revision++
    }

    fun clear() = replace(emptyList(), -1)

    /** Replaces the current song object (e.g. with full details) if it has the same id. */
    fun updateCurrent(song: Song): Boolean {
        val index = currentIndex
        if (index !in songs.indices || songs[index].id != song.id) return false
        songs = songs.toMutableList().also { it[index] = song }
        revision++
        return true
    }

    fun setShuffle(enabled: Boolean) {
        shuffleEnabled = enabled
        regenerateShuffle()
        revision++
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
        revision++
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
        revision++
        return true
    }

    /**
     * Makes [index] (an index into [songs]) current without rebuilding the queue: with shuffle
     * the shuffled order is kept and the position moves to that song (songs between the old
     * and the new position are skipped / become upcoming again). Returns false when out of range.
     */
    fun jumpTo(index: Int): Boolean {
        if (index !in songs.indices) return false
        if (shuffleEnabled) {
            val position = shuffleOrder.indexOf(index)
            if (position < 0) return false
            shufflePosition = position
        }
        currentIndex = index
        revision++
        return true
    }

    // ---------------------------------------------------------------------------------------
    // Edits
    // ---------------------------------------------------------------------------------------

    /**
     * Inserts [song] so it plays right after the current song (in play order, shuffle included).
     * On an empty queue it becomes the only (current) song. Returns its index in [songs].
     */
    fun insertNext(song: Song): Int {
        if (currentIndex !in songs.indices) {
            replace(listOf(song), 0)
            return 0
        }
        val (_, position) = playOrder()
        return insert(song, songIndex = currentIndex + 1, orderPosition = position + 1, autoplay = false)
    }

    /**
     * Appends [song] to the end of the user's part of the queue: after the last upcoming song
     * the user picked, before any songs added by autoplay. Returns its index in [songs].
     */
    fun insertIntoQueue(song: Song): Int {
        if (currentIndex !in songs.indices) {
            replace(listOf(song), 0)
            return 0
        }
        val (order, position) = playOrder()
        // First upcoming autoplay song in play order (the new song goes before it).
        val orderPosition = (position + 1 until order.size).firstOrNull { isAutoplay(order[it]) } ?: order.size
        // In the list itself: before the first autoplay song after the current one (or at the end).
        val songIndex = if (shuffleEnabled) {
            (currentIndex + 1 until songs.size).firstOrNull { isAutoplay(it) } ?: songs.size
        } else {
            orderPosition
        }
        return insert(song, songIndex, orderPosition, autoplay = false)
    }

    /** Appends songs found by autoplay at the very end of the play order. */
    fun appendAutoplay(newSongs: List<Song>) {
        if (newSongs.isEmpty() || currentIndex !in songs.indices) return
        val first = songs.size
        songs = songs + newSongs
        autoplayFlags = autoplayFlags + List(newSongs.size) { true }
        if (shuffleEnabled) shuffleOrder = shuffleOrder + (first until songs.size)
        revision++
    }

    /** Removes the autoplay songs that have not been played yet (after the current one). */
    fun dropUpcomingAutoplay(): Boolean {
        val (order, position) = playOrder()
        if (position < 0) return false
        val doomed = (position + 1 until order.size).map { order[it] }.filter { isAutoplay(it) }
        if (doomed.isEmpty()) return false
        doomed.sortedDescending().forEach { removeNonCurrent(it) }
        revision++
        return true
    }

    /** Result of [remove]. */
    enum class Removal {
        /** Nothing removed (index out of range). */
        NONE,

        /** A song other than the current one was removed. */
        REMOVED,

        /** The current song was removed and the next one became current. */
        CURRENT_SKIPPED,

        /** The current song was removed; nothing followed, so the one before it became current. */
        CURRENT_REMOVED_AT_END,

        /** The only song was removed: the queue is empty now. */
        EMPTIED,
    }

    /**
     * Removes the song at [index] (an index into [songs]). Removing the current song skips to
     * the next one (in play order); when nothing follows, the previous song becomes current.
     */
    fun remove(index: Int): Removal {
        if (index !in songs.indices) return Removal.NONE
        if (songs.size == 1) {
            clear()
            return Removal.EMPTIED
        }
        if (index != currentIndex) {
            removeNonCurrent(index)
            revision++
            return Removal.REMOVED
        }
        val result = if (moveToNext() && currentIndex != index) {
            Removal.CURRENT_SKIPPED
        } else {
            // Last in play order (repeat off): step back instead.
            val (order, position) = playOrder()
            val previous = order.getOrNull(position - 1) ?: order.first { it != index }
            jumpTo(previous)
            Removal.CURRENT_REMOVED_AT_END
        }
        removeNonCurrent(index)
        revision++
        return result
    }

    private fun insert(song: Song, songIndex: Int, orderPosition: Int, autoplay: Boolean): Int {
        songs = songs.toMutableList().also { it.add(songIndex, song) }
        autoplayFlags = autoplayFlags.toMutableList().also { it.add(songIndex, autoplay) }
        if (currentIndex >= songIndex) currentIndex++
        if (shuffleEnabled) {
            val remapped = shuffleOrder.map { if (it >= songIndex) it + 1 else it }.toMutableList()
            remapped.add(orderPosition.coerceIn(shufflePosition + 1, remapped.size), songIndex)
            shuffleOrder = remapped
        }
        revision++
        return songIndex
    }

    private fun removeNonCurrent(index: Int) {
        songs = songs.toMutableList().also { it.removeAt(index) }
        autoplayFlags = autoplayFlags.toMutableList().also { it.removeAt(index) }
        if (currentIndex > index) currentIndex--
        if (shuffleEnabled) {
            val position = shuffleOrder.indexOf(index)
            if (position in 0 until shufflePosition) shufflePosition--
            shuffleOrder = shuffleOrder.filter { it != index }.map { if (it > index) it - 1 else it }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Snapshot (session restore)
    // ---------------------------------------------------------------------------------------

    /** Everything needed to rebuild this queue later ([restore]); null when empty. */
    fun snapshot(): QueueSnapshot? {
        if (currentIndex !in songs.indices) return null
        return QueueSnapshot(
            songs = songs,
            currentIndex = currentIndex,
            shuffleEnabled = shuffleEnabled,
            shuffleOrder = shuffleOrder,
            shufflePosition = shufflePosition,
            autoplayFlags = autoplayFlags,
        )
    }

    /** Rebuilds a queue from [snapshot]; an inconsistent shuffle order is regenerated. */
    fun restore(snapshot: QueueSnapshot) {
        songs = snapshot.songs
        currentIndex = if (songs.isEmpty()) -1 else snapshot.currentIndex.coerceIn(songs.indices)
        autoplayFlags = snapshot.autoplayFlags.takeIf { it.size == songs.size } ?: List(songs.size) { false }
        shuffleEnabled = snapshot.shuffleEnabled
        val order = snapshot.shuffleOrder
        val valid = shuffleEnabled && order.size == songs.size && order.sorted() == songs.indices.toList() &&
            order.getOrNull(snapshot.shufflePosition) == currentIndex
        if (valid) {
            shuffleOrder = order
            shufflePosition = snapshot.shufflePosition
        } else {
            regenerateShuffle()
        }
        revision++
    }

    /** The queue in play order (see [QueueView]). */
    fun view(autoplayEnabled: Boolean = false): QueueView {
        if (currentIndex !in songs.indices) {
            return QueueView(shuffleEnabled = shuffleEnabled, repeatMode = repeatMode, autoplayEnabled = autoplayEnabled)
        }
        val (order, position) = when {
            shuffleEnabled -> shuffleOrder to shufflePosition
            // Wrapping: what was played before comes again after the end, so list it there.
            wraps -> (songs.indices.drop(currentIndex) + songs.indices.take(currentIndex)) to 0
            else -> songs.indices.toList() to currentIndex
        }
        return QueueView(
            items = order.map { QueueItem(it, songs[it], isAutoplay = isAutoplay(it)) },
            currentPosition = position,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            autoplayEnabled = autoplayEnabled,
        )
    }

    private fun regenerateShuffle() {
        if (!shuffleEnabled || currentIndex !in songs.indices) {
            shuffleOrder = emptyList()
            shufflePosition = -1
            return
        }
        val others = songs.indices.filter { it != currentIndex }
        // The user's songs are shuffled; autoplay songs stay at the end, in their order.
        val rest = others.filter { !isAutoplay(it) }.shuffled(random) + others.filter { isAutoplay(it) }
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

/** A [PlaybackQueue]'s state, for persisting / restoring a session. */
data class QueueSnapshot(
    val songs: List<Song>,
    val currentIndex: Int,
    val shuffleEnabled: Boolean,
    val shuffleOrder: List<Int>,
    val shufflePosition: Int,
    val autoplayFlags: List<Boolean>,
)
