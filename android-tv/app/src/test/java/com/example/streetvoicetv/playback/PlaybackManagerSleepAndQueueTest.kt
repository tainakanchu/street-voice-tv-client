package com.example.streetvoicetv.playback

import android.app.Application
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sleep timer (virtual time) and the queue edits (playNext(song) / addToQueue / removeFromQueue). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class PlaybackManagerSleepAndQueueTest {

    private val dispatcher = StandardTestDispatcher()
    private val h = ManagerHarness(dispatcher, clock = { dispatcher.scheduler.currentTime })
    private val manager get() = h.manager
    private val engine get() = h.engine
    private val state get() = manager.state.value

    private fun runCurrent() = dispatcher.scheduler.runCurrent()
    private fun advance(ms: Long) {
        dispatcher.scheduler.advanceTimeBy(ms)
        runCurrent()
    }

    private fun startPlaying(count: Int = 3, start: Int = 0) {
        manager.playQueue(testSongs(count), start)
        runCurrent()
        engine.becomeReady()
        runCurrent()
    }

    private fun upcomingIds(): List<Int> {
        val v = manager.upNext.value
        return v.items.drop(v.currentPosition).map { it.song.id }
    }

    // --- sleep timer ------------------------------------------------------------------------

    @Test
    fun `countdown ticks, fades out over the last 10 s, pauses and restores the volume`() {
        startPlaying()
        manager.setSleepTimer(SleepTimerOption.MIN_15)
        assertEquals(SleepTimerState.Countdown(15 * 60_000L), manager.sleepTimer.value)

        advance(60_000)
        assertEquals(14 * 60_000L, (manager.sleepTimer.value as SleepTimerState.Countdown).remainingMs)
        assertEquals(0.7f, engine.volume, 0.0001f)

        advance(13 * 60_000L + 55_000) // 5 s left
        val remaining = (manager.sleepTimer.value as SleepTimerState.Countdown).remainingMs
        assertTrue(remaining in 4_000..5_000)
        assertTrue("fading", engine.volume < 0.7f * 0.6f)
        assertTrue("still playing while fading", engine.playWhenReady)

        advance(5_000)
        assertEquals(SleepTimerState.Off, manager.sleepTimer.value)
        assertFalse(engine.playWhenReady)
        assertFalse(state.isPlaying)
        assertEquals("volume restored", 0.7f, engine.volume, 0.0001f)
    }

    @Test
    fun `cancelling during the fade restores the volume and keeps playing`() {
        startPlaying()
        manager.setSleepTimer(SleepTimerOption.MIN_15)
        advance(15 * 60_000L - 3_000)
        assertTrue(engine.volume < 0.7f)
        manager.setSleepTimer(SleepTimerOption.OFF)
        assertEquals(0.7f, engine.volume, 0.0001f)
        advance(10_000)
        assertTrue(engine.playWhenReady)
        assertEquals(SleepTimerState.Off, manager.sleepTimer.value)
    }

    @Test
    fun `a paused player stays paused and the timer still ends`() {
        startPlaying()
        manager.setSleepTimer(SleepTimerOption.MIN_30)
        manager.pause() // e.g. from the media session
        advance(30 * 60_000L)
        assertEquals(SleepTimerState.Off, manager.sleepTimer.value)
        assertFalse(engine.playWhenReady)
        assertEquals(0.7f, engine.volume, 0.0001f)
    }

    @Test
    fun `end of song pauses when the song ends and loads the next one paused`() {
        startPlaying()
        manager.setSleepTimer(SleepTimerOption.END_OF_SONG)
        assertEquals(SleepTimerState.EndOfSong, manager.sleepTimer.value)

        // Last seconds of the song: the ticker fades it.
        engine.currentPosition = 200_000 - 5_000
        advance(1_000)
        assertTrue(engine.volume < 0.7f)

        engine.end()
        runCurrent()
        assertEquals(SleepTimerState.Off, manager.sleepTimer.value)
        assertEquals(2, state.song?.id)
        assertFalse(engine.playWhenReady)
        assertFalse(state.isPlaying)
        assertEquals(0.7f, engine.volume, 0.0001f)
    }

    @Test
    fun `end of song restores the volume when skipping away from the fade`() {
        startPlaying()
        manager.setSleepTimer(SleepTimerOption.END_OF_SONG)
        engine.currentPosition = 200_000 - 2_000
        advance(1_000)
        assertTrue(engine.volume < 0.7f)
        manager.playNext()
        assertEquals(0.7f, engine.volume, 0.0001f)
        assertEquals(SleepTimerState.EndOfSong, manager.sleepTimer.value)
    }

    @Test
    fun `stop cancels the timer`() {
        startPlaying()
        manager.setSleepTimer(SleepTimerOption.MIN_60)
        manager.stop()
        assertEquals(SleepTimerState.Off, manager.sleepTimer.value)
    }

    // --- queue edits ------------------------------------------------------------------------

    @Test
    fun `playNext(song) inserts after the current song`() {
        startPlaying(count = 3)
        manager.playNext(testSong(100))
        assertEquals(listOf(1, 100, 2, 3), upcomingIds())
        assertEquals(1, state.song?.id)
        manager.playNext()
        runCurrent()
        assertEquals(100, state.song?.id)
    }

    @Test
    fun `playNext(song) with shuffle plays it next`() {
        manager.toggleShuffle()
        startPlaying(count = 6)
        manager.playNext(testSong(100))
        manager.playNext()
        runCurrent()
        assertEquals(100, state.song?.id)
    }

    @Test
    fun `addToQueue appends and on an empty queue it starts playing`() {
        manager.addToQueue(testSong(7))
        runCurrent()
        assertEquals(7, state.song?.id)
        assertTrue(engine.playWhenReady)

        manager.addToQueue(testSong(8))
        manager.addToQueue(testSong(9))
        assertEquals(listOf(7, 8, 9), upcomingIds())
        assertTrue(state.hasNext)
    }

    @Test
    fun `removeFromQueue of another song keeps playing`() {
        startPlaying(count = 4)
        manager.removeFromQueue(2)
        assertEquals(listOf(1, 2, 4), upcomingIds())
        assertEquals(1, state.song?.id)
        assertEquals(1, h.streams.requests.size)
    }

    @Test
    fun `removeFromQueue of the current song skips to the next`() {
        startPlaying(count = 3)
        manager.removeFromQueue(0)
        runCurrent()
        assertEquals(2, state.song?.id)
        assertEquals(listOf(1, 2), h.streams.requests)
        assertTrue(engine.playWhenReady)
        assertEquals(2, state.queueSize)
    }

    @Test
    fun `removeFromQueue of the current song with shuffle skips in play order`() {
        manager.toggleShuffle()
        startPlaying(count = 5)
        val next = manager.upNext.value.next!!.song.id
        manager.removeFromQueue(manager.upNext.value.current!!.queueIndex)
        runCurrent()
        assertEquals(next, state.song?.id)
        assertEquals(4, state.queueSize)
    }

    @Test
    fun `removing the last current song loads the previous one paused`() {
        startPlaying(count = 2, start = 1)
        manager.removeFromQueue(1)
        runCurrent()
        assertEquals(1, state.song?.id)
        assertFalse(engine.playWhenReady)
    }

    @Test
    fun `removing the only song stops`() {
        startPlaying(count = 1)
        manager.removeFromQueue(0)
        assertNull(state.song)
        assertEquals(1, h.serviceStops)
    }
}
