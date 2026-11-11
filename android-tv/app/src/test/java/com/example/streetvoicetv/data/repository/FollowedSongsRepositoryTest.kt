package com.example.streetvoicetv.data.repository

import android.app.Application
import android.content.Context
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.Page
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class FollowedSongsRepositoryTest {

    private val repository = FakeStreetVoiceRepository()
    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("followed_songs_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }
    private val session = SessionManager(prefs) {}
    private val follows = FollowRepository(repository, session)
    private val followedSongs = FollowedSongsRepository(repository, follows)

    private fun artist(name: String) = Artist(name.hashCode(), name, name, null, null, null, 0, 0, 0)

    private fun song(id: Int, user: String, publishAt: String?) =
        Song(id, "s$id", user, user, null, 100.0, 0, publishAt = publishAt)

    private fun follow(vararg names: String) {
        repository.following = { user, _, _ ->
            Result.success(Page(if (user == "me") names.map(::artist) else emptyList(), names.size, null))
        }
    }

    @Test
    fun `merges newest first across artists, top 20`() = runTest {
        session.saveSession("sid", "csrf", "me")
        follow("a", "b")
        repository.onArtistSongs = { user ->
            Result.success(
                when (user) {
                    "a" -> listOf(song(1, "a", "2026-11-01T10:00:00+0800"), song(2, "a", "2026-06-01T10:00:00+0800"))
                    else -> listOf(song(3, "b", "2026-11-05T10:00:00+0800"), song(4, "b", "2025-01-01T00:00:00Z"))
                },
            )
        }
        val result = followedSongs.load().getOrThrow()
        assertEquals(listOf(3, 1, 2, 4), result.songs.map { it.id })
    }

    @Test
    fun `undated songs are interleaved per artist and sorted after dated ones`() {
        val a = listOf(song(1, "a", null), song(2, "a", null))
        val b = listOf(song(3, "b", null), song(4, "b", "2026-01-01T00:00:00+0800"))
        assertEquals(listOf(4, 1, 3, 2), mergeNewestFirst(listOf(a, b), 20).map { it.id })
        assertEquals(2, mergeNewestFirst(listOf(a, b), 2).size)
    }

    @Test
    fun `at most 10 artists, 4 requests at a time`() = runTest {
        session.saveSession("sid", "csrf", "me")
        follow(*Array(12) { "u$it" })
        var running = 0
        var maxRunning = 0
        repository.onArtistSongs = { user ->
            running++
            maxRunning = maxOf(maxRunning, running)
            delay(1_000)
            running--
            Result.success(listOf(song(user.removePrefix("u").toInt(), user, null)))
        }
        val result = followedSongs.load().getOrThrow()

        assertEquals(4, maxRunning)
        assertEquals(10, repository.calls.count { it.startsWith("getArtistSongs") })
        assertEquals(3_000, currentTime) // ceil(10 / 4) waves
        assertEquals(10, result.songs.size)
    }

    @Test
    fun `partial failures still return the rest, total failure is an error`() = runTest {
        session.saveSession("sid", "csrf", "me")
        follow("a", "b")
        repository.onArtistSongs = { user ->
            if (user == "a") Result.failure(RuntimeException("500")) else Result.success(listOf(song(3, "b", null)))
        }
        assertEquals(listOf(3), followedSongs.load().getOrThrow().songs.map { it.id })

        repository.onArtistSongs = { Result.failure(RuntimeException("500")) }
        assertTrue(followedSongs.load(forceRefresh = true).isFailure)
    }

    @Test
    fun `nobody followed - empty, no song requests`() = runTest {
        session.saveSession("sid", "csrf", "me")
        follow()
        assertTrue(followedSongs.load().getOrThrow().songs.isEmpty())
        assertEquals(0, repository.calls.count { it.startsWith("getArtistSongs") })
    }

    @Test
    fun `logged out - fails without requests`() = runTest {
        assertTrue(followedSongs.load().exceptionOrNull() is NotLoggedInException)
        assertTrue(repository.calls.isEmpty())
    }

    @Test
    fun `cached for 10 minutes, per user, and invalidated by a follow`() = runTest {
        session.saveSession("sid", "csrf", "me")
        follow("a")
        repository.onArtistSongs = { Result.success(listOf(song(1, "a", null))) }
        var now = 1_000_000L
        followedSongs.now = { now }

        followedSongs.load()
        followedSongs.load()
        assertEquals(1, repository.calls.count { it.startsWith("getArtistSongs") })

        now += 9 * 60_000
        followedSongs.load()
        assertEquals(1, repository.calls.count { it.startsWith("getArtistSongs") })

        now += 2 * 60_000
        assertEquals(null, followedSongs.fresh())
        followedSongs.load()
        assertEquals(2, repository.calls.count { it.startsWith("getArtistSongs") })

        follows.setFollowing(artist("b"), follow = true)
        assertEquals(null, followedSongs.fresh())

        session.saveSession("sid2", "csrf", "other")
        assertEquals(null, followedSongs.fresh())
    }
}
