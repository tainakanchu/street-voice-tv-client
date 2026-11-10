package com.example.streetvoicetv.ui.search

import com.example.streetvoicetv.ui.common.UserMessages
import android.app.Application
import com.example.streetvoicetv.data.SearchHistoryManager
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
import com.example.streetvoicetv.testutil.MainDispatcherRule
import com.example.streetvoicetv.testutil.lastPage
import com.example.streetvoicetv.domain.model.Page
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
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeStreetVoiceRepository()
    private val history = SearchHistoryManager(RuntimeEnvironment.getApplication())
    private val vm = SearchViewModel(repository, history)

    private val song = Song(1, "稻香", "周杰倫", "jay", null, 200.0, 0)

    @Test
    fun `blank query does nothing`() {
        vm.onQueryChange("   ")
        vm.search()
        assertTrue(repository.calls.isEmpty())
        assertTrue(history.history.value.isEmpty())
    }

    @Test
    fun `search trims query, records history and shows partial results`() {
        repository.searchSongs = { _, _ -> lastPage(listOf(song)) }
        repository.searchArtistsResult = Result.failure(RuntimeException("artists down"))
        repository.searchPlaylistsResult = lastPage(emptyList())

        vm.onQueryChange("  周杰倫 ")
        vm.search()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf(song), state.songs)
        assertTrue(state.hasResults)
        assertNull(state.error) // only fails when all three fail
        assertEquals(listOf("周杰倫"), history.history.value)
        assertTrue("searchSongs(周杰倫)" in repository.calls)
    }

    @Test
    fun `all searches failing surfaces an error`() {
        repository.searchSongs = { _, _ -> Result.failure(RuntimeException("offline")) }
        repository.searchArtistsResult = Result.failure(RuntimeException("offline"))
        repository.searchPlaylistsResult = Result.failure(RuntimeException("offline"))

        vm.onQueryChange("lofi")
        vm.search()
        assertEquals(UserMessages.SEARCH_FAILED, vm.uiState.value.error)

        vm.clearError()
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `songs load more, and a new search drops the pending page of the old one`() {
        val stale = CompletableDeferred<Result<Page<Song>>>()
        val first = List(20) { Song(it, "歌 $it", "a", "a", null, 1.0, 0) }
        repository.searchSongs = { offset, _ ->
            if (offset == 0) Result.success(Page(first, 100, 20)) else stale.await()
        }
        repository.searchArtistsResult = lastPage(emptyList())
        repository.searchPlaylistsResult = lastPage(emptyList())

        vm.onQueryChange("lofi")
        vm.search()
        assertEquals(1, vm.uiState.value.resultsGeneration)
        assertTrue(vm.uiState.value.songsLoadMore.canLoadMore)

        vm.loadMoreSongs()
        assertTrue("searchSongs(lofi, offset=20)" in repository.calls)
        assertTrue(vm.uiState.value.songsLoadMore.isLoadingMore)

        vm.onQueryChange("jazz")
        vm.search()
        stale.complete(Result.success(Page(listOf(Song(999, "stale", "a", "a", null, 1.0, 0)), 100, null)))

        val state = vm.uiState.value
        assertEquals(first, state.songs)
        assertFalse(state.songsLoadMore.isLoadingMore)
        assertEquals(2, state.resultsGeneration)
    }
}
