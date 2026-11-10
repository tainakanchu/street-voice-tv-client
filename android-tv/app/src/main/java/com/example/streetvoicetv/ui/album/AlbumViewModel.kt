package com.example.streetvoicetv.ui.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.domain.model.Album
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
class AlbumViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val albumId: Int = savedStateHandle.get<Int>("albumId") ?: -1

    private val _uiState = MutableStateFlow(AlbumUiState())
    val uiState: StateFlow<AlbumUiState> = _uiState.asStateFlow()

    private val songs = PagedListLoader(
        scope = viewModelScope,
        tag = TAG,
        key = Song::id,
        fetch = { offset, limit -> repository.getAlbumSongs(albumId, limit, offset) },
        onChange = { items, loadMore -> _uiState.update { it.copy(songs = items, loadMore = loadMore) } },
    )
    private var loadJob: Job? = null

    init {
        if (albumId > 0) {
            load()
        } else {
            _uiState.value = AlbumUiState(error = UserMessages.NOT_FOUND)
        }
    }

    private fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null, playAllPending = null) }

        loadJob = viewModelScope.launch {
            val detailDeferred = async { repository.getAlbumDetail(albumId) }
            val songsDeferred = async { songs.fetchFirst() }

            val detailResult = detailDeferred.await()
            val songsResult = songsDeferred.await()

            detailResult
                .onSuccess { album ->
                    songs.reset(songsResult.getOrNull())
                    _uiState.update { it.copy(isLoading = false, album = album) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = userMessageFor(TAG, error)) }
                }
        }
    }

    fun retry() {
        if (albumId > 0) load()
    }

    fun loadMore() = songs.loadMore()

    fun retryLoadMore() = songs.retryLoadMore()

    /**
     * Loads the remaining songs (up to [PLAY_ALL_CAP]) and hands them to [play].
     * Repeated presses while loading are ignored.
     */
    fun playAll(mode: PlayAllMode, play: (List<Song>, PlayAllMode) -> Unit) {
        if (_uiState.value.playAllPending != null) return
        _uiState.update { it.copy(playAllPending = mode) }
        songs.loadUpTo(PLAY_ALL_CAP) { loaded ->
            _uiState.update { it.copy(playAllPending = null) }
            if (loaded.isNotEmpty()) play(loaded, mode)
        }
    }

    private companion object {
        const val TAG = "AlbumViewModel"
    }
}

data class AlbumUiState(
    val isLoading: Boolean = false,
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
    val loadMore: LoadMoreState = LoadMoreState(),
    /** Set while 播放全部 / 隨機播放 waits for the remaining pages. */
    val playAllPending: PlayAllMode? = null,
    val error: String? = null,
)
