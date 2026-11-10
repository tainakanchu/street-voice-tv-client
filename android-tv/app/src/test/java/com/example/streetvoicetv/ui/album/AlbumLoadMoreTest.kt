package com.example.streetvoicetv.ui.album

import androidx.lifecycle.SavedStateHandle
import com.example.streetvoicetv.domain.model.Album
import com.example.streetvoicetv.domain.model.Page
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.ui.common.PLAY_ALL_CAP
import com.example.streetvoicetv.ui.common.PlayAllMode
import com.example.streetvoicetv.ui.common.UserMessages
import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
import com.example.streetvoicetv.testutil.MainDispatcherRule
import com.example.streetvoicetv.testutil.pagesOf
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** Load-more behaviour of a paginated detail list (shared [PagedListLoader] logic). */
class AlbumLoadMoreTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeStreetVoiceRepository().apply {
        albumDetailResult = Result.success(Album(1, "專輯", null, 0, "音樂人", "artist", null))
    }

    private fun song(id: Int) = Song(id, "歌 $id", "音樂人", "artist", null, 120.0, 0)
    private fun songs(ids: IntRange) = ids.map(::song)
    private fun page(ids: IntRange, next: Int?) = Result.success(Page(songs(ids), 100, next))

    private fun viewModel() = AlbumViewModel(repository, SavedStateHandle(mapOf("albumId" to 1)))

    private val AlbumViewModel.ids get() = uiState.value.songs.map { it.id }

    @Test
    fun `load more appends the next page, dedupes and reaches the end`() {
        repository.albumSongs = { offset, _ ->
            when (offset) {
                0 -> page(1..30, next = 30)
                // The list shifted on the server: the second page repeats songs 29 and 30.
                30 -> page(29..45, next = null)
                else -> error("unexpected offset $offset")
            }
        }
        val vm = viewModel()
        assertEquals((1..30).toList(), vm.ids)
        assertFalse(vm.uiState.value.loadMore.endReached)
        assertTrue(vm.uiState.value.loadMore.canLoadMore)

        vm.loadMore()

        assertEquals((1..45).toList(), vm.ids)
        val loadMore = vm.uiState.value.loadMore
        assertTrue(loadMore.endReached)
        assertFalse(loadMore.isLoadingMore)
        assertNull(loadMore.error)

        // Nothing more to load: no further request.
        vm.loadMore()
        assertEquals(listOf("getAlbumSongs(1, offset=0)", "getAlbumSongs(1, offset=30)"),
            repository.calls.filter { it.startsWith("getAlbumSongs") })
    }

    @Test
    fun `only one load-more runs at a time`() {
        val second = CompletableDeferred<Result<Page<Song>>>()
        repository.albumSongs = { offset, _ -> if (offset == 0) page(1..30, next = 30) else second.await() }
        val vm = viewModel()

        vm.loadMore()
        assertTrue(vm.uiState.value.loadMore.isLoadingMore)
        vm.loadMore()
        vm.loadMore()
        assertEquals(1, repository.calls.count { it == "getAlbumSongs(1, offset=30)" })

        second.complete(page(31..40, next = 40))
        assertFalse(vm.uiState.value.loadMore.isLoadingMore)
        assertEquals((1..40).toList(), vm.ids)
    }

    @Test
    fun `failed load-more shows an error, is not retried automatically, and retry recovers`() {
        var failSecond = true
        repository.albumSongs = { offset, _ ->
            when {
                offset == 0 -> page(1..30, next = 30)
                failSecond -> Result.failure(IOException("offline"))
                else -> page(31..35, next = null)
            }
        }
        val vm = viewModel()

        vm.loadMore()
        assertEquals(UserMessages.NETWORK, vm.uiState.value.loadMore.error)
        assertFalse(vm.uiState.value.loadMore.isLoadingMore)
        assertFalse(vm.uiState.value.loadMore.canLoadMore)
        assertEquals((1..30).toList(), vm.ids) // loaded items are kept

        // The scroll trigger keeps firing: it must not hammer the server while the error shows.
        vm.loadMore()
        assertEquals(1, repository.calls.count { it == "getAlbumSongs(1, offset=30)" })

        failSecond = false
        vm.retryLoadMore()
        assertNull(vm.uiState.value.loadMore.error)
        assertTrue(vm.uiState.value.loadMore.endReached)
        assertEquals((1..35).toList(), vm.ids)
    }

    @Test
    fun `reload cancels a pending load-more so its page is never appended`() {
        val stale = CompletableDeferred<Result<Page<Song>>>()
        var firstPage = 1..30
        repository.albumSongs = { offset, _ -> if (offset == 0) page(firstPage, next = 30) else stale.await() }
        val vm = viewModel()

        vm.loadMore()
        assertTrue(vm.uiState.value.loadMore.isLoadingMore)

        firstPage = 101..130
        vm.retry()
        stale.complete(page(31..60, next = null))

        assertEquals((101..130).toList(), vm.ids)
        assertFalse(vm.uiState.value.loadMore.isLoadingMore)
        assertFalse(vm.uiState.value.loadMore.endReached)
    }

    @Test
    fun `play all loads the remaining pages up to the cap`() {
        repository.albumSongs = pagesOf(songs(1..250))
        val vm = viewModel()
        var played: Pair<List<Song>, PlayAllMode>? = null

        vm.playAll(PlayAllMode.Shuffle) { list, mode -> played = list to mode }

        val (list, mode) = played!!
        assertEquals(PlayAllMode.Shuffle, mode)
        assertEquals((1..PLAY_ALL_CAP).toList(), list.map { it.id })
        assertNull(vm.uiState.value.playAllPending)
        assertFalse(vm.uiState.value.loadMore.endReached) // more than the cap exists
    }

    @Test
    fun `play all of a fully loaded list starts at once`() {
        repository.albumSongs = pagesOf(songs(1..12))
        val vm = viewModel()
        var played: List<Song>? = null

        vm.playAll(PlayAllMode.InOrder) { list, _ -> played = list }

        assertEquals((1..12).toList(), played!!.map { it.id })
        assertEquals(1, repository.calls.count { it.startsWith("getAlbumSongs") })
    }

    @Test
    fun `play all shows pending state while loading and ignores repeated presses`() {
        val second = CompletableDeferred<Result<Page<Song>>>()
        repository.albumSongs = { offset, _ -> if (offset == 0) page(1..30, next = 30) else second.await() }
        val vm = viewModel()
        val played = mutableListOf<List<Song>>()

        vm.playAll(PlayAllMode.InOrder) { list, _ -> played += list }
        assertEquals(PlayAllMode.InOrder, vm.uiState.value.playAllPending)
        vm.playAll(PlayAllMode.Shuffle) { list, _ -> played += list }
        assertTrue(played.isEmpty())

        // A failing page still plays what is loaded.
        second.complete(Result.failure(IOException("offline")))
        assertEquals(1, played.size)
        assertEquals((1..30).toList(), played[0].map { it.id })
        assertNull(vm.uiState.value.playAllPending)
        assertEquals(UserMessages.NETWORK, vm.uiState.value.loadMore.error)
    }
}
