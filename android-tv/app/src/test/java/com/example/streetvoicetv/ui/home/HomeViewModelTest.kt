package com.example.streetvoicetv.ui.home

import android.app.Application
import android.content.Context
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.data.repository.FollowRepository
import com.example.streetvoicetv.data.repository.FollowedSongsRepository
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.ChartCategory
import com.example.streetvoicetv.domain.model.ChartPeriod
import com.example.streetvoicetv.domain.model.Page
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.parsePublishAt
import com.example.streetvoicetv.ui.common.UserMessages
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.playback.RecentlyPlayedEntry
import com.example.streetvoicetv.testutil.FakePlaybackActions
import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
import com.example.streetvoicetv.testutil.restorableSession
import com.example.streetvoicetv.testutil.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeStreetVoiceRepository().apply {
        chartResult = Result.success(listOf(Song(1, "歌", "人", "u", null, 100.0, 0)))
        editorChoiceResult = Result.success(emptyList())
        recommendedPlaylistsResult = Result.success(emptyList())
        artistDetailResult = Result.failure(RuntimeException("no profile"))
    }
    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("home_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }
    private val session = SessionManager(prefs) {}

    private fun playlist(id: Int, owner: String) = Playlist(id = id, name = "$owner list", imageUrl = null, songsCount = 1, curatorName = owner)

    @Test
    fun `logout during a load cancels it so the previous user's playlists never show`() {
        session.saveSession("sid", "csrf", "alice")
        val alicePlaylists = CompletableDeferred<Result<List<Playlist>>>()
        var cancelled = false
        repository.onArtistPlaylists = {
            try {
                alicePlaylists.await()
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }

        val vm = HomeViewModel(repository, session, FollowedSongsRepository(repository, FollowRepository(repository, session)))
        assertTrue(vm.uiState.value.isLoading)

        session.clearSession()
        assertTrue(cancelled)
        alicePlaylists.complete(Result.success(listOf(playlist(1, "alice"))))

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.myPlaylists.isEmpty())
        assertEquals(1, state.chartSongs.size)
    }

    @Test
    fun `retry cancels the running load`() {
        val firstChart = CompletableDeferred<Result<List<Song>>>()
        var calls = 0
        repository.onChart = {
            calls++
            if (calls == 1) firstChart.await() else Result.success(emptyList())
        }
        val vm = HomeViewModel(repository, session, FollowedSongsRepository(repository, FollowRepository(repository, session)))
        vm.retry()
        firstChart.complete(Result.success(listOf(Song(9, "stale", "x", "x", null, 1.0, 0))))
        assertTrue(vm.uiState.value.chartSongs.isEmpty())
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `username arriving after login reloads my playlists`() {
        repository.onArtistPlaylists = { owner -> Result.success(listOf(playlist(2, owner))) }
        repository.currentUsernameResult = Result.failure(RuntimeException("offline"))
        session.saveSession("sid", "csrf", username = null)

        val vm = HomeViewModel(repository, session, FollowedSongsRepository(repository, FollowRepository(repository, session)))
        assertTrue(vm.uiState.value.myPlaylists.isEmpty())
        assertTrue("getCurrentUsername" in repository.calls)

        session.updateUsername("bob")
        assertEquals(listOf(playlist(2, "bob")), vm.uiState.value.myPlaylists)
    }

    @Test
    fun `missing username is fetched and stored`() {
        repository.onArtistPlaylists = { owner -> Result.success(listOf(playlist(3, owner))) }
        repository.currentUsernameResult = Result.success("carol")
        session.saveSession("sid", "csrf", username = null)

        val vm = HomeViewModel(repository, session, FollowedSongsRepository(repository, FollowRepository(repository, session)))
        assertEquals("carol", session.session.value.username)
        assertEquals(listOf(playlist(3, "carol")), vm.uiState.value.myPlaylists)
    }

    // --- 本週排行 / 曲風 / 追蹤音樂人的新歌 ---

    private val followedSongs = FollowedSongsRepository(repository, FollowRepository(repository, session))

    private fun homeVm() = HomeViewModel(repository, session, followedSongs)

    private fun followA() {
        repository.onArtistPlaylists = { Result.success(emptyList()) }
        repository.following = { user, _, _ ->
            Result.success(Page(if (user == "me") listOf(Artist(1, "a", "A", null, null, null, 0, 0, 0)) else emptyList(), 1, null))
        }
    }

    @Test
    fun `weekly chart and genre chips load with the other rows`() {
        repository.onChartPage = { slug, period, _, limit ->
            Result.success(Page(if (slug == "all" && period == ChartPeriod.Weekly) List(limit) { Song(100 + it, "w", "a", "a", null, 1.0, 0) } else emptyList(), null, null))
        }
        val state = homeVm().uiState.value
        assertEquals(20, state.weeklySongs.size)
        assertEquals(ChartCategory.forPeriod(ChartPeriod.Realtime), state.genres)
        assertEquals(null, state.followedNew)
    }

    @Test
    fun `followed artists' new songs - logged in, with release age`() {
        session.saveSession("sid", "csrf", "me")
        followA()
        repository.onArtistSongs = {
            Result.success(listOf(Song(5, "new", "A", "a", null, 1.0, 0, publishAt = "2026-11-03T18:18:42+0800")))
        }
        followedSongs.now = { parsePublishAt("2026-11-03T18:18:42+0800")!! + 3 * 86_400_000L }

        val row = homeVm().uiState.value.followedNew!!
        assertEquals(listOf(5), row.queue.map { it.id })
        assertEquals("3 天前", row.songs.single().ageLabel)
        assertNull(row.error)
    }

    @Test
    fun `followed row - hidden when nothing is followed, error with retry when all fail`() {
        session.saveSession("sid", "csrf", "me")
        repository.onArtistPlaylists = { Result.success(emptyList()) }
        repository.following = { _, _, _ -> Result.success(Page(emptyList(), 0, null)) }
        assertNull(homeVm().uiState.value.followedNew)

        followA()
        repository.onArtistSongs = { Result.failure(RuntimeException("500")) }
        // A new session-level cache (the empty result above is cached).
        val vm = HomeViewModel(repository, session, FollowedSongsRepository(repository, FollowRepository(repository, session)))
        assertEquals(UserMessages.LOAD_FAILED, vm.uiState.value.followedNew!!.error)

        repository.onArtistSongs = { Result.success(listOf(Song(5, "new", "A", "a", null, 1.0, 0))) }
        vm.retryFollowed()
        assertEquals(listOf(5), vm.uiState.value.followedNew!!.queue.map { it.id })
        assertNull(vm.uiState.value.followedNew!!.error)
    }

    @Test
    fun `followed row is not blocked by a slow main row`() {
        session.saveSession("sid", "csrf", "me")
        followA()
        repository.onArtistSongs = { Result.success(listOf(Song(5, "new", "A", "a", null, 1.0, 0))) }
        val slowChart = CompletableDeferred<Result<List<Song>>>()
        repository.onChart = { slowChart.await() }

        val vm = homeVm()
        assertTrue(vm.uiState.value.isLoading)
        assertEquals(listOf(5), vm.uiState.value.followedNew!!.queue.map { it.id })
        slowChart.complete(Result.success(emptyList()))
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `followed row is cached and refreshed on resume only when stale`() {
        session.saveSession("sid", "csrf", "me")
        followA()
        repository.onArtistSongs = { Result.success(listOf(Song(5, "new", "A", "a", null, 1.0, 0))) }
        var now = 0L
        followedSongs.now = { now }
        val vm = homeVm()
        fun songRequests() = repository.calls.count { it.startsWith("getArtistSongs") }
        assertEquals(1, songRequests())

        vm.onResume()
        assertEquals(1, songRequests())
        now += 11 * 60_000
        vm.onResume()
        assertEquals(2, songRequests())

        // Logout hides the row.
        session.clearSession()
        assertNull(vm.uiState.value.followedNew)
    }

    // --- 繼續播放 / 最近播放 ---

    private fun song(id: Int, durationSeconds: Double = 200.0) = Song(id, "歌 $id", "人", "u", null, durationSeconds, 0)

    @Test
    fun `continue card - shown for a restorable session, with progress and queue size`() {
        val playback = FakePlaybackActions(restorableSession(listOf(song(1), song(2), song(3)), currentIndex = 1, positionMs = 50_000))
        val vm = HomeViewModel(repository, session, followedSongs, playback)

        val card = vm.uiState.value.continueListening!!
        assertEquals(2, card.song.id)
        assertEquals(50_000L, card.positionMs)
        assertEquals(3, card.queueSize)
        assertEquals(0.25f, card.progress, 0.001f)
    }

    @Test
    fun `continue card - absent without a session, appears when the session loads late`() {
        val playback = FakePlaybackActions()
        val vm = HomeViewModel(repository, session, followedSongs, playback)
        assertNull(vm.uiState.value.continueListening)

        playback.restorable.value = restorableSession(listOf(song(7)))
        assertEquals(7, vm.uiState.value.continueListening!!.song.id)
        // Unknown duration: no progress rather than a division by zero.
        playback.restorable.value = restorableSession(listOf(song(8, durationSeconds = 0.0)), positionMs = 10_000)
        assertEquals(0f, vm.uiState.value.continueListening!!.progress)
    }

    @Test
    fun `resume restores the session, returns the song to open and hides the card`() {
        val playback = FakePlaybackActions(restorableSession(listOf(song(1), song(2)), currentIndex = 1))
        val vm = HomeViewModel(repository, session, followedSongs, playback)

        assertEquals(2, vm.resumeLastSession())
        assertEquals(listOf("resumeLastSession(play=true)"), playback.calls)
        assertNull(vm.uiState.value.continueListening)
        // Nothing left to resume.
        assertNull(vm.resumeLastSession())
        assertEquals(1, playback.calls.size)
    }

    @Test
    fun `recently played row - most recent first with played-at age, capped at 20`() {
        val now = 10 * 86_400_000L
        val entries = List(25) { RecentlyPlayedEntry(song(100 + it), now - (it + 1) * 5 * 60_000L) }
        val playback = FakePlaybackActions(recent = entries)
        val vm = HomeViewModel(repository, session, followedSongs, playback, clock = { now })

        val row = vm.uiState.value.recentlyPlayed
        assertEquals(20, row.size)
        assertEquals((100 until 120).toList(), vm.uiState.value.recentlyPlayedQueue.map { it.id })
        assertEquals("5 分鐘前", row[0].ageLabel)
        assertEquals("10 分鐘前", row[1].ageLabel)
    }

    @Test
    fun `recently played row - empty without history, labels refresh on resume`() {
        var now = 0L
        val playback = FakePlaybackActions()
        val vm = HomeViewModel(repository, session, followedSongs, playback, clock = { now })
        assertTrue(vm.uiState.value.recentlyPlayed.isEmpty())

        playback.recentlyPlayedEntries.value = listOf(RecentlyPlayedEntry(song(1), playedAtMs = 0L))
        assertEquals("剛剛", vm.uiState.value.recentlyPlayed.single().ageLabel)
        now = 3 * 3_600_000L
        vm.onResume()
        assertEquals("3 小時前", vm.uiState.value.recentlyPlayed.single().ageLabel)
    }
}
