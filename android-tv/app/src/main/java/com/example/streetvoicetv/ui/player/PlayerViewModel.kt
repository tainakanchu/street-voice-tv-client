package com.example.streetvoicetv.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import com.example.streetvoicetv.playback.PlaybackManager
import com.example.streetvoicetv.playback.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    private val playbackManager: PlaybackManager,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val songId: Int = savedStateHandle.get<Int>("songId") ?: -1

    private val _loadingState = MutableStateFlow(PlayerLoadingState())
    val loadingState: StateFlow<PlayerLoadingState> = _loadingState.asStateFlow()

    private val _isLiked = MutableStateFlow(false)
    val isLiked: StateFlow<Boolean> = _isLiked.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = sessionManager.isLoggedIn

    val playbackState: StateFlow<PlaybackState> = playbackManager.state

    init {
        if (songId > 0) {
            val current = playbackManager.state.value
            if (current.song?.id == songId && current.hasMedia) {
                // 既に同じ曲を再生中ならロードし直さない
                _loadingState.value = PlayerLoadingState(isLoaded = true)
            } else {
                loadSongAndPlay(songId)
            }
        } else {
            _loadingState.value = PlayerLoadingState(error = "Invalid song ID")
        }
    }

    private fun loadSongAndPlay(songId: Int) {
        _loadingState.value = PlayerLoadingState(isLoading = true)

        viewModelScope.launch {
            val songResult = repository.getSongDetail(songId)
            val song = songResult.getOrElse { error ->
                _loadingState.value = PlayerLoadingState(
                    error = "Failed to load song: ${error.message}",
                )
                return@launch
            }

            val streamResult = repository.getStreamUrl(songId)
            val stream = streamResult.getOrElse { error ->
                _loadingState.value = PlayerLoadingState(
                    error = "Failed to load stream: ${error.message}",
                )
                return@launch
            }

            _isLiked.value = song.isLiked
            playbackManager.playCurrentInQueue(song, stream.hlsUrl)
            _loadingState.value = PlayerLoadingState(isLoaded = true)
        }
    }

    fun togglePlayPause() {
        playbackManager.togglePlayPause()
    }

    fun skipNext() {
        playbackManager.playNext()
    }

    fun skipPrevious() {
        playbackManager.playPrevious()
    }

    fun seekTo(positionMs: Long) {
        playbackManager.seekTo(positionMs)
    }

    fun toggleLike() {
        val currentSongId = playbackManager.state.value.song?.id ?: return
        viewModelScope.launch {
            if (_isLiked.value) {
                repository.unlikeSong(currentSongId).onSuccess { _isLiked.value = false }
            } else {
                repository.likeSong(currentSongId).onSuccess { _isLiked.value = true }
            }
        }
    }

    fun retry() {
        if (songId > 0) loadSongAndPlay(songId)
    }
}

data class PlayerLoadingState(
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val error: String? = null,
)
