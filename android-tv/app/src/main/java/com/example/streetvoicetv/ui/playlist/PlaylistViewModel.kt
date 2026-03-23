package com.example.streetvoicetv.ui.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.domain.model.Playlist
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
class PlaylistViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val playlistId: Int = savedStateHandle.get<Int>("playlistId") ?: -1

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    init {
        if (playlistId > 0) {
            load()
        } else {
            _uiState.value = PlaylistUiState(error = "Invalid playlist ID")
        }
    }

    private fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val detailDeferred = async { repository.getPlaylistDetail(playlistId) }
            val songsDeferred = async { repository.getPlaylistSongs(playlistId) }

            val detailResult = detailDeferred.await()
            val songsResult = songsDeferred.await()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                playlist = detailResult.getOrNull(),
                songs = songsResult.getOrDefault(emptyList()),
                error = if (detailResult.isFailure && songsResult.isFailure)
                    detailResult.exceptionOrNull()?.message ?: "Failed to load playlist"
                else null,
            )
        }
    }

    fun retry() {
        if (playlistId > 0) load()
    }
}

data class PlaylistUiState(
    val isLoading: Boolean = false,
    val playlist: Playlist? = null,
    val songs: List<Song> = emptyList(),
    val error: String? = null,
)
