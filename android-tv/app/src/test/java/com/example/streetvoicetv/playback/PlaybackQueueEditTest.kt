package com.example.streetvoicetv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** playNext / addToQueue / remove / autoplay / snapshot on [PlaybackQueue] (pure JVM). */
class PlaybackQueueEditTest {

    private fun queue(count: Int = 5, start: Int = 0, shuffle: Boolean = false) =
        PlaybackQueue(Random(1)).apply {
            replace(testSongs(count), start)
            if (shuffle) setShuffle(true)
        }

    /** Song ids in play order from the current song on. */
    private fun PlaybackQueue.upcomingIds(): List<Int> {
        val v = view()
        return v.items.drop(v.currentPosition).map { it.song.id }
    }

    private fun PlaybackQueue.playAllIds(): List<Int> {
        val ids = mutableListOf(current!!.id)
        while (moveToNext()) ids += current!!.id
        return ids
    }

    @Test
    fun `insertNext plays right after the current song`() {
        val q = queue(start = 1)
        q.insertNext(testSong(100))
        assertEquals(listOf(2, 100, 3, 4, 5), q.upcomingIds())
        assertEquals(2, q.current?.id)
    }

    @Test
    fun `insertNext with shuffle goes right after current in the shuffled order`() {
        val q = queue(shuffle = true, start = 2)
        val before = q.upcomingIds()
        q.insertNext(testSong(100))
        val after = q.upcomingIds()
        assertEquals(before.first(), after.first())
        assertEquals(100, after[1])
        assertEquals(before.drop(1), after.drop(2))
        // The order stays a permutation of the songs and points at the current song.
        assertEquals(q.songs.indices.toList(), q.order.sorted())
        assertEquals(before.first(), q.current?.id)
    }

    @Test
    fun `insertIntoQueue goes after user songs but before autoplay songs`() {
        val q = queue(count = 3)
        q.appendAutoplay(listOf(testSong(50), testSong(51)))
        q.insertIntoQueue(testSong(100))
        assertEquals(listOf(1, 2, 3, 100, 50, 51), q.upcomingIds())
        assertFalse(q.isAutoplay(q.songs.indexOfFirst { it.id == 100 }))
        assertTrue(q.isAutoplay(q.songs.indexOfFirst { it.id == 50 }))
    }

    @Test
    fun `insertIntoQueue with shuffle lands before autoplay in play order`() {
        val q = queue(count = 4, shuffle = true)
        q.appendAutoplay(listOf(testSong(50)))
        q.insertIntoQueue(testSong(100))
        val ids = q.upcomingIds()
        assertEquals(listOf(100, 50), ids.takeLast(2))
        assertEquals(6, ids.size)
    }

    @Test
    fun `remove a later song keeps the current one`() {
        val q = queue(start = 1)
        assertEquals(PlaybackQueue.Removal.REMOVED, q.remove(3))
        assertEquals(2, q.current?.id)
        assertEquals(listOf(2, 3, 5), q.upcomingIds())
    }

    @Test
    fun `remove an earlier song shifts the current index`() {
        val q = queue(start = 3)
        q.remove(0)
        assertEquals(4, q.current?.id)
        assertEquals(2, q.currentIndex)
    }

    @Test
    fun `remove current skips to the next song`() {
        val q = queue(start = 1)
        assertEquals(PlaybackQueue.Removal.CURRENT_SKIPPED, q.remove(1))
        assertEquals(3, q.current?.id)
        assertEquals(listOf(1, 3, 4, 5), q.songs.map { it.id })
    }

    @Test
    fun `remove current with shuffle skips to the next in the shuffled order`() {
        val q = queue(shuffle = true)
        val expectedNext = q.upcomingIds()[1]
        val currentIndex = q.currentIndex
        assertEquals(PlaybackQueue.Removal.CURRENT_SKIPPED, q.remove(currentIndex))
        assertEquals(expectedNext, q.current?.id)
        assertEquals(4, q.size)
        assertEquals(q.songs.indices.toList(), q.order.sorted())
        assertEquals(q.currentIndex, q.order[0])
    }

    @Test
    fun `remove the last current song steps back`() {
        val q = queue(count = 3, start = 2)
        assertEquals(PlaybackQueue.Removal.CURRENT_REMOVED_AT_END, q.remove(2))
        assertEquals(2, q.current?.id)
        assertEquals(2, q.size)
    }

    @Test
    fun `remove the only song empties the queue`() {
        val q = queue(count = 1)
        assertEquals(PlaybackQueue.Removal.EMPTIED, q.remove(0))
        assertEquals(null, q.current)
        assertEquals(PlaybackQueue.Removal.NONE, q.remove(0))
    }

    @Test
    fun `shuffle play-through after edits still plays every song once`() {
        val q = queue(count = 6, shuffle = true)
        q.insertNext(testSong(100))
        q.insertIntoQueue(testSong(101))
        assertEquals(1, q.current?.id)
        q.remove(q.songs.indexOfFirst { it.id == 3 })
        val played = q.playAllIds()
        assertEquals(q.size, played.size)
        assertEquals(q.songs.map { it.id }.toSet(), played.toSet())
        assertEquals(100, played[1])
    }

    @Test
    fun `autoplay songs are appended last, flagged and dropped when upcoming`() {
        val q = queue(count = 2, start = 1)
        q.appendAutoplay(listOf(testSong(50), testSong(51)))
        val view = q.view(autoplayEnabled = true)
        assertEquals(listOf(false, false, true, true), view.items.map { it.isAutoplay })
        assertEquals(2, view.autoplayDividerPosition)
        assertTrue(q.hasNext)

        q.moveToNext() // now on 50
        assertTrue(q.dropUpcomingAutoplay())
        assertEquals(listOf(1, 2, 50), q.songs.map { it.id })
        assertEquals(50, q.current?.id)
        assertFalse(q.hasNext)
    }

    @Test
    fun `shuffle keeps autoplay songs at the end of the order`() {
        val q = queue(count = 4)
        q.appendAutoplay(listOf(testSong(50), testSong(51)))
        q.setShuffle(true)
        assertEquals(listOf(50, 51), q.upcomingIds().takeLast(2))
    }

    @Test
    fun `snapshot restores songs, position and shuffle order`() {
        val q = queue(count = 6, shuffle = true)
        q.moveToNext()
        q.appendAutoplay(listOf(testSong(50)))
        val snapshot = q.snapshot()!!
        val restored = PlaybackQueue(Random(99)).apply { restore(snapshot) }
        assertEquals(q.songs, restored.songs)
        assertEquals(q.current, restored.current)
        assertEquals(q.order, restored.order)
        assertTrue(restored.shuffleEnabled)
        assertEquals(q.upcomingIds(), restored.upcomingIds())
        assertTrue(restored.isAutoplay(6))
    }

    @Test
    fun `restore regenerates an inconsistent shuffle order`() {
        val q = queue(count = 4, shuffle = true)
        val broken = q.snapshot()!!.copy(shuffleOrder = listOf(0, 0, 1), shufflePosition = 7)
        val restored = PlaybackQueue(Random(3)).apply { restore(broken) }
        assertEquals(restored.songs.indices.toList(), restored.order.sorted())
        assertEquals(restored.currentIndex, restored.order[0])
    }
}
