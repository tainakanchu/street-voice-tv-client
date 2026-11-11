package com.example.streetvoicetv.data.local

import android.app.Application
import com.example.streetvoicetv.playback.QueueSnapshot
import com.example.streetvoicetv.playback.RecentlyPlayedEntry
import com.example.streetvoicetv.playback.RestorableSession
import com.example.streetvoicetv.playback.testSong
import com.example.streetvoicetv.playback.testSongs
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

// Robolectric only for android.util.Log on failures.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class JsonFilePlaybackStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dir: File get() = File(tmp.root, "playback")
    private fun store() = JsonFilePlaybackStore({ dir }, Dispatchers.Unconfined)

    private val session = RestorableSession(
        queue = QueueSnapshot(
            songs = testSongs(3).map { it.copy(albumName = "專輯", imageUrl = "https://img/${it.id}", lyrics = "很長的歌詞") },
            currentIndex = 1,
            shuffleEnabled = true,
            shuffleOrder = listOf(1, 2, 0),
            shufflePosition = 0,
            autoplayFlags = listOf(false, false, true),
        ),
        positionMs = 61_234,
        savedAtMs = 1_700_000_000_000,
    )

    @Test
    fun session_roundTrip_withoutLyrics() = runBlocking {
        store().saveSession(session)
        val loaded = store().loadSession()!!
        assertEquals(session.positionMs, loaded.positionMs)
        assertEquals(session.savedAtMs, loaded.savedAtMs)
        assertEquals(session.queue.copy(songs = session.songs.map { it.copy(lyrics = null) }), loaded.queue)
        assertEquals("專輯", loaded.currentSong.albumName)
        assertNull("lyrics are refetched, not stored", loaded.currentSong.lyrics)
    }

    @Test
    fun recent_roundTrip_keepsOrderAndTimes() = runBlocking {
        val entries = listOf(RecentlyPlayedEntry(testSong(5), 20), RecentlyPlayedEntry(testSong(2), 10))
        store().saveRecentlyPlayed(entries)
        assertEquals(entries, store().loadRecentlyPlayed())
    }

    @Test
    fun missingOrCorruptFiles_loadAsNothing() = runBlocking {
        assertNull(store().loadSession())
        assertTrue(store().loadRecentlyPlayed().isEmpty())
        dir.mkdirs()
        File(dir, "playback_session.json").writeText("{not json")
        File(dir, "recently_played.json").writeText("[]")
        assertNull(store().loadSession())
        assertTrue(store().loadRecentlyPlayed().isEmpty())
    }

    @Test
    fun overwrite_replacesAndLeavesNoTempFile() = runBlocking {
        store().saveSession(session)
        store().saveSession(session.copy(positionMs = 5))
        assertEquals(5L, store().loadSession()?.positionMs)
        assertEquals(listOf("playback_session.json"), dir.list()!!.toList())
    }
}
