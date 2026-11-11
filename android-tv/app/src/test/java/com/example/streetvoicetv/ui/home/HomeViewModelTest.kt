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
import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
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
}
