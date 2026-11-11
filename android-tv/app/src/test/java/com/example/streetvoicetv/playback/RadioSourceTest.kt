package com.example.streetvoicetv.playback

import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RadioSourceTest {

    private val repo = FakeStreetVoiceRepository().apply {
        artistSongsResult = Result.success(testSongs(10, firstId = 1))
        chartResult = Result.success(testSongs(10, firstId = 100) + testSong(1))
    }
    private val source = MixedRadioSource(
        listOf(ArtistRadioSource(repo, Random(1)) to 1, ChartRadioSource(repo, Random(2)) to 1),
        Random(3),
    )

    @Test
    fun mixesArtistAndChart_excludingAndWithoutDuplicates() = runBlocking {
        val ids = source.nextSongs(testSong(1), exclude = setOf(1, 2, 100), count = 10).map { it.id }
        assertEquals(10, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it in setOf(1, 2, 100) })
        assertEquals(5, ids.count { it < 100 })
        assertEquals(5, ids.count { it > 100 })
    }

    @Test
    fun onePartFailing_isFilledFromTheOther() = runBlocking {
        repo.artistSongsResult = Result.failure(java.io.IOException("offline"))
        val ids = source.nextSongs(testSong(1), exclude = emptySet(), count = 6).map { it.id }
        assertEquals(6, ids.size)
        assertTrue(ids.all { it >= 100 || it == 1 })
    }

    @Test
    fun everythingFailing_returnsEmpty() = runBlocking {
        repo.artistSongsResult = Result.failure(java.io.IOException("offline"))
        repo.chartResult = Result.failure(java.io.IOException("offline"))
        assertTrue(source.nextSongs(testSong(1), emptySet(), 10).isEmpty())
    }
}
