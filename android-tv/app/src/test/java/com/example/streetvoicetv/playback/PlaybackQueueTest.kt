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

    @Test
    fun `updateCurrent only replaces the same song`() {
        val q = queue(3)
        assertFalse(q.updateCurrent(testSong(2).copy(lyrics = "x")))
        assertTrue(q.updateCurrent(testSong(1).copy(lyrics = "x")))
        assertEquals("x", q.current?.lyrics)
    }
}
