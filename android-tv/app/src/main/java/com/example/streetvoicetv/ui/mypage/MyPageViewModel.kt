package com.example.streetvoicetv.ui.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.data.auth.SessionState
import com.example.streetvoicetv.domain.model.Artist
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
class MyPageViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyPageUiState(isLoading = true))
    val uiState: StateFlow<MyPageUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            // Reacts to the username becoming available (or the session being cleared,
            // e.g. after a 401). StateFlow is already distinct.
            sessionManager.session.collect { load(it) }
        }
    }

    private fun load(session: SessionState = sessionManager.session.value) {
        loadJob?.cancel()
        val username = session.username
        when {
            !session.isLoggedIn -> {
                _uiState.value = MyPageUiState(needsLogin = true)
                return
            }
            username == null -> {
                // Logged in but the username is unknown: fetch it; on success the session
                // update calls load() again with the username.
                _uiState.value = _uiState.value.copy(isLoading = true, needsLogin = false)
                loadJob = viewModelScope.launch {
                    repository.getCurrentUsername()
                        .onSuccess { sessionManager.updateUsername(it) }
                        .onFailure {
                            userMessageFor(TAG, it)
                            _uiState.value = MyPageUiState(needsLogin = true)
                        }
                }
                return
            }
        }

        val name: String = username ?: return
        _uiState.value = _uiState.value.copy(isLoading = true, needsLogin = false)
        loadJob = viewModelScope.launch {
            val profileDeferred = async { repository.getArtistDetail(name) }
            val likedDeferred = async { repository.getLikedSongs(name) }
            val playlistsDeferred = async { repository.getArtistPlaylists(name) }

            val profileResult = profileDeferred.await()
            val likedResult = likedDeferred.await()
            val playlistsResult = playlistsDeferred.await()

            _uiState.value = MyPageUiState(
                isLoading = false,
                profile = profileResult.getOrNull(),
                likedSongs = likedResult.getOrDefault(emptyList()),
                playlists = playlistsResult.getOrDefault(emptyList()),
                // Partial failures are shown inline instead of as an empty section.
                likedError = likedResult.exceptionOrNull()?.let { userMessageFor(TAG, it) },
                playlistsError = playlistsResult.exceptionOrNull()?.let { userMessageFor(TAG, it) },
            )
        }
    }

    fun logout() {
        loadJob?.cancel()
        sessionManager.clearSession()
    }

    fun retry() = load()

    private companion object {
        const val TAG = "MyPageViewModel"
    }
}

data class MyPageUiState(
    val isLoading: Boolean = false,
    val profile: Artist? = null,
    val likedSongs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val likedError: String? = null,
    val playlistsError: String? = null,
    /** The session is gone or its user could not be determined: offer 重新登入. */
    val needsLogin: Boolean = false,
)
