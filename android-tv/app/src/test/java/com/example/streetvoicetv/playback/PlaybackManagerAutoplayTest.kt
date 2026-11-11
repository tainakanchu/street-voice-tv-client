package com.example.streetvoicetv.playback

import android.app.Application
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 自動播放: appending songs when the queue runs out. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class PlaybackManagerAutoplayTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = InMemorySettings(autoplayEnabled = true)
    private val radio = FakeRadio(pool = testSongs(20, firstId = 100))
    private val h = ManagerHarness(dispatcher, settings = settings, radio = radio)
    private val manager get() = h.manager
    private val state get() = manager.state.value

    private fun runCurrent() = dispatcher.scheduler.runCurrent()

    private fun play(songs: List<Song>, start: Int = 0) {
        manager.playQueue(songs, start)
        runCurrent()
        h.engine.becomeReady()
        runCurrent()
    }

    private fun upcomingIds(): List<Int> {
        val v = manager.upNext.value
        return v.items.drop(v.currentPosition + 1).map { it.song.id }
    }

    @Test
    fun `prefetches once two songs remain and appends ten`() {
        play(testSongs(4))
        assertTrue("4 songs left: no fetch yet", radio.requests.isEmpty())
        manager.playNext() // 3 left
        runCurrent()
        assertTrue(radio.requests.isEmpty())
        manager.playNext() // 2 left (current + 1)
        runCurrent()
        assertEquals(1, radio.requests.size)
        assertEquals(3, radio.requests.single().first)
        assertEquals(14, state.queueSize)
        val view = manager.upNext.value
        assertEquals(PlaybackManager.AUTOPLAY_BATCH, view.items.count { it.isAutoplay })
        assertEquals(view.currentPosition + 2, view.autoplayDividerPosition)
        assertTrue(view.autoplayEnabled)
        assertEquals(4, upcomingIds().first())
    }

    @Test
    fun `when the queue ends it continues with autoplay songs`() {
        radio.gate = CompletableDeferred()
        play(listOf(testSong(1)))
        assertEquals("single song: fetched at once", 1, radio.requests.size)
        assertTrue("hasNext reflects autoplay", state.hasNext)

        h.engine.end()
        runCurrent()
        assertEquals(1, state.song?.id)

        radio.gate!!.complete(Unit)
        runCurrent()
        assertEquals(100, state.song?.id)
        assertEquals(listOf(1, 100), h.streams.requests)
        assertTrue(state.isPlaying)
    }

    @Test
    fun `ending without a prefetched batch fetches and continues`() {
        play(listOf(testSong(1)))
        // Prefetch done at once: 10 songs appended; play through them.
        assertEquals(11, state.queueSize)
        repeat(10) {
            h.engine.end()
            runCurrent()
            h.engine.becomeReady()
            runCurrent()
        }
        // Each step near the end fetches another batch, so playback never stops.
        assertTrue(state.queueSize > 11)
        assertTrue(state.isPlaying)
        assertTrue(radio.requests.size >= 2)
    }

    @Test
    fun `excludes queued and already played songs`() {
        play(listOf(testSong(100)))
        // Song 100 played; now a different queue.
        play(testSongs(2, firstId = 101))
        val exclude = radio.requests.last().second
        assertTrue(100 in exclude)
        assertTrue(101 in exclude && 102 in exclude)
        val appended = manager.upNext.value.items.filter { it.isAutoplay }.map { it.song.id }
        assertTrue(appended.none { it in setOf(100, 101, 102) })
        assertEquals(appended.size, appended.toSet().size)
    }

    @Test
    fun `skip at the end waits for the fetch and then advances`() {
        radio.gate = CompletableDeferred()
        play(listOf(testSong(1)))
        manager.playNext()
        runCurrent()
        assertEquals(1, state.song?.id)
        radio.gate!!.complete(Unit)
        runCurrent()
        assertEquals(100, state.song?.id)
    }

    @Test
    fun `setting off stops at the end`() {
        manager.setAutoplayEnabled(false)
        assertFalse(settings.autoplayEnabled)
        play(testSongs(2))
        manager.playNext()
        runCurrent()
        h.engine.becomeReady()
        runCurrent()
        assertFalse(state.hasNext)
        h.engine.end()
        runCurrent()
        assertEquals(2, state.song?.id)
        assertTrue(radio.requests.isEmpty())
        assertFalse(manager.upNext.value.autoplayEnabled)
    }

    @Test
    fun `turning it off drops upcoming autoplay songs`() {
        play(listOf(testSong(1)))
        assertEquals(11, state.queueSize)
        manager.toggleAutoplay()
        assertEquals(1, state.queueSize)
        assertFalse(state.hasNext)
        manager.toggleAutoplay()
        runCurrent()
        assertTrue(manager.autoplayEnabled.value)
        assertEquals(11, state.queueSize)
    }

    @Test
    fun `repeat all never autoplays`() {
        manager.toggleRepeatMode() // ALL
        play(testSongs(1))
        assertTrue(radio.requests.isEmpty())
    }

    @Test
    fun `nothing found - hasNext turns false and the queue stops at its end`() {
        radio.pool = emptyList()
        play(listOf(testSong(1)))
        assertEquals(1, radio.requests.size)
        assertFalse(state.hasNext)
        h.engine.end()
        runCurrent()
        assertEquals("no retry loop for the same seed", 1, radio.requests.size)
        assertEquals(1, state.song?.id)
    }

    @Test
    fun `a fetch that fails is treated as nothing found`() {
        val failing = ManagerHarness(
            dispatcher,
            settings = InMemorySettings(autoplayEnabled = true),
            radio = object : FakeRadio() {
                override suspend fun nextSongs(seed: Song, exclude: Set<Int>, count: Int): List<Song> =
                    throw java.io.IOException("offline")
            },
        )
        failing.manager.playQueue(listOf(testSong(1)), 0)
        runCurrent()
        assertFalse(failing.manager.state.value.hasNext)
    }

    @Test
    fun `replacing the queue cancels a pending fetch`() {
        radio.gate = CompletableDeferred()
        play(listOf(testSong(1)))
        play(testSongs(5, firstId = 50))
        radio.gate!!.complete(Unit)
        runCurrent()
        assertEquals(5, state.queueSize)
        assertEquals(50, state.song?.id)
    }
}
