package com.example.streetvoicetv.ui.artist

import androidx.lifecycle.SavedStateHandle
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
import com.example.streetvoicetv.testutil.MainDispatcherRule
import com.example.streetvoicetv.ui.common.UserMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ArtistViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeStreetVoiceRepository()
    private val artist = Artist(1, "a", "A", null, null, null, 0, 0, 0)

    @Test
    fun `songs failure is surfaced, albums success is not`() {
        repository.artistDetailResult = Result.success(artist)
        repository.artistSongsResult = Result.failure(RuntimeException("500"))
        repository.artistAlbumsResult = Result.success(emptyList())

        val state = ArtistViewModel(repository, SavedStateHandle(mapOf("username" to "a"))).uiState.value
        assertEquals(artist, state.artist)
        assertEquals(UserMessages.LOAD_FAILED, state.songsError)
        assertNull(state.albumsError)
        assertNull(state.error)
    }
}
