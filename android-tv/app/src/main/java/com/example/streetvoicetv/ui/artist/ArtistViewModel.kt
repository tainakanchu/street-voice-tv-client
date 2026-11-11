package com.example.streetvoicetv.ui.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.data.repository.FollowRepository
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
    private val followRepository: FollowRepository,
    private val sessionManager: SessionManager,
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
    private val following = PagedListLoader(
        scope = viewModelScope,
        tag = TAG,
        key = Artist::username,
        fetch = { offset, limit -> repository.getFollowing(username, limit, offset) },
        onChange = { items, loadMore -> _uiState.update { it.copy(following = items, followingLoadMore = loadMore) } },
    )
    private var loadJob: Job? = null
    private var followStateJob: Job? = null
    private var followStateUser: String? = null

    init {
        if (username.isNotBlank()) {
            load()
            viewModelScope.launch {
                // Login / logout (e.g. back from the login screen opened by 追蹤): re-derive.
                sessionManager.session.collect { refreshFollowState() }
            }
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
            val followingDeferred = async { following.fetchFirst() }

            val detailResult = detailDeferred.await()
            val songsResult = songsDeferred.await()
            val albumsResult = albumsDeferred.await()
            val followingResult = followingDeferred.await()

            detailResult
                .onSuccess { artist ->
                    songs.reset(songsResult.getOrNull())
                    albums.reset(albumsResult.getOrNull())
                    following.reset(followingResult.getOrNull())
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            artist = artist,
                            // Partial failures are shown inline instead of as an empty list.
                            songsError = songsResult.exceptionOrNull()?.let { e -> userMessageFor(TAG, e) },
                            albumsError = albumsResult.exceptionOrNull()?.let { e -> userMessageFor(TAG, e) },
                            followingError = followingResult.exceptionOrNull()?.let { e -> userMessageFor(TAG, e) },
                        )
                    }
                    refreshFollowState()
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

    /**
     * Follow state of the 追蹤 button: the server's `is_follow` of the detail (only meaningful
     * with a session) until the logged-in user's follow list is known, then that list.
     * Never overrides a toggle in flight.
     */
    private fun refreshFollowState() {
        val me = followRepository.currentUser()
        val loggedIn = sessionManager.session.value.isLoggedIn
        _uiState.update {
            it.copy(
                follow = it.follow.copy(
                    isLoggedIn = loggedIn,
                    isSelf = me != null && me == username,
                    isFollowing = if (!loggedIn) false else followRepository.isFollowing(username)
                        ?: (it.artist?.isFollowing == true),
                ),
            )
        }
        if (!loggedIn || me == null) {
            followStateJob?.cancel()
            return
        }
        // Already loading this user's list: its result updates the state.
        if (followStateJob?.isActive == true && followStateUser == me) return
        followStateJob?.cancel()
        followStateUser = me
        followStateJob = viewModelScope.launch {
            followRepository.ensureLoaded().onSuccess { snapshot ->
                _uiState.update {
                    if (it.follow.inFlight) it else it.copy(follow = it.follow.copy(isFollowing = username in snapshot.usernames))
                }
            }
        }
    }

    /**
     * 追蹤 / 追蹤中: optimistic toggle, reverted (with a message) when the request fails.
     * Presses while a request is in flight are ignored. Logged out: [onNeedsLogin].
     */
    fun toggleFollow(onNeedsLogin: () -> Unit) {
        val state = _uiState.value
        val artist = state.artist ?: return
        if (!state.follow.isLoggedIn) {
            onNeedsLogin()
            return
        }
        if (state.follow.inFlight || state.follow.isSelf) return
        val follow = !state.follow.isFollowing
        followStateJob?.cancel()
        _uiState.update {
            it.copy(
                follow = it.follow.copy(isFollowing = follow, inFlight = true, error = null),
                artist = it.artist?.let { a -> a.copy(followersCount = (a.followersCount + if (follow) 1 else -1).coerceAtLeast(0)) },
            )
        }
        viewModelScope.launch {
            val result = followRepository.setFollowing(artist, follow)
            _uiState.update {
                if (result.isSuccess) {
                    it.copy(follow = it.follow.copy(inFlight = false))
                } else {
                    it.copy(
                        follow = it.follow.copy(
                            isFollowing = !follow,
                            inFlight = false,
                            error = userMessageFor(TAG, result.exceptionOrNull(), UserMessages.FOLLOW_FAILED),
                        ),
                        artist = it.artist?.let { a -> a.copy(followersCount = (a.followersCount + if (follow) -1 else 1).coerceAtLeast(0)) },
                    )
                }
            }
        }
    }

    fun loadMoreFollowing() = following.loadMore()

    fun retryLoadMoreFollowing() = following.retryLoadMore()

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

enum class ArtistTab { Songs, Albums, Following }

/** The 追蹤 button of the artist header. */
data class FollowUiState(
    val isLoggedIn: Boolean = false,
    val isFollowing: Boolean = false,
    /** A follow / unfollow request is running (further presses are ignored). */
    val inFlight: Boolean = false,
    /** The artist is the logged-in user: no button. */
    val isSelf: Boolean = false,
    /** Message of the last failed toggle (the state was reverted). */
    val error: String? = null,
)

data class ArtistUiState(
    val isLoading: Boolean = false,
    val artist: Artist? = null,
    val songs: List<Song> = emptyList(),
    val songsLoadMore: LoadMoreState = LoadMoreState(),
    val albums: List<Album> = emptyList(),
    val albumsLoadMore: LoadMoreState = LoadMoreState(),
    val songsError: String? = null,
    val albumsError: String? = null,
    /** Users this artist follows (追蹤中 tab). */
    val following: List<Artist> = emptyList(),
    val followingLoadMore: LoadMoreState = LoadMoreState(),
    val followingError: String? = null,
    val follow: FollowUiState = FollowUiState(),
    /** Set while 播放全部 / 隨機播放 waits for the remaining songs. */
    val playAllPending: PlayAllMode? = null,
    val selectedTab: ArtistTab = ArtistTab.Songs,
    val error: String? = null,
)
