package com.example.streetvoicetv.ui.mypage

import android.app.Application
import android.content.Context
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
import com.example.streetvoicetv.testutil.MainDispatcherRule
import com.example.streetvoicetv.ui.common.UserMessages
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
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MyPageViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeStreetVoiceRepository()
    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("mypage_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }
    private val session = SessionManager(prefs) {}

    private val artist = Artist(1, "alice", "Alice", null, null, null, 0, 0, 0)
    private val song = Song(1, "歌", "Alice", "alice", null, 100.0, 0)

    @Test
    fun `partial failures are surfaced instead of empty sections`() {
        session.saveSession("sid", "c", "alice")
        repository.artistDetailResult = Result.success(artist)
        repository.likedSongsResult = Result.failure(SocketTimeoutException())
        repository.onArtistPlaylists = { Result.success(emptyList()) }

        val state = MyPageViewModel(repository, session).uiState.value
        assertEquals(artist, state.profile)
        assertEquals(UserMessages.TIMEOUT, state.likedError)
        assertNull(state.playlistsError)
    }

    @Test
    fun `logged in without username refetches it and then loads`() {
        session.saveSession("sid", "c", username = null)
        repository.currentUsernameResult = Result.success("alice")
        repository.artistDetailResult = Result.success(artist)
        repository.likedSongsResult = Result.success(listOf(song))
        repository.onArtistPlaylists = { Result.success(emptyList()) }

        val state = MyPageViewModel(repository, session).uiState.value
        assertEquals("alice", session.session.value.username)
        assertEquals(artist, state.profile)
        assertEquals(listOf(song), state.likedSongs)
        assertFalse(state.needsLogin)
    }

    @Test
    fun `username that cannot be fetched asks to log in again`() {
        session.saveSession("sid", "c", username = null)
        repository.currentUsernameResult = Result.failure(RuntimeException("403"))
        val state = MyPageViewModel(repository, session).uiState.value
        assertTrue(state.needsLogin)
        assertFalse(state.isLoading)
    }

    @Test
    fun `session cleared elsewhere switches to needsLogin`() {
        session.saveSession("sid", "c", "alice")
        repository.artistDetailResult = Result.success(artist)
        repository.likedSongsResult = Result.success(emptyList())
        repository.onArtistPlaylists = { Result.success(emptyList()) }
        val vm = MyPageViewModel(repository, session)
        assertEquals(artist, vm.uiState.value.profile)

        session.clearSession() // e.g. a 401
        assertTrue(vm.uiState.value.needsLogin)
        assertNull(vm.uiState.value.profile)
    }
}
