package com.example.streetvoicetv.ui.album

import androidx.lifecycle.SavedStateHandle
import com.example.streetvoicetv.domain.model.Album
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
import com.example.streetvoicetv.ui.common.UserMessages
import com.example.streetvoicetv.testutil.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AlbumViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeStreetVoiceRepository()

    private val album = Album(1, "專輯", null, 2, "音樂人", "artist", null)
    private val songs = listOf(
        Song(10, "歌 1", "音樂人", "artist", null, 120.0, 0),
        Song(11, "歌 2", "音樂人", "artist", null, 130.0, 0),
    )

    @Test
    fun `loads album and songs`() {
        repository.albumDetailResult = Result.success(album)
        repository.albumSongsResult = Result.success(songs)

        val vm = AlbumViewModel(repository, SavedStateHandle(mapOf("albumId" to 1)))

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(album, state.album)
        assertEquals(songs, state.songs)
        assertNull(state.error)
    }

    @Test
    fun `detail failure surfaces error and retry recovers`() {
        repository.albumDetailResult = Result.failure(RuntimeException("boom"))
        repository.albumSongsResult = Result.success(songs)

        val vm = AlbumViewModel(repository, SavedStateHandle(mapOf("albumId" to 1)))
        assertEquals(UserMessages.LOAD_FAILED, vm.uiState.value.error)
        assertNull(vm.uiState.value.album)

        repository.albumDetailResult = Result.success(album)
        vm.retry()
        assertEquals(album, vm.uiState.value.album)
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun `invalid id does not hit repository`() {
        val vm = AlbumViewModel(repository, SavedStateHandle())
        assertEquals(UserMessages.NOT_FOUND, vm.uiState.value.error)
        assertTrue(repository.calls.isEmpty())
    }
}
