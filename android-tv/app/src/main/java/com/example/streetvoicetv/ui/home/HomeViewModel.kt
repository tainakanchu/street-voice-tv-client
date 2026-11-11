package com.example.streetvoicetv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.data.auth.SessionState
import com.example.streetvoicetv.data.repository.FollowedSongs
import com.example.streetvoicetv.data.repository.FollowedSongsRepository
import com.example.streetvoicetv.data.repository.NotLoggedInException
import com.example.streetvoicetv.domain.model.ChartCategory
import com.example.streetvoicetv.domain.model.ChartPeriod
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.model.formatReleaseAge
import com.example.streetvoicetv.domain.model.parsePublishAt
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import com.example.streetvoicetv.playback.PlaybackManager
import com.example.streetvoicetv.playback.RecentlyPlayedEntry
import com.example.streetvoicetv.playback.RestorableSession
import com.example.streetvoicetv.ui.common.PlaybackActions
import com.example.streetvoicetv.ui.common.PlaybackManagerActions
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
class HomeViewModel internal constructor(
    private val repository: StreetVoiceRepository,
    private val sessionManager: SessionManager,
    private val followedSongsRepository: FollowedSongsRepository,
    private val playback: PlaybackActions = PlaybackActions.None,
    /** Epoch ms (for 「N 分鐘前」 of 最近播放). */
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    @Inject
    constructor(
        repository: StreetVoiceRepository,
        sessionManager: SessionManager,
        followedSongsRepository: FollowedSongsRepository,
        playbackManager: PlaybackManager,
    ) : this(repository, sessionManager, followedSongsRepository, PlaybackManagerActions(playbackManager))

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** The running load; a new load (retry, login / logout) cancels it. */
    private var loadJob: Job? = null

    /** The 「追蹤音樂人的新歌」 load: independent of the other rows. */
    private var followedJob: Job? = null

    /** Whose playlists [HomeUiState.myPlaylists] holds. */
    private var myPlaylistsOwner: String? = null

    init {
        viewModelScope.launch {
            // StateFlow is already distinct: this reloads on login, logout and when the
            // username becomes available (e.g. fetched after login), and loads initially.
            sessionManager.session.collect { session -> load(session) }
        }
        // 繼續播放 / 最近播放 are local (saved by the PlaybackManager, loaded asynchronously).
        viewModelScope.launch {
            playback.restorable.collect { session ->
                _uiState.update { it.copy(continueListening = session?.toContinueListening()) }
            }
        }
        viewModelScope.launch {
            playback.recentlyPlayedEntries.collect { entries ->
                _uiState.update { it.copy(recentlyPlayed = entries.toRecentSongs()) }
            }
        }
    }

    private fun RestorableSession.toContinueListening(): ContinueListening? {
        if (songs.isEmpty()) return null
        return ContinueListening(song = currentSong, positionMs = positionMs, queueSize = songs.size)
    }

    private fun List<RecentlyPlayedEntry>.toRecentSongs(): List<RecentSong> {
        val now = clock()
        return take(RECENTLY_PLAYED_COUNT).map { RecentSong(it.song, formatReleaseAge(it.playedAtMs, now)) }
    }

    /**
     * 繼續播放: restores the saved session and starts playing it. Returns the id of the song
     * that is now loaded (to open the player), or null when there was nothing to resume.
     */
    fun resumeLastSession(): Int? {
        val songId = playback.restorable.value?.currentSong?.id ?: return null
        return if (playback.resumeLastSession(play = true)) songId else null
    }

    private fun load(session: SessionState = sessionManager.session.value) {
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                // Never show the previous user's playlists while (re)loading.
                myPlaylists = if (session.username == myPlaylistsOwner) it.myPlaylists else emptyList(),
            )
        }
        loadFollowed(session)

        loadJob = viewModelScope.launch {
            val currentUsername = session.username

            // Logged in but the username is unknown (e.g. it could not be fetched at login):
            // fetch it; the session update triggers another load with the username.
            if (session.isLoggedIn && currentUsername == null) {
                launch {
                    repository.getCurrentUsername().onSuccess { sessionManager.updateUsername(it) }
                }
            }

            // 本週排行: its own row, shown whenever it arrives (never holds up the others).
            launch {
                val weekly = repository.getChart(ChartCategory.ALL_SLUG, ChartPeriod.Weekly, offset = 0, limit = WEEKLY_COUNT)
                _uiState.update { it.copy(weeklySongs = weekly.getOrNull()?.items.orEmpty()) }
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
            _uiState.update {
                it.copy(
                    isLoading = false,
                    chartSongs = chart,
                    editorPicks = editor,
                    playlists = playlists,
                    myPlaylists = myPlaylists,
                    genres = ChartCategory.forPeriod(ChartPeriod.Realtime),
                    error = error,
                )
            }
        }
    }

    /**
     * (Re)loads 「追蹤音樂人的新歌」. A fresh cached result (see [FollowedSongsRepository]) is
     * shown at once without a request; logged out → no row.
     */
    private fun loadFollowed(session: SessionState = sessionManager.session.value, force: Boolean = false) {
        followedJob?.cancel()
        if (!session.isLoggedIn || session.username == null) {
            followedJob = null
            _uiState.update { it.copy(followedNew = null) }
            return
        }
        if (!force) {
            followedSongsRepository.fresh()?.let { cached ->
                followedJob = null
                _uiState.update { it.copy(followedNew = cached.toUi()) }
                return
            }
        }
        // Never show another user's row while loading.
        _uiState.update { state ->
            state.copy(followedNew = state.followedNew?.takeIf { it.owner == session.username }?.copy(isRetrying = force))
        }
        followedJob = viewModelScope.launch {
            val result = followedSongsRepository.load(forceRefresh = force)
            _uiState.update { state ->
                result.fold(
                    onSuccess = { state.copy(followedNew = it.toUi()) },
                    onFailure = { e ->
                        val previous = state.followedNew?.takeIf { it.owner == session.username && it.songs.isNotEmpty() }
                        when {
                            e is NotLoggedInException -> state.copy(followedNew = null)
                            // Keep showing what we had; a refresh failure is not worth an error.
                            previous != null -> state.copy(followedNew = previous.copy(isRetrying = false))
                            else -> state.copy(
                                followedNew = FollowedNewSongs(
                                    owner = session.username,
                                    songs = emptyList(),
                                    error = userMessageFor(TAG, e),
                                ),
                            )
                        }
                    },
                )
            }
        }
    }

    private fun FollowedSongs.toUi(): FollowedNewSongs? {
        if (songs.isEmpty()) return null
        val now = followedSongsRepository.now()
        return FollowedNewSongs(
            owner = owner,
            songs = songs.map { song ->
                RecentSong(song, parsePublishAt(song.publishAt)?.let { formatReleaseAge(it, now) })
            },
        )
    }

    fun retry() = load()

    /** 重試 of the 「追蹤音樂人的新歌」 row. */
    fun retryFollowed() = loadFollowed(force = true)

    /**
     * Home became visible again (e.g. back from the player): refreshes 「追蹤音樂人的新歌」 once
     * its cache is stale (older than 10 minutes, or a follow changed meanwhile).
     */
    fun onResume() {
        // 「N 分鐘前」 of 最近播放 moves on while Home is not visible.
        _uiState.update { it.copy(recentlyPlayed = playback.recentlyPlayedEntries.value.toRecentSongs()) }
        if (followedJob?.isActive == true) return
        val session = sessionManager.session.value
        if (!session.isLoggedIn || session.username == null) return
        if (followedSongsRepository.fresh() == null) loadFollowed(session)
    }

    private companion object {
        const val TAG = "HomeViewModel"
        const val WEEKLY_COUNT = 20
        const val RECENTLY_PLAYED_COUNT = 20
    }
}

