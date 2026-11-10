package com.example.streetvoicetv.playback

import android.app.Application
import androidx.media3.common.PlaybackException
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.ui.common.UserMessages
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric only because media3's PlaybackException touches android.os classes.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class PlaybackManagerTest {

    private val dispatcher = StandardTestDispatcher()
    private val h = ManagerHarness(dispatcher)
    private val manager get() = h.manager
    private val engine get() = h.engine
    private val streams get() = h.streams
    private val state get() = manager.state.value

    private fun runCurrent() = dispatcher.scheduler.runCurrent()
    private fun advance(ms: Long) {
        dispatcher.scheduler.advanceTimeBy(ms)
        runCurrent()
    }

    /** Queue [songs], let the URL load and the player become ready. */
    private fun startPlaying(count: Int = 3, start: Int = 0) {
        manager.playQueue(testSongs(count), start)
        runCurrent()
        engine.becomeReady()
        runCurrent()
    }

    // --- loading / stale loads --------------------------------------------------------------

    @Test
    fun `skipping while a URL loads cancels the stale load`() {
        streams.gated = true
        manager.playQueue(testSongs(3), 0)
        runCurrent()
        manager.playNext()
        runCurrent()
        assertEquals(listOf(1, 2), streams.requests)

        streams.complete(1) // stale: must not start
        runCurrent()
        assertTrue(engine.loads.isEmpty())
        assertEquals(2, state.song?.id)
        assertEquals(1, state.queueIndex)
        assertTrue(state.isLoading)

        streams.complete(2)
        runCurrent()
        assertEquals(listOf(FakeStreams.url(2) to 0L), engine.loads)
        assertEquals(2, state.song?.id)
    }

    @Test
    fun `the previous song stops at once when switching`() {
        startPlaying()
        assertTrue(engine.isPlaying)
        streams.gated = true
        manager.playNext()
        assertFalse("old song silenced while the next URL loads", engine.isPlaying)
        assertEquals(2, state.song?.id)
        assertTrue(state.isLoading)
        assertTrue("shows as playing (wanted) while loading", state.isPlaying)
    }

    @Test
    fun `play is reported once per song when it actually starts`() {
        streams.gated = true
        manager.playQueue(testSongs(3), 0)
        runCurrent()
        manager.playNext() // song 1 skipped before it played
        runCurrent()
        streams.complete(2)
        runCurrent()
        assertTrue(h.reported.isEmpty())

        engine.becomeReady()
        runCurrent()
        assertEquals(listOf(2), h.reported)

        manager.pause()
        manager.resume()
        runCurrent()
        assertEquals(listOf(2), h.reported)
    }

    @Test
    fun `repeat one loops the song and counts each loop`() {
        h.manager.toggleRepeatMode() // ALL
        h.manager.toggleRepeatMode() // ONE
        startPlaying()
        engine.end()
        runCurrent()
        assertEquals(1, state.song?.id)
        assertTrue(engine.isPlaying)
        assertEquals(1, engine.loads.size)
        assertEquals(listOf(1, 1), h.reported)
    }

    @Test
    fun `stale player events after a switch are ignored`() {
        startPlaying()
        streams.gated = true
        manager.playNext()
        engine.deliverStaleEnded()
        runCurrent()
        assertEquals(2, state.song?.id)
        assertEquals(listOf(1, 2), streams.requests)
    }

    // --- setQueue atomicity -----------------------------------------------------------------

    @Test
    fun `setQueue switches atomically and keeps the new queue`() {
        startPlaying(count = 2)
        streams.gated = true
        manager.setQueue(testSongs(3, firstId = 10), 1)

        assertFalse(engine.isPlaying)
        assertEquals(11, state.song?.id)
        assertEquals(3, state.queueSize)
        assertEquals(1, state.queueIndex)
        assertTrue(state.hasNext)
        assertTrue(state.hasPrevious)

        // A late ENDED from the old song must not advance the new queue.
        engine.deliverStaleEnded()
        runCurrent()
        streams.complete(11)
        runCurrent()
        assertEquals(FakeStreams.url(11), engine.loads.last().first)
        assertEquals(11, state.song?.id)
        assertEquals(3, state.queueSize)
    }

    @Test
    fun `selecting the song that is already playing does not restart it`() {
        startPlaying(count = 2)
        manager.updateSongDetail(testSong(1).copy(lyrics = "lyrics"))
        manager.setQueue(testSongs(5), 0)
        runCurrent()
        assertEquals(1, engine.loads.size)
        assertEquals(5, state.queueSize)
        assertEquals("lyrics", state.song?.lyrics)
        assertTrue(engine.isPlaying)
    }

    // --- errors -----------------------------------------------------------------------------

    @Test
    fun `play after a player error refetches the URL and resumes at the last position`() {
        startPlaying()
        engine.currentPosition = 50_000
        engine.fail(PlaybackException.ERROR_CODE_DECODING_FAILED)
        runCurrent()
        assertEquals(UserMessages.PLAYBACK_FAILED, state.error)
        assertFalse(state.isPlaying)
        assertEquals(NowPlaying(state.song, false, UserMessages.PLAYBACK_FAILED), manager.nowPlaying.value)

        manager.togglePlayPause()
        runCurrent()
        assertNull(state.error)
        assertEquals(listOf(1, 1), streams.requests)
        assertEquals(FakeStreams.url(1) to 50_000L, engine.loads.last())
        engine.becomeReady()
        runCurrent()
        assertTrue(engine.isPlaying)
        assertEquals("a resumed song is not counted again", listOf(1), h.reported)
    }

    @Test
    fun `transient errors are retried automatically a limited number of times`() {
        startPlaying()
        engine.fail(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        runCurrent()
        assertNull(state.error)
        assertTrue(state.isLoading)
        advance(1_000)
        assertEquals(2, engine.loads.size)

        engine.fail(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) // e.g. expired signed URL
        runCurrent()
        advance(2_000)
        assertEquals(3, engine.loads.size)

        engine.fail(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        advance(10_000)
        assertEquals(3, engine.loads.size)
        assertEquals(UserMessages.NETWORK, state.error)
    }

    @Test
    fun `URL failure on auto-next shows an error for the next song`() {
        startPlaying()
        streams.failing += 2
        engine.end()
        runCurrent()
        assertEquals(2, state.song?.id)
        assertNotNull(state.error)
        assertFalse(state.isLoading)

        streams.failing.clear()
        manager.retry()
        runCurrent()
        assertNull(state.error)
        assertEquals(FakeStreams.url(2), engine.loads.last().first)
    }

    // --- end of queue -----------------------------------------------------------------------

    @Test
    fun `play at the end of the queue restarts the current song`() {
        startPlaying(count = 1)
        engine.end()
        runCurrent()
        assertFalse(state.isPlaying)
        assertFalse(state.hasNext)

        manager.togglePlayPause()
        runCurrent()
        assertEquals(0L, engine.currentPosition)
        assertTrue(engine.isPlaying)
        assertTrue(state.isPlaying)
    }

    // --- hasNext / hasPrevious --------------------------------------------------------------

    @Test
    fun `hasNext and hasPrevious follow the real queue logic`() {
        startPlaying(count = 3, start = 2)
        assertFalse(state.hasNext)
        assertTrue(state.hasPrevious)

        manager.toggleRepeatMode() // ALL
        assertTrue(state.hasNext)
        manager.toggleRepeatMode() // ONE: manual skip wraps
        assertTrue(state.hasNext)
        manager.playNext()
        assertEquals(1, state.song?.id)

        manager.toggleRepeatMode() // OFF
        manager.toggleShuffle() // order starts at the current song
        assertFalse(state.hasPrevious)
        assertTrue(state.hasNext)
        manager.playNext()
        manager.playNext()
        assertFalse("end of the shuffle order", state.hasNext)
    }

    @Test
    fun `shuffle survives replacing the queue with a shorter one`() {
        manager.toggleShuffle()
        manager.toggleRepeatMode() // ALL
        startPlaying(count = 8, start = 7)
        manager.playQueue(testSongs(2, firstId = 50), 1)
        runCurrent()
        repeat(6) {
            manager.playNext()
            runCurrent()
            assertTrue(state.queueIndex in 0..1)
        }
    }

    // --- seek / position --------------------------------------------------------------------

    @Test
    fun `relative seeks accumulate while paused and clamp to the duration`() {
        startPlaying()
        manager.pause()
        manager.seekTo(10_000)
        repeat(3) { manager.seekBy(10_000) }
        assertEquals(40_000L, manager.positionMs.value)
        assertEquals(40_000L, engine.currentPosition)
        assertEquals(40_000L, state.positionMs)

        manager.seekBy(1_000_000)
        assertEquals(200_000L, engine.currentPosition)
        manager.seekBy(-1_000_000)
        assertEquals(0L, engine.currentPosition)
    }

    @Test
    fun `position ticks do not touch the playback state`() {
        startPlaying()
        val before = manager.state.value
        engine.currentPosition = 1_000
        advance(1_000)
        engine.currentPosition = 2_000
        advance(1_000)
        assertEquals(2_000L, manager.positionMs.value)
        assertSame(before, manager.state.value)

        manager.pause()
        engine.currentPosition = 9_000
        advance(5_000)
        assertEquals("ticker stopped while paused", 2_000L, manager.positionMs.value)
    }

    @Test
    fun `previous restarts the song after three seconds`() {
        startPlaying(start = 1)
        manager.seekTo(5_000)
        manager.playPrevious()
        assertEquals(2, state.song?.id)
        assertEquals(0L, engine.currentPosition)
        manager.playPrevious()
        assertEquals(1, state.song?.id)
    }

    // --- service ----------------------------------------------------------------------------

    @Test
    fun `service is started with playback and stopped by stop`() {
        startPlaying()
        assertTrue(h.serviceStarts > 0)
        assertTrue(manager.isPlaybackActive)
        manager.stop()
        assertEquals(1, h.serviceStops)
        assertNull(state.song)
        assertFalse(manager.isPlaybackActive)
    }

    @Test
    fun `playback stays active (foreground) while the next URL loads`() {
        startPlaying()
        streams.gated = true
        engine.end()
        runCurrent()
        assertEquals(2, state.song?.id)
        assertTrue(manager.isPlaybackActive)
    }
}
