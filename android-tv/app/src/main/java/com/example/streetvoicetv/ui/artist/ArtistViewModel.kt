package com.example.streetvoicetv.ui.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.domain.model.Album
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val username: String = savedStateHandle.get<String>("username") ?: ""

    private val _uiState = MutableStateFlow(ArtistUiState())
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    init {
        if (username.isNotBlank()) {
            load()
        } else {
            _uiState.value = ArtistUiState(error = "Invalid artist")
        }
    }

    private fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val detailDeferred = async { repository.getArtistDetail(username) }
            val songsDeferred = async { repository.getArtistSongs(username) }
            val albumsDeferred = async { repository.getArtistAlbums(username) }

            val detailResult = detailDeferred.await()
            val songsResult = songsDeferred.await()
            val albumsResult = albumsDeferred.await()

            detailResult
                .onSuccess { artist ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        artist = artist,
                        songs = songsResult.getOrDefault(emptyList()),
                        albums = albumsResult.getOrDefault(emptyList()),
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load artist: ${error.message}",
                    )
                }
        }
    }

    fun retry() {
        if (username.isNotBlank()) load()
    }

    fun selectTab(tab: ArtistTab) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
    }
}

enum class ArtistTab { Songs, Albums }

data class ArtistUiState(
    val isLoading: Boolean = false,
    val artist: Artist? = null,
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val selectedTab: ArtistTab = ArtistTab.Songs,
    val error: String? = null,
)
