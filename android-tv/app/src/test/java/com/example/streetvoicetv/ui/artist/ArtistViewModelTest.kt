package com.example.streetvoicetv.ui.artist

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.data.repository.FollowRepository
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.Page
import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
import com.example.streetvoicetv.testutil.MainDispatcherRule
import com.example.streetvoicetv.testutil.pagesOf
import com.example.streetvoicetv.ui.common.UserMessages
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
class ArtistViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeStreetVoiceRepository().apply {
        artistSongsResult = Result.success(emptyList())
        artistAlbumsResult = Result.success(emptyList())
        following = { _, _, _ -> Result.success(Page(emptyList(), 0, null)) }
    }
    private val artist = Artist(1, "a", "A", null, null, null, 10, 0, 0)
    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("artist_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }
    private val session = SessionManager(prefs) {}
    private val followRepository = FollowRepository(repository, session)

    private fun artistOf(name: String) = Artist(name.hashCode(), name, name.uppercase(), null, null, null, 0, 0, 0)

    private fun viewModel() = ArtistViewModel(repository, followRepository, session, SavedStateHandle(mapOf("username" to "a")))

    @Test
    fun `songs failure is surfaced, albums success is not`() {
        repository.artistDetailResult = Result.success(artist)
        repository.artistSongsResult = Result.failure(RuntimeException("500"))

        val state = viewModel().uiState.value
        assertEquals(artist, state.artist)
        assertEquals(UserMessages.LOAD_FAILED, state.songsError)
        assertNull(state.albumsError)
        assertNull(state.error)
    }

    @Test
    fun `following tab lists who the artist follows, paged`() {
        repository.artistDetailResult = Result.success(artist)
        val all = (1..45).map { artistOf("f$it") }
        val pages = pagesOf(all)
        repository.following = { user, offset, limit -> if (user == "a") pages(offset, limit) else Result.success(Page(emptyList(), 0, null)) }

        val vm = viewModel()
        assertEquals(30, vm.uiState.value.following.size)
        assertEquals(45, vm.uiState.value.followingLoadMore.total)
        vm.loadMoreFollowing()
        assertEquals(all, vm.uiState.value.following)
        assertTrue(vm.uiState.value.followingLoadMore.endReached)
    }

    @Test
    fun `logged out - follow button asks for login and sends nothing`() {
        repository.artistDetailResult = Result.success(artist)
        val vm = viewModel()
        assertFalse(vm.uiState.value.follow.isLoggedIn)

        var loginRequested = 0
        vm.toggleFollow { loginRequested++ }
        assertEquals(1, loginRequested)
        assertFalse(repository.calls.any { it.startsWith("followArtist") })
    }

    @Test
    fun `logged in - follow state comes from my following list`() {
        session.saveSession("sid", "csrf", "me")
        repository.artistDetailResult = Result.success(artist.copy(isFollowing = false))
        repository.following = { user, _, _ ->
            Result.success(Page(if (user == "me") listOf(artistOf("a")) else emptyList(), null, null))
        }
        val state = viewModel().uiState.value
        assertTrue(state.follow.isLoggedIn)
        assertTrue(state.follow.isFollowing)
    }

    @Test
    fun `own artist page has no follow button`() {
        session.saveSession("sid", "csrf", "a")
        repository.artistDetailResult = Result.success(artist)
        assertTrue(viewModel().uiState.value.follow.isSelf)
    }

    @Test
    fun `follow is optimistic and ignores presses while in flight`() {
        session.saveSession("sid", "csrf", "me")
        repository.artistDetailResult = Result.success(artist)
        val response = CompletableDeferred<Result<Unit>>()
        repository.onFollow = { _, _ -> response.await() }
        val vm = viewModel()

        vm.toggleFollow {}
        val pending = vm.uiState.value
        assertTrue(pending.follow.isFollowing)
        assertTrue(pending.follow.inFlight)
        assertEquals(11, pending.artist!!.followersCount)

        vm.toggleFollow {} // ignored
        assertEquals(1, repository.calls.count { it.startsWith("followArtist") || it.startsWith("unfollowArtist") })

        response.complete(Result.success(Unit))
        val done = vm.uiState.value
        assertTrue(done.follow.isFollowing)
        assertFalse(done.follow.inFlight)
        assertEquals(true, followRepository.isFollowing("a"))
    }

    @Test
    fun `failed unfollow reverts and shows a message`() {
        session.saveSession("sid", "csrf", "me")
        repository.artistDetailResult = Result.success(artist)
        repository.following = { user, _, _ ->
            Result.success(Page(if (user == "me") listOf(artistOf("a")) else emptyList(), null, null))
        }
        repository.onFollow = { _, _ -> Result.failure(RuntimeException("403")) }
        val vm = viewModel()
        assertTrue(vm.uiState.value.follow.isFollowing)

        vm.toggleFollow {}
        val state = vm.uiState.value
        assertEquals(listOf("unfollowArtist(a)"), repository.calls.filter { it.contains("ollowArtist") })
        assertTrue(state.follow.isFollowing)
        assertFalse(state.follow.inFlight)
        assertEquals(UserMessages.FOLLOW_FAILED, state.follow.error)
        assertEquals(10, state.artist!!.followersCount)
        assertEquals(true, followRepository.isFollowing("a"))
    }
}
