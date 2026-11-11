package com.example.streetvoicetv.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import com.example.streetvoicetv.playback.PlaybackManager
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.playback.QueueView
import com.example.streetvoicetv.ui.common.UserMessages
import com.example.streetvoicetv.ui.common.userMessageFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    private val playbackManager: PlaybackManager,
    sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val songId: Int = savedStateHandle.get<Int>("songId") ?: -1

    private val _loadingState = MutableStateFlow(PlayerLoadingState())
    val loadingState: StateFlow<PlayerLoadingState> = _loadingState.asStateFlow()

    /** Like status known for one song id; never applied to another song. */
    private data class LikeState(val songId: Int, val liked: Boolean)

    private val likeState = MutableStateFlow<LikeState?>(null)
    private var likeJob: Job? = null

    private val currentSongId = playbackManager.state.map { it.song?.id }.distinctUntilChanged()

    val isLiked: StateFlow<Boolean> = combine(likeState, currentSongId) { like, id ->
        like != null && like.songId == id && like.liked
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isDetailLoading = MutableStateFlow(false)

    /** Full song details (lyrics / synopsis / like) are being fetched for the current song. */
    val isDetailLoading: StateFlow<Boolean> = _isDetailLoading.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = sessionManager.isLoggedIn

    val playbackState: StateFlow<PlaybackState> = playbackManager.state

    /** The play queue in play order (does not change with the position). */
    val upNext: StateFlow<QueueView> = playbackManager.upNext

    /** Live position; collect only where progress is drawn. */
    val positionMs: StateFlow<Long> = playbackManager.positionMs

    /** Detail already fetched by [loadSongAndPlay], so the song-change collector need not refetch it. */
    private var prefetched: Song? = null
    private var loadJob: Job? = null

    init {
        if (songId > 0) {
            val current = playbackManager.state.value
            if (current.song?.id == songId) {
                // Already the current song (just queued from a list, or reopened): don't reload,
                // but recover it if it is in an error state.
                _loadingState.value = PlayerLoadingState(isLoaded = true)
                if (current.error != null) playbackManager.retry()
            } else {
                loadSongAndPlay(songId)
            }
        } else {
            _loadingState.value = PlayerLoadingState(error = UserMessages.NOT_FOUND)
        }

        // Fetch details (lyrics, like status) whenever the current song changes, A -> B -> A included.
        // collectLatest cancels the fetch for a song that is no longer current.
        viewModelScope.launch {
            currentSongId.collectLatest { id ->
                if (id == null) return@collectLatest
                val detail = prefetched?.takeIf { it.id == id } ?: run {
                    _isDetailLoading.value = true
                    try {
                        repository.getSongDetail(id).getOrNull()
                    } finally {
                        _isDetailLoading.value = false
                    }
                }
                currentCoroutineContext().ensureActive()
                prefetched = null
                if (detail != null) {
                    likeState.value = LikeState(id, detail.isLiked)
                    playbackManager.updateSongDetail(detail)
                }
            }
        }
    }

    private fun loadSongAndPlay(songId: Int) {
        _loadingState.value = PlayerLoadingState(isLoading = true)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // The stream URL is fetched by PlaybackManager; song details are needed first here
            // only because nothing is queued for this id.
            repository.getSongDetail(songId)
                .onSuccess { song ->
                    prefetched = song
                    likeState.value = LikeState(song.id, song.isLiked)
                    playbackManager.playQueue(listOf(song), 0)
                    _loadingState.value = PlayerLoadingState(isLoaded = true)
                }
                .onFailure { error ->
                    _loadingState.value = PlayerLoadingState(
                        error = userMessageFor(TAG, error, UserMessages.PLAYBACK_FAILED),
                    )
                }
        }
    }

    fun togglePlayPause() = playbackManager.togglePlayPause()

    fun skipNext() = playbackManager.playNext()

    fun skipPrevious() = playbackManager.playPrevious()

    /** Jumps to a song of the queue panel ([com.example.streetvoicetv.playback.QueueItem.queueIndex]). */
    fun playQueueItem(queueIndex: Int) = playbackManager.playQueueItem(queueIndex)

    fun seekBy(deltaMs: Long) = playbackManager.seekBy(deltaMs)

    fun handleMediaKey(keyCode: Int): Boolean = playbackManager.handleMediaKey(keyCode)

    fun toggleShuffle() = playbackManager.toggleShuffle()

    fun toggleRepeatMode() = playbackManager.toggleRepeatMode()

    /** Optimistic like toggle for the current song; ignored while a request is in flight. */
    fun toggleLike() {
        if (likeJob?.isActive == true) return
        val id = playbackManager.state.value.song?.id ?: return
        val known = likeState.value?.takeIf { it.songId == id } ?: return // status not loaded yet
        val liked = !known.liked
        likeState.value = LikeState(id, liked)
        likeJob = viewModelScope.launch {
            val result = if (liked) repository.likeSong(id) else repository.unlikeSong(id)
            result.onFailure {
                if (likeState.value?.songId == id) likeState.value = LikeState(id, !liked)
            }
        }
    }

    /** Retry of the initial song load (loading error screen). */
    fun retry() {
        if (songId > 0) loadSongAndPlay(songId)
    }

    /** Retry of a playback error (fresh stream URL, resumes at the last position). */
    fun retryPlayback() = playbackManager.retry()

    private companion object {
        const val TAG = "PlayerViewModel"
    }
}

data class PlayerLoadingState(
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val error: String? = null,
)
