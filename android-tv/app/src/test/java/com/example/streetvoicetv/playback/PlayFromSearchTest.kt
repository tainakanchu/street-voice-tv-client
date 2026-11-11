package com.example.streetvoicetv.playback

import androidx.media3.common.MediaItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PlayFromSearchTest {

    private val played = mutableListOf<Pair<List<Int>, Int>>()
    private var searched = mutableListOf<String>()

    private fun subject(
        results: List<com.example.streetvoicetv.domain.model.Song> = testSongs(3),
        fallback: List<com.example.streetvoicetv.domain.model.Song> = testSongs(2, firstId = 100),
        canResume: Boolean = false,
        failSearch: Boolean = false,
    ) = PlayFromSearch(
        search = { q ->
            searched += q
            if (failSearch) throw IOException("offline")
            results
        },
        fallback = { fallback },
        playQueue = { songs, index -> played += songs.map { it.id } to index },
        resumeLastSession = { canResume },
    )

    @Test
    fun `plays the results from the first one`() = runTest {
        assertTrue(subject().play("  告五人 "))
        assertEquals(listOf("告五人"), searched)
        assertEquals(listOf(listOf(1, 2, 3) to 0), played)
    }

    @Test
    fun `nothing found or a failed search plays nothing`() = runTest {
        assertFalse(subject(results = emptyList()).play("zzz"))
        assertFalse(subject(failSearch = true).play("zzz"))
        assertEquals(emptyList<Any>(), played)
    }

    @Test
    fun `empty query resumes the last session, else plays the fallback`() = runTest {
        assertTrue(subject(canResume = true).play(""))
        assertEquals(emptyList<Any>(), played)
        assertTrue(subject(canResume = false).play(" "))
        assertEquals(listOf(listOf(100, 101) to 0), played)
        assertTrue("no search for an empty query", searched.isEmpty())
    }

    @Test
    fun `placeholder items are recognised`() {
        assertTrue(PlayFromSearch.isHandled(listOf(PlayFromSearch.handledItem)))
        assertFalse(PlayFromSearch.isHandled(emptyList()))
        assertFalse(PlayFromSearch.isHandled(listOf(MediaItem.Builder().setMediaId("x").build())))
    }
}
