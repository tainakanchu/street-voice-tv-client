package com.example.streetvoicetv.ui.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.domain.model.Album
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
class AlbumViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val albumId: Int = savedStateHandle.get<Int>("albumId") ?: -1

    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    init {
        if (albumId > 0) {
            load()
        } else {
            _uiState.value = AlbumUiState(error = "Invalid album ID")
        }
    }

    private fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val detailDeferred = async { repository.getAlbumDetail(albumId) }
            val songsDeferred = async { repository.getAlbumSongs(albumId) }

            val detailResult = detailDeferred.await()
            val songsResult = songsDeferred.await()

            detailResult
                .onSuccess { album ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        album = album,
                        songs = songsResult.getOrDefault(emptyList()),
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load album: ${error.message}",
                    )
                }
        }
    }

    fun retry() {
        if (albumId > 0) load()
    }
}

data class AlbumUiState(
    val isLoading: Boolean = false,
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val error: String? = null,
)
