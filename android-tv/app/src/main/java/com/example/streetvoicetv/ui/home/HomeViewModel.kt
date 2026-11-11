package com.example.streetvoicetv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.data.auth.SessionState
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import com.example.streetvoicetv.ui.common.userMessageFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** The running load; a new load (retry, login / logout) cancels it. */
    private var loadJob: Job? = null

    /** Whose playlists [HomeUiState.myPlaylists] holds. */
    private var myPlaylistsOwner: String? = null

    init {
        viewModelScope.launch {
            // StateFlow is already distinct: this reloads on login, logout and when the
            // username becomes available (e.g. fetched after login), and loads initially.
            sessionManager.session.collect { session -> load(session) }
        }
    }

    private fun load(session: SessionState = sessionManager.session.value) {
        loadJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
            // Never show the previous user's playlists while (re)loading.
            myPlaylists = if (session.username == myPlaylistsOwner) _uiState.value.myPlaylists else emptyList(),
        )

        loadJob = viewModelScope.launch {
            val currentUsername = session.username

            // Logged in but the username is unknown (e.g. it could not be fetched at login):
            // fetch it; the session update triggers another load with the username.
            if (session.isLoggedIn && currentUsername == null) {
                launch {
                    repository.getCurrentUsername().onSuccess { sessionManager.updateUsername(it) }
                }
            }

            val chartDeferred = async { repository.getRealtimeChart(limit = 10) }
            val editorDeferred = async { repository.getEditorChoice(limit = 10) }
            val playlistsDeferred = async { repository.getRecommendedPlaylists(limit = 20) }

            val myPlaylistsDeferred = if (currentUsername != null) {
                async { repository.getArtistPlaylists(currentUsername) }
            } else null

            // ログイン中かつプロフィール画像未取得なら取得
            if (currentUsername != null && sessionManager.profileImageUrl.value == null) {
                launch {
                    repository.getArtistDetail(currentUsername).onSuccess { artist ->
                        // Still the same user? (A logout may have happened meanwhile.)
                        if (sessionManager.session.value.username == currentUsername) {
                            sessionManager.saveProfileImage(artist.profileImageUrl)
                        }
                    }
                }
            }

            val chartResult = chartDeferred.await()
            val editorResult = editorDeferred.await()
            val playlistsResult = playlistsDeferred.await()
            val myPlaylistsResult = myPlaylistsDeferred?.await()

            val chart = chartResult.getOrDefault(emptyList())
            val editor = editorResult.getOrDefault(emptyList())
            val playlists = playlistsResult.getOrDefault(emptyList())
            val myPlaylists = myPlaylistsResult?.getOrDefault(emptyList()) ?: emptyList()

            // Only surface an error when nothing at all could be shown.
            val nothingLoaded = chart.isEmpty() && editor.isEmpty() && playlists.isEmpty() && myPlaylists.isEmpty()
            val firstFailure = listOfNotNull(chartResult, editorResult, playlistsResult, myPlaylistsResult)
                .firstNotNullOfOrNull { it.exceptionOrNull() }
            val error = if (nothingLoaded && firstFailure != null) userMessageFor(TAG, firstFailure) else null

            myPlaylistsOwner = currentUsername
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                chartSongs = chart,
                editorPicks = editor,
                playlists = playlists,
                myPlaylists = myPlaylists,
                error = error,
            )
        }
    }

    fun retry() = load()

    private companion object {
        const val TAG = "HomeViewModel"
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val chartSongs: List<Song> = emptyList(),
    val editorPicks: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val myPlaylists: List<Playlist> = emptyList(),
    val error: String? = null,
)