/**
 * A song of a Home row with a time label: the release age of 「追蹤音樂人的新歌」 (「3 天前」, if
 * the API gave a date) or when it was played for 「最近播放」 (「5 分鐘前」).
 */
data class RecentSong(
    val song: Song,
    val ageLabel: String?,
)

/** The 「追蹤音樂人的新歌」 row: songs newest first, or an [error] (offering 重試). */
data class FollowedNewSongs(
    val owner: String,
    val songs: List<RecentSong>,
    val error: String? = null,
    /** 重試 pressed: the request is running. */
    val isRetrying: Boolean = false,
) {
    /** The queue of 播放全部: the merged list, newest first. */
    val queue: List<Song> = songs.map { it.song }
}

/** The 「繼續播放」 card: the saved session's current song, where it stopped, and the queue size. */
data class ContinueListening(
    val song: Song,
    val positionMs: Long,
    val queueSize: Int,
) {
    /** Progress through the song (0..1); 0 when the duration is unknown. */
    val progress: Float
        get() {
            val durationMs = song.durationSeconds * 1000
            if (durationMs.isNaN() || durationMs <= 0) return 0f
            return (positionMs / durationMs).toFloat().coerceIn(0f, 1f)
        }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val chartSongs: List<Song> = emptyList(),
    val editorPicks: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val myPlaylists: List<Playlist> = emptyList(),
    /** 曲風 chips (chart categories); each opens the realtime chart of that category. */
    val genres: List<ChartCategory> = emptyList(),
    /** 本週排行 (weekly chart, top 20). */
    val weeklySongs: List<Song> = emptyList(),
    /** 「追蹤音樂人的新歌」 (null: no row — logged out, nobody followed, or not loaded yet). */
    val followedNew: FollowedNewSongs? = null,
    /** 「繼續播放」 (null: nothing saved, or a queue is already loaded). */
    val continueListening: ContinueListening? = null,
    /** 「最近播放」, most recent first (empty: no row). */
    val recentlyPlayed: List<RecentSong> = emptyList(),
    val error: String? = null,
) {
    /** The queue of a 最近播放 card: the row as shown. */
    val recentlyPlayedQueue: List<Song> = recentlyPlayed.map { it.song }
}
