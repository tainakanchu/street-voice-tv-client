package com.example.streetvoicetv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaybackQueueTest {

    private fun queue(size: Int, start: Int = 0, shuffle: Boolean = false, repeat: RepeatMode = RepeatMode.OFF, seed: Int = 1) =
        PlaybackQueue(Random(seed)).apply {
            setShuffle(shuffle)
            repeatMode = repeat
            replace(testSongs(size), start)
        }

    @Test
    fun `sequential next and previous stop at the ends when repeat is off`() {
        val q = queue(3)
        assertFalse(q.hasPrevious)
        assertFalse(q.moveToPrevious())
        assertTrue(q.moveToNext())
        assertTrue(q.moveToNext())
        assertEquals(2, q.currentIndex)
        assertFalse(q.hasNext)
        assertFalse(q.moveToNext())
        assertEquals(2, q.currentIndex)
    }

    @Test
    fun `repeat all and repeat one wrap when skipping manually`() {
        for (mode in listOf(RepeatMode.ALL, RepeatMode.ONE)) {
            val q = queue(3, start = 2, repeat = mode)
            assertTrue(q.hasNext)
            assertTrue(q.moveToNext())
            assertEquals(0, q.currentIndex)
            assertTrue(q.moveToPrevious())
            assertEquals(2, q.currentIndex)
        }
    }

    @Test
    fun `shuffle visits every song once starting with the current one`() {
        val q = queue(10, start = 4, shuffle = true)
        val visited = mutableListOf(q.currentIndex)
        while (q.moveToNext()) visited += q.currentIndex
        assertEquals(4, visited.first())
        assertEquals((0 until 10).toSet(), visited.toSet())
        assertEquals(10, visited.size)
        assertFalse(q.hasNext)
        // ...and back again in the same order.
        val back = mutableListOf(q.currentIndex)
        while (q.moveToPrevious()) back += q.currentIndex
        assertEquals(visited.reversed(), back)
    }

    @Test
    fun `shuffle with repeat all never plays the same song twice in a row when wrapping`() {
        repeat(200) { seed ->
            val q = queue(4, shuffle = true, repeat = RepeatMode.ALL, seed = seed)
            var last = q.currentIndex
            repeat(12) {
                assertTrue(q.moveToNext())
                assertNotEquals("seed $seed", last, q.currentIndex)
                last = q.currentIndex
            }
        }
    }

    @Test
    fun `replacing the queue regenerates the shuffle order`() {
        val q = queue(10, start = 9, shuffle = true)
        q.replace(testSongs(2, firstId = 100), 1)
        assertEquals(listOf(1, 0), q.order)
        assertEquals(101, q.current?.id)
        assertTrue(q.moveToNext())
        assertEquals(100, q.current?.id)
        assertFalse(q.moveToNext())
    }

    @Test
    fun `empty queue is safe`() {
        val q = queue(0, shuffle = true, repeat = RepeatMode.ALL)
        assertNull(q.current)
        assertEquals(-1, q.currentIndex)
        assertTrue(q.order.isEmpty())
        assertFalse(q.hasNext)
        assertFalse(q.hasPrevious)
        assertFalse(q.moveToNext())
        assertFalse(q.moveToPrevious())
        q.setShuffle(false)
        q.setShuffle(true)
        assertTrue(q.order.isEmpty())
    }

    @Test
    fun `start index is coerced into range`() {
        assertEquals(2, queue(3, start = 7).currentIndex)
        assertEquals(0, queue(3, start = -3).currentIndex)
    }

    @Test
    fun `turning shuffle on mid-queue starts the order at the current song`() {
        val q = queue(5, start = 3)
        q.setShuffle(true)
        assertEquals(3, q.order.first())
        assertFalse(q.hasPrevious)
        assertTrue(q.hasNext)
    }

    // --- view(): the queue in play order ---------------------------------------------------

    private val QueueView.indices get() = items.map { it.queueIndex }

    @Test
    fun `view without shuffle lists the queue with played songs before the current one`() {
        val v = queue(5, start = 2).view()
        assertEquals(listOf(0, 1, 2, 3, 4), v.indices)
        assertEquals(2, v.currentPosition)
        assertEquals(3, v.current?.song?.id)
        assertEquals(3, v.next?.queueIndex)
        assertEquals(2, v.upcomingCount)
        assertTrue(v.isPlayed(1))
        assertFalse(v.isPlayed(2))
        assertFalse(v.wrapsAround)
    }

    @Test
    fun `view at the end of the queue has no next song when repeat is off`() {
        val v = queue(3, start = 2).view()
        assertEquals(2, v.currentPosition)
        assertNull(v.next)
        assertEquals(0, v.upcomingCount)
    }

    @Test
    fun `view with shuffle follows the shuffled order and the next song is the one played next`() {
        val q = queue(8, start = 5, shuffle = true)
        assertTrue(q.moveToNext())
        assertTrue(q.moveToNext())
        val v = q.view()
        assertEquals(q.order, v.indices)
        assertEquals(2, v.currentPosition)
        assertEquals(q.currentIndex, v.current?.queueIndex)
        val expectedNext = v.next?.queueIndex
        assertTrue(q.moveToNext())
        assertEquals(expectedNext, q.currentIndex)
    }

    @Test
    fun `view with repeat all wraps - songs before the current one follow after the end`() {
        val q = queue(4, start = 2, repeat = RepeatMode.ALL)
        val v = q.view()
        assertEquals(listOf(2, 3, 0, 1), v.indices)
        assertEquals(0, v.currentPosition)
        assertTrue(v.wrapsAround)
        assertFalse(v.reshufflesAtEnd)
        // The listed order is exactly what next plays.
        val played = mutableListOf(q.currentIndex)
        repeat(3) { q.moveToNext(); played += q.currentIndex }
        assertEquals(v.indices, played)
    }

    @Test
    fun `view with shuffle and repeat keeps the history and reshuffles at the end`() {
        val q = queue(3, shuffle = true, repeat = RepeatMode.ALL)
        q.moveToNext()
        q.moveToNext()
        val v = q.view()
        assertEquals(2, v.currentPosition)
        assertNull(v.next)
        assertTrue(v.reshufflesAtEnd)
    }

    @Test
    fun `view of a single song and of an empty queue`() {
        val single = queue(1).view()
        assertEquals(listOf(0), single.indices)
        assertEquals(0, single.currentPosition)
        assertNull(single.next)
        assertEquals(listOf(0), queue(1, repeat = RepeatMode.ALL).view().indices)

        val empty = queue(0).view()
        assertTrue(empty.isEmpty)
        assertEquals(-1, empty.currentPosition)
        assertNull(empty.current)
        assertNull(empty.next)
    }

    @Test
    fun `jumpTo moves within the list without rebuilding it`() {
        val q = queue(5)
        assertTrue(q.jumpTo(3))
        assertEquals(3, q.currentIndex)
        assertEquals(listOf(0, 1, 2, 3, 4), q.view().indices)
        assertEquals(3, q.view().currentPosition)
        assertFalse(q.jumpTo(5))
        assertFalse(q.jumpTo(-1))
        assertEquals(3, q.currentIndex)
    }

    @Test
    fun `jumpTo keeps the shuffled order and moves the position in it`() {
        val q = queue(8, start = 0, shuffle = true)
        val order = q.order
        val target = order[5]
        assertTrue(q.jumpTo(target))
        assertEquals(order, q.order)
        assertEquals(5, q.view().currentPosition)
        assertEquals(order[6], q.view().next?.queueIndex)
        assertTrue(q.moveToNext())
        assertEquals(order[6], q.currentIndex)
        // Jumping back: the songs after it are upcoming again.
        assertTrue(q.jumpTo(order[1]))
        assertEquals(1, q.view().currentPosition)
        assertTrue(q.hasPrevious)
    }

    @Test
    fun `revision changes with every change that affects the view`() {
        val q = queue(3)
        val seen = mutableSetOf(q.revision)
        fun changed() = assertTrue(seen.add(q.revision))
        q.moveToNext(); changed()
        q.moveToPrevious(); changed()
        q.jumpTo(2); changed()
        q.setShuffle(true); changed()
        q.repeatMode = RepeatMode.ALL; changed()
        q.updateCurrent(q.current!!.copy(lyrics = "x")); changed()
        q.replace(testSongs(2), 0); changed()
        val before = q.revision
        q.repeatMode = RepeatMode.ALL // same value: no change
        q.updateCurrent(testSong(99)) // not the current song: no change
        assertEquals(before, q.revision)
    }

    @Test
    fun `updateCurrent only replaces the same song`() {
        val q = queue(3)
        assertFalse(q.updateCurrent(testSong(2).copy(lyrics = "x")))
        assertTrue(q.updateCurrent(testSong(1).copy(lyrics = "x")))
        assertEquals("x", q.current?.lyrics)
    }
}
