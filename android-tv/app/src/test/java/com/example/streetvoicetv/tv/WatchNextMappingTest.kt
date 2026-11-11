package com.example.streetvoicetv.tv

import android.app.Application
import androidx.tvprovider.media.tv.TvContractCompat
import com.example.streetvoicetv.playback.QueueSnapshot
import com.example.streetvoicetv.playback.RestorableSession
import com.example.streetvoicetv.playback.testSong
import com.example.streetvoicetv.playback.testSongs
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class WatchNextMappingTest {

    private val song = testSong(7).copy(
        name = "夜車",
        artistName = "草東",
        imageUrl = "https://img.example/7.jpg",
        durationSeconds = 200.0,
    )

    @Test
    fun `spec carries title, artist, art, position and duration`() {
        val spec = WatchNextMapping.specOf(song, positionMs = 42_000, durationMs = 199_000, nowMs = 1_000)
        assertEquals(WatchNextSpec(7, "夜車", "草東", "https://img.example/7.jpg", 42_000, 199_000, 1_000), spec)
    }

    @Test
    fun `duration falls back to the song length and position is clamped`() {
        val spec = WatchNextMapping.specOf(song, positionMs = 999_000, durationMs = 0, nowMs = 0)
        assertEquals(200_000L, spec.durationMs)
        assertEquals(200_000L, spec.positionMs)
        assertEquals(0L, WatchNextMapping.specOf(song, -5, 0, 0).positionMs)
    }

    @Test
    fun `poster falls back to album art`() {
        val noArt = song.copy(imageUrl = null, albumImageUrl = "https://img.example/album.jpg")
        assertEquals("https://img.example/album.jpg", WatchNextMapping.specOf(noArt, 0, 0, 0).posterUrl)
        assertNull(WatchNextMapping.specOf(song.copy(imageUrl = null), 0, 0, 0).posterUrl)
    }

    @Test
    fun `target prefers the loaded song, then the saved session, else removal`() {
        val session = RestorableSession(QueueSnapshot(testSongs(3), currentIndex = 1, shuffleEnabled = false, shuffleOrder = emptyList(), shufflePosition = -1, autoplayFlags = emptyList()), positionMs = 5_000, savedAtMs = 77)

        val playing = WatchNextMapping.targetOf(song, 1_000, 200_000, session, nowMs = 99)
        assertEquals(7, (playing as WatchNextTarget.Publish).spec.songId)
        assertEquals(99L, playing.spec.lastEngagementMs)

        val saved = WatchNextMapping.targetOf(null, 0, 0, session, nowMs = 99) as WatchNextTarget.Publish
        assertEquals(2, saved.spec.songId)
        assertEquals(5_000L, saved.spec.positionMs)
        assertEquals("engagement = when the session was saved", 77L, saved.spec.lastEngagementMs)

        assertEquals(WatchNextTarget.Remove, WatchNextMapping.targetOf(null, 0, 0, null, nowMs = 99))
    }

    @Test
    fun `program is a continue-watching track with the resume deep link`() {
        val spec = WatchNextMapping.specOf(song, positionMs = 42_000, durationMs = 199_000, nowMs = 1_234)
        val program = WatchNextMapping.buildProgram(spec)

        assertEquals(TvContractCompat.WatchNextPrograms.TYPE_TRACK, program.type)
        assertEquals(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE, program.watchNextType)
        assertEquals("夜車", program.title)
        assertEquals("草東", program.description)
        assertEquals("草東", program.author)
        assertEquals("https://img.example/7.jpg", program.posterArtUri.toString())
        assertEquals(TvContractCompat.PreviewPrograms.ASPECT_RATIO_1_1, program.posterArtAspectRatio)
        assertEquals(42_000, program.lastPlaybackPositionMillis)
        assertEquals(199_000, program.durationMillis)
        assertEquals(1_234L, program.lastEngagementTimeUtcMillis)
        assertEquals("streetvoicetv://resume", program.intentUri.toString())
        assertEquals("7", program.internalProviderId)
        // Writable as provider values.
        assertEquals("夜車", program.toContentValues().getAsString(TvContractCompat.WatchNextPrograms.COLUMN_TITLE))
    }

    @Test
    fun `the deep link of the program parses as the resume action`() {
        val program = WatchNextMapping.buildProgram(WatchNextMapping.specOf(song, 0, 0, 0))
        assertEquals(
            LaunchAction.ResumeLastSession,
            LaunchIntents.parse(android.content.Intent.ACTION_VIEW, program.intentUri, null),
        )
    }
}
