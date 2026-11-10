package com.example.streetvoicetv.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateTest {

    @Test
    fun `formats position and duration as m-ss`() {
        val state = PlaybackState(positionMs = 83_999, durationMs = 245_000)
        assertEquals("1:23", state.formattedPosition)
        assertEquals("4:05", state.formattedDuration)
    }

    @Test
    fun `formats zero and over-an-hour values`() {
        assertEquals("0:00", PlaybackState().formattedPosition)
        assertEquals("61:01", PlaybackState(durationMs = 3_661_000).formattedDuration)
    }

    @Test
    fun `progress is zero when duration unknown`() {
        assertEquals(0f, PlaybackState(positionMs = 1_000, durationMs = 0).progress)
        assertEquals(0.5f, PlaybackState(positionMs = 1_000, durationMs = 2_000).progress)
    }

    @Test
    fun `hasNext and hasPrevious follow queue position when repeat is off`() {
        val first = PlaybackState(queueSize = 3, queueIndex = 0)
        assertTrue(first.hasNext)
        assertFalse(first.hasPrevious)

        val last = PlaybackState(queueSize = 3, queueIndex = 2)
        assertFalse(last.hasNext)
        assertTrue(last.hasPrevious)
    }

    @Test
    fun `repeat and shuffle allow wrapping`() {
        val repeatAll = PlaybackState(queueSize = 3, queueIndex = 2, repeatMode = RepeatMode.ALL)
        assertTrue(repeatAll.hasNext)

        val shuffleSingle = PlaybackState(queueSize = 1, queueIndex = 0, shuffleEnabled = true)
        assertFalse(shuffleSingle.hasNext)
        assertFalse(shuffleSingle.hasPrevious)

        // The defaults ignore shuffle; PlaybackManager passes exact values from PlaybackQueue.
        val explicit = PlaybackState(queueSize = 2, queueIndex = 1, shuffleEnabled = true, nextAvailable = true)
        assertTrue(explicit.hasNext)
    }
}
