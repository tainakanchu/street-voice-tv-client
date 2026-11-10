package com.example.streetvoicetv.playback

import android.app.Application
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [PlaybackManager.upNext] and [PlaybackManager.playQueueItem]. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class PlaybackManagerUpNextTest {

    private val dispatcher = StandardTestDispatcher()
    private val h = ManagerHarness(dispatcher)
    private val manager get() = h.manager
    private val engine get() = h.engine
    private val upNext get() = manager.upNext.value
    private val ids get() = upNext.items.map { it.song.id }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()
    private fun advance(ms: Long) {
        dispatcher.scheduler.advanceTimeBy(ms)
        runCurrent()
    }

    private fun startPlaying(count: Int = 5, start: Int = 0) {
        manager.playQueue(testSongs(count), start)
        runCurrent()
        engine.becomeReady()
        runCurrent()
    }

    @Test
    fun `empty before anything is queued`() {
        assertTrue(upNext.isEmpty)
        assertEquals(-1, upNext.currentPosition)
    }

    @Test
    fun `normal order with the current position, following next and previous`() {
        startPlaying(5, start = 1)
        assertEquals(listOf(1, 2, 3, 4, 5), ids)
        assertEquals(1, upNext.currentPosition)
        assertEquals(3, upNext.next?.song?.id)

        manager.playNext()
        runCurrent()
        assertEquals(2, upNext.currentPosition)
        assertEquals(3, upNext.current?.song?.id)
    }

    @Test
    fun `shuffle publishes the shuffled order, and next plays the listed next song`() {
        startPlaying(10, start = 3)
        manager.toggleShuffle()
        assertTrue(upNext.shuffleEnabled)
        assertEquals(0, upNext.currentPosition)
        assertEquals(4, upNext.current?.song?.id)
        val listed = ids
        assertEquals((1..10).toSet(), listed.toSet())

        val played = mutableListOf(manager.state.value.song!!.id)
        repeat(9) {
            manager.playNext()
            runCurrent()
            played += manager.state.value.song!!.id
        }
        assertEquals(listed, played)
        assertEquals(listed, ids) // the order itself did not change while skipping
        assertEquals(9, upNext.currentPosition)
    }

    @Test
    fun `repeat all wraps - the songs already played are listed after the end`() {
        startPlaying(4, start = 2)
        manager.toggleRepeatMode() // ALL
        assertEquals(RepeatMode.ALL, upNext.repeatMode)
        assertEquals(listOf(3, 4, 1, 2), ids)
        assertEquals(0, upNext.currentPosition)

        manager.playNext()
        runCurrent()
        assertEquals(listOf(4, 1, 2, 3), ids)
        assertEquals(1, upNext.next?.song?.id)
        manager.playNext() // wraps to the first song
        runCurrent()
        assertEquals(1, manager.state.value.song?.id)
    }

    @Test
    fun `single song`() {
        startPlaying(1)
        assertEquals(listOf(1), ids)
        assertEquals(0, upNext.currentPosition)
        assertNull(upNext.next)
    }

    @Test
    fun `playQueueItem jumps to the song without rebuilding the queue`() {
        startPlaying(5)
        manager.toggleShuffle()
        val order = upNext.items
        val target = order[3]

        manager.playQueueItem(target.queueIndex)
        runCurrent()
        assertEquals(target.song.id, manager.state.value.song?.id)
        assertEquals(order, upNext.items)
        assertEquals(3, upNext.currentPosition)
        assertEquals(order[4], upNext.next)
        assertEquals(FakeStreams.url(target.song.id), engine.loads.last().first)
        assertEquals(0L, engine.loads.last().second)

        manager.playNext()
        runCurrent()
        assertEquals(order[4].song.id, manager.state.value.song?.id)
    }

    @Test
    fun `playQueueItem on the current song resumes instead of restarting`() {
        startPlaying(3)
        engine.currentPosition = 50_000
        manager.pause()
        val loads = engine.loads.size

        manager.playQueueItem(0)
        runCurrent()
        assertTrue(manager.state.value.isPlaying)
        assertEquals(loads, engine.loads.size)
        assertEquals(50_000L, engine.currentPosition)

        manager.playQueueItem(0) // already playing: nothing happens
        manager.playQueueItem(7) // out of range: ignored
        manager.playQueueItem(-1)
        runCurrent()
        assertEquals(loads, engine.loads.size)
        assertEquals(1, manager.state.value.song?.id)
    }

    @Test
    fun `upNext does not emit on position ticks, seeks or play-pause`() {
        startPlaying(3)
        val before = manager.upNext.value
        engine.currentPosition = 1_000
        advance(1_000)
        engine.currentPosition = 2_000
        advance(1_000)
        manager.seekBy(10_000)
        manager.pause()
        manager.resume()
        runCurrent()
        assertSame(before, manager.upNext.value)

        manager.playNext()
        runCurrent()
        assertFalse(before === manager.upNext.value)
    }

    @Test
    fun `stop empties the queue view`() {
        startPlaying(3)
        manager.stop()
        assertTrue(upNext.isEmpty)
    }
}
