package com.example.streetvoicetv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            sessionManager.isLoggedIn.drop(1).collect { load() }
        }
    }

    private fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val chartDeferred = async { repository.getRealtimeChart(limit = 10) }
            val editorDeferred = async { repository.getEditorChoice(limit = 10) }
            val playlistsDeferred = async { repository.getRecommendedPlaylists(limit = 20) }

            val currentUsername = sessionManager.username.value
            val myPlaylistsDeferred = if (currentUsername != null) {
                async { repository.getArtistPlaylists(currentUsername) }
            } else null

            // ログイン中かつプロフィール画像未取得なら取得
            if (currentUsername != null && sessionManager.profileImageUrl.value == null) {
                launch {
                    repository.getArtistDetail(currentUsername).onSuccess { artist ->
                        sessionManager.saveProfileImage(artist.profileImageUrl)
                    }
                }
            }

            val chart = chartDeferred.await().getOrDefault(emptyList())
            val editor = editorDeferred.await().getOrDefault(emptyList())
            val playlists = playlistsDeferred.await().getOrDefault(emptyList())
            val myPlaylists = myPlaylistsDeferred?.await()?.getOrDefault(emptyList()) ?: emptyList()

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                chartSongs = chart,
                editorPicks = editor,
                playlists = playlists,
                myPlaylists = myPlaylists,
            )
        }
    }

    fun retry() = load()
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val chartSongs: List<Song> = emptyList(),
    val editorPicks: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val myPlaylists: List<Playlist> = emptyList(),
    val error: String? = null,
)
