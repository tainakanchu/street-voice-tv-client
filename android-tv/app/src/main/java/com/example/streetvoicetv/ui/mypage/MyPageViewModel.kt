package com.example.streetvoicetv.ui.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.domain.model.Artist
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
class MyPageViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyPageUiState())
    val uiState: StateFlow<MyPageUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        val username = sessionManager.username.value ?: return
        _uiState.value = _uiState.value.copy(isLoading = true)

        viewModelScope.launch {
            val profileDeferred = async { repository.getArtistDetail(username) }
            val likedDeferred = async { repository.getLikedSongs(username) }
            val playlistsDeferred = async { repository.getArtistPlaylists(username) }

            val profile = profileDeferred.await().getOrNull()
            val likedSongs = likedDeferred.await().getOrDefault(emptyList())
            val playlists = playlistsDeferred.await().getOrDefault(emptyList())

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                profile = profile,
                likedSongs = likedSongs,
                playlists = playlists,
            )
        }
    }

    fun logout() {
        sessionManager.clearSession()
    }

    fun retry() = load()
}

data class MyPageUiState(
    val isLoading: Boolean = false,
    val profile: Artist? = null,
    val likedSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
)
