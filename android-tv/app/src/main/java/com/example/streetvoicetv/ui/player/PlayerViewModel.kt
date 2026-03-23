package com.example.streetvoicetv.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val songId: Int = savedStateHandle.get<Int>("songId") ?: -1

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        if (songId > 0) {
            loadSongAndStream(songId)
        } else {
            _uiState.value = PlayerUiState(error = "Invalid song ID")
        }
    }

    private fun loadSongAndStream(songId: Int) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            // Fetch song details
            val songResult = repository.getSongDetail(songId)
            songResult
                .onSuccess { song ->
                    _uiState.value = _uiState.value.copy(song = song)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load song details: ${error.message}",
                    )
                    return@launch
                }

            // Fetch stream URL
            val streamResult = repository.getStreamUrl(songId)
            streamResult
                .onSuccess { stream ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        streamUrl = stream.hlsUrl,
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load stream: ${error.message}",
                    )
                }
        }
    }

    fun retry() {
        if (songId > 0) {
            loadSongAndStream(songId)
        }
    }

    fun onPlaybackError(message: String) {
        _uiState.value = _uiState.value.copy(error = "Playback error: $message")
    }
}

data class PlayerUiState(
    val isLoading: Boolean = false,
    val song: Song? = null,
    val streamUrl: String? = null,
    val error: String? = null,
)
