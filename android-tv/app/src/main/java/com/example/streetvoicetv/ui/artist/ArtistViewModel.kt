package com.example.streetvoicetv.ui.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.domain.model.Album
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import com.example.streetvoicetv.ui.common.LoadMoreState
import com.example.streetvoicetv.ui.common.PLAY_ALL_CAP
import com.example.streetvoicetv.ui.common.PagedListLoader
import com.example.streetvoicetv.ui.common.PlayAllMode
import com.example.streetvoicetv.ui.common.UserMessages
import com.example.streetvoicetv.ui.common.userMessageFor
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
class ArtistViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val username: String = savedStateHandle.get<String>("username") ?: ""

    private val _uiState = MutableStateFlow(ArtistUiState())
    val uiState: StateFlow<ArtistUiState> = _uiState.asStateFlow()

    private val songs = PagedListLoader(
        scope = viewModelScope,
        tag = TAG,
        key = Song::id,
        fetch = { offset, limit -> repository.getArtistSongs(username, limit, offset) },
        onChange = { items, loadMore -> _uiState.update { it.copy(songs = items, songsLoadMore = loadMore) } },
    )
    private val albums = PagedListLoader(
        scope = viewModelScope,
        tag = TAG,
        key = Album::id,
        fetch = { offset, limit -> repository.getArtistAlbums(username, limit, offset) },
        onChange = { items, loadMore -> _uiState.update { it.copy(albums = items, albumsLoadMore = loadMore) } },
    )
    private var loadJob: Job? = null

    init {
        if (username.isNotBlank()) {
            load()
        } else {
            _uiState.value = ArtistUiState(error = UserMessages.NOT_FOUND)
        }
    }

    private fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null, playAllPending = null) }

        loadJob = viewModelScope.launch {
            val detailDeferred = async { repository.getArtistDetail(username) }
            val songsDeferred = async { songs.fetchFirst() }
            val albumsDeferred = async { albums.fetchFirst() }

            val detailResult = detailDeferred.await()
            val songsResult = songsDeferred.await()
            val albumsResult = albumsDeferred.await()

            detailResult
                .onSuccess { artist ->
                    songs.reset(songsResult.getOrNull())
                    albums.reset(albumsResult.getOrNull())
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            artist = artist,
                            // Partial failures are shown inline instead of as an empty list.
                            songsError = songsResult.exceptionOrNull()?.let { e -> userMessageFor(TAG, e) },
                            albumsError = albumsResult.exceptionOrNull()?.let { e -> userMessageFor(TAG, e) },
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = userMessageFor(TAG, error)) }
                }
        }
    }

    fun retry() {
        if (username.isNotBlank()) load()
    }

    fun selectTab(tab: ArtistTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun loadMoreSongs() = songs.loadMore()

    fun retryLoadMoreSongs() = songs.retryLoadMore()

    fun loadMoreAlbums() = albums.loadMore()

    fun retryLoadMoreAlbums() = albums.retryLoadMore()

    /** Loads the remaining songs (up to [PLAY_ALL_CAP]) and hands them to [play]. */
    fun playAll(mode: PlayAllMode, play: (List<Song>, PlayAllMode) -> Unit) {
        if (_uiState.value.playAllPending != null) return
        _uiState.update { it.copy(playAllPending = mode) }
        songs.loadUpTo(PLAY_ALL_CAP) { loaded ->
            _uiState.update { it.copy(playAllPending = null) }
            if (loaded.isNotEmpty()) play(loaded, mode)
        }
    }

    private companion object {
        const val TAG = "ArtistViewModel"
    }
}

enum class ArtistTab { Songs, Albums }

data class ArtistUiState(
    val isLoading: Boolean = false,
    val artist: Artist? = null,
    val songs: List<Song> = emptyList(),
    val songsLoadMore: LoadMoreState = LoadMoreState(),
    val albums: List<Album> = emptyList(),
    val albumsLoadMore: LoadMoreState = LoadMoreState(),
    val songsError: String? = null,
    val albumsError: String? = null,
    /** Set while 播放全部 / 隨機播放 waits for the remaining songs. */
    val playAllPending: PlayAllMode? = null,
    val selectedTab: ArtistTab = ArtistTab.Songs,
    val error: String? = null,
)
