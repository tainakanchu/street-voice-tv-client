package com.example.streetvoicetv.playback

import android.app.Application
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Session persistence / restore and the recently played list. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class PlaybackManagerSessionTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = { dispatcher.scheduler.currentTime + 1_000_000L }

    private fun harness(store: FakePlaybackStore = FakePlaybackStore(), settings: PlaybackSettings = InMemorySettings()) =
        ManagerHarness(dispatcher, settings = settings, store = store, clock = clock)

    private fun runCurrent() = dispatcher.scheduler.runCurrent()
    private fun advance(ms: Long) {
        dispatcher.scheduler.advanceTimeBy(ms)
        runCurrent()
    }

    private fun ManagerHarness.startPlaying(count: Int = 3, start: Int = 0) {
        manager.playQueue(testSongs(count), start)
        runCurrent()
        engine.becomeReady()
        runCurrent()
    }

    // --- saving -----------------------------------------------------------------------------

    @Test
    fun `saves on song change, pause and every 15 s while playing`() {
        val h = harness()
        runCurrent()
        h.startPlaying()
        val afterStart = h.store.sessionSaves.size
        assertTrue("saved when the song started", afterStart >= 1)
        assertEquals(1, h.store.session?.currentSong?.id)

        h.engine.currentPosition = 30_000
        advance(PlaybackManager.SAVE_INTERVAL_MS)
        assertTrue("periodic save", h.store.sessionSaves.size > afterStart)
        assertTrue(h.store.session!!.positionMs >= 30_000)

        h.engine.currentPosition = 42_000
        h.manager.pause()
        runCurrent()
        assertEquals(42_000L, h.store.session?.positionMs)

        val beforeIdle = h.store.sessionSaves.size
        advance(PlaybackManager.SAVE_INTERVAL_MS * 3)
        assertEquals("no periodic saves while paused", beforeIdle, h.store.sessionSaves.size)

        h.manager.playNext()
        runCurrent()
        assertEquals(2, h.store.session?.currentSong?.id)
        assertEquals(0L, h.store.session?.positionMs)
    }

    @Test
    fun `saveSessionNow writes the current state`() {
        val h = harness()
        h.startPlaying()
        h.store.sessionSaves.clear()
        h.engine.currentPosition = 12_345
        h.manager.saveSessionNow()
        runCurrent()
        assertEquals(12_345, h.store.sessionSaves.single().positionMs)
    }

    @Test
    fun `persisted session round-trips the shuffle order`() {
        val h = harness(settings = InMemorySettings(shuffleEnabled = true))
        h.startPlaying(count = 8)
        h.manager.playNext()
        runCurrent()
        val saved = h.store.session!!
        assertTrue(saved.queue.shuffleEnabled)
        assertEquals(saved.queue.shuffleOrder[saved.queue.shufflePosition], saved.currentIndex)

        val upNextBefore = h.manager.upNext.value.items.map { it.song.id }
        val h2 = harness(store = FakePlaybackStore(session = saved))
        runCurrent()
        assertTrue(h2.manager.resumeLastSession())
        runCurrent()
        assertEquals(upNextBefore, h2.manager.upNext.value.items.map { it.song.id })
        assertTrue(h2.manager.state.value.shuffleEnabled)
    }

    // --- restore ----------------------------------------------------------------------------

    private fun savedSession(position: Long = 61_000, index: Int = 1) = RestorableSession(
        queue = QueueSnapshot(
            songs = testSongs(4),
            currentIndex = index,
            shuffleEnabled = false,
            shuffleOrder = emptyList(),
            shufflePosition = -1,
            autoplayFlags = listOf(false, false, false, false),
        ),
        positionMs = position,
        savedAtMs = 5,
    )

    @Test
    fun `restorable is loaded asynchronously and does not auto-play`() {
        val h = harness(FakePlaybackStore(session = savedSession()))
        assertNull(h.manager.restorable.value)
        runCurrent()
        val restorable = h.manager.restorable.value
        assertNotNull(restorable)
        assertEquals(2, restorable!!.currentSong.id)
        // Nothing was loaded or started on its own.
        assertTrue(h.streams.requests.isEmpty())
        assertNull(h.manager.state.value.song)
    }

    @Test
    fun `resumeLastSession refetches the URL and seeks to the saved position`() {
        val h = harness(FakePlaybackStore(session = savedSession()))
        runCurrent()
        assertTrue(h.manager.resumeLastSession(play = false))
        runCurrent()
        assertEquals(listOf(2), h.streams.requests)
        assertEquals(listOf(FakeStreams.url(2) to 61_000L), h.engine.loads)
        assertFalse("prepared paused", h.engine.playWhenReady)
        assertFalse(h.manager.state.value.isPlaying)
        assertEquals(1, h.manager.state.value.queueIndex)
        assertEquals(4, h.manager.state.value.queueSize)
        assertNull("consumed", h.manager.restorable.value)
        assertFalse(h.manager.resumeLastSession())

        h.manager.resume()
        h.engine.becomeReady()
        runCurrent()
        assertTrue(h.engine.isPlaying)
    }

    @Test
    fun `resumeLastSession plays by default`() {
        val h = harness(FakePlaybackStore(session = savedSession()))
        runCurrent()
        h.manager.resumeLastSession()
        runCurrent()
        assertTrue(h.engine.playWhenReady)
        assertTrue(h.manager.state.value.isPlaying)
    }

    @Test
    fun `a session saved at the very end restarts the song`() {
        val h = harness(FakePlaybackStore(session = savedSession(position = 199_000)))
        runCurrent()
        h.manager.resumeLastSession()
        runCurrent()
        assertEquals(0L, h.engine.loads.single().second)
    }

    @Test
    fun `playing something before the load finishes hides the saved session`() {
        val h = harness(FakePlaybackStore(session = savedSession()))
        h.manager.playQueue(testSongs(2, firstId = 50), 0)
        runCurrent()
        assertNull(h.manager.restorable.value)
    }

    @Test
    fun `stop keeps the session restorable`() {
        val h = harness()
        h.startPlaying()
        h.engine.currentPosition = 9_000
        h.manager.pause()
        h.manager.stop()
        runCurrent()
        val restorable = h.manager.restorable.value
        assertEquals(1, restorable?.currentSong?.id)
        assertEquals(9_000L, restorable?.positionMs)
        assertEquals(1, h.store.session?.currentSong?.id)
    }

    // --- recently played --------------------------------------------------------------------

    @Test
    fun `recently played is most recent first, distinct and persisted`() {
        val h = harness()
        h.startPlaying(count = 3)
        h.manager.playNext()
        runCurrent()
        h.engine.becomeReady()
        runCurrent()
        h.manager.playQueueItem(0) // song 1 again
        runCurrent()
        h.engine.becomeReady()
        runCurrent()

        assertEquals(listOf(1, 2), h.manager.recentlyPlayed.value.map { it.id })
        assertEquals(listOf(1, 2), h.store.recent.map { it.song.id })
        assertTrue(h.manager.recentlyPlayedEntries.value.all { it.playedAtMs > 0 })
    }

    @Test
    fun `only songs that actually started count as played`() {
        val h = harness()
        h.manager.playQueue(testSongs(3), 0)
        runCurrent()
        h.manager.playNext() // song 1 skipped before it played
        runCurrent()
        assertTrue(h.manager.recentlyPlayed.value.isEmpty())
    }

    @Test
    fun `recently played is capped at 50`() {
        val h = harness()
        val songs = testSongs(60)
        h.manager.playQueue(songs, 0)
        runCurrent()
        h.engine.becomeReady()
        runCurrent()
        repeat(59) {
            h.manager.playNext()
            runCurrent()
            h.engine.becomeReady()
            runCurrent()
        }
        val ids = h.manager.recentlyPlayed.value.map { it.id }
        assertEquals(PlaybackManager.MAX_RECENTLY_PLAYED, ids.size)
        assertEquals((60 downTo 11).toList(), ids)
    }

    @Test
    fun `loaded history merges behind songs played meanwhile`() {
        val stored = listOf(RecentlyPlayedEntry(testSong(1), 1), RecentlyPlayedEntry(testSong(9), 2))
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val h = harness(FakePlaybackStore(recent = stored).apply { loadGate = gate })
        // Song 5 starts playing before the (slow) history load finishes.
        h.manager.playQueue(listOf(testSong(5)), 0)
        runCurrent()
        h.engine.becomeReady()
        runCurrent()
        assertEquals(listOf(5), h.manager.recentlyPlayed.value.map { it.id })
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf(5, 1, 9), h.manager.recentlyPlayed.value.map { it.id })
    }
}
