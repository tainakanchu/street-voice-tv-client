package com.example.streetvoicetv.ui.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import com.example.streetvoicetv.ui.common.LoadMoreState
import com.example.streetvoicetv.ui.common.PLAY_ALL_CAP
import com.example.streetvoicetv.ui.common.PagedListLoader
import com.example.streetvoicetv.ui.common.PlayAllMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyPageViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyPageUiState())
    val uiState: StateFlow<MyPageUiState> = _uiState.asStateFlow()

    private var username: String? = null
    private val likedSongs = PagedListLoader(
        scope = viewModelScope,
        tag = TAG,
        key = Song::id,
        fetch = fetch@{ offset, limit ->
            val user = username ?: return@fetch Result.failure(IllegalStateException("not logged in"))
            repository.getLikedSongs(user, limit, offset)
        },
        onChange = { items, loadMore -> _uiState.update { it.copy(likedSongs = items, likedLoadMore = loadMore) } },
    )
    private var loadJob: Job? = null

    init {
        load()
    }

    private fun load() {
        val user = sessionManager.username.value ?: return
        username = user
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, playAllPending = null) }

        loadJob = viewModelScope.launch {
            val profileDeferred = async { repository.getArtistDetail(user) }
            val likedDeferred = async { likedSongs.fetchFirst() }
            val playlistsDeferred = async { repository.getArtistPlaylists(user) }

            val profile = profileDeferred.await().getOrNull()
            val liked = likedDeferred.await().getOrNull()
            val playlists = playlistsDeferred.await().getOrDefault(emptyList())

            likedSongs.reset(liked)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    profile = profile,
                    playlists = playlists,
                )
            }
        }
    }

    fun logout() {
        sessionManager.clearSession()
    }

    fun retry() = load()

    fun loadMoreLiked() = likedSongs.loadMore()

    fun retryLoadMoreLiked() = likedSongs.retryLoadMore()

    /** Loads the remaining liked songs (up to [PLAY_ALL_CAP]) and hands them to [play]. */
    fun playAllLiked(mode: PlayAllMode, play: (List<Song>, PlayAllMode) -> Unit) {
        if (_uiState.value.playAllPending != null) return
        _uiState.update { it.copy(playAllPending = mode) }
        likedSongs.loadUpTo(PLAY_ALL_CAP) { loaded ->
            _uiState.update { it.copy(playAllPending = null) }
            if (loaded.isNotEmpty()) play(loaded, mode)
        }
    }

    private companion object {
        const val TAG = "MyPageViewModel"
    }
}

data class MyPageUiState(
    val isLoading: Boolean = false,
    val profile: Artist? = null,
    val likedSongs: List<Song> = emptyList(),
    val likedLoadMore: LoadMoreState = LoadMoreState(),
    /** Set while 播放全部 / 隨機播放 waits for the remaining liked songs. */
    val playAllPending: PlayAllMode? = null,
    val playlists: List<Playlist> = emptyList(),
)
