package com.example.streetvoicetv.ui.home

import android.app.Application
import android.content.Context
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
import com.example.streetvoicetv.testutil.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val vm = HomeViewModel(repository, session)
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
        val vm = HomeViewModel(repository, session)
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

        val vm = HomeViewModel(repository, session)
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

        val vm = HomeViewModel(repository, session)
        assertEquals("carol", session.session.value.username)
        assertEquals(listOf(playlist(3, "carol")), vm.uiState.value.myPlaylists)
    }
}
