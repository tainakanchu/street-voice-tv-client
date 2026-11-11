package com.example.streetvoicetv.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.data.SearchHistoryManager
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.Page
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import com.example.streetvoicetv.ui.common.LoadMoreState
import com.example.streetvoicetv.ui.common.PagedListLoader
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
class SearchViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    private val searchHistoryManager: SearchHistoryManager,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<String>> = searchHistoryManager.history

    /** Query of the results on screen (the text field may have been edited since). */
    private var resultsQuery: String = ""

    /** Songs are paginated (the server returns at most 100); artists / playlists stay capped. */
    private val songs = PagedListLoader(
        scope = viewModelScope,
        tag = TAG,
        pageSize = SONGS_PAGE_SIZE,
        key = Song::id,
        fetch = { offset, limit -> repository.searchSongs(resultsQuery, limit, offset) },
        onChange = { items, loadMore -> _uiState.update { it.copy(songs = items, songsLoadMore = loadMore) } },
    )
    private var searchJob: Job? = null

    init {
        // `search?q=…` (voice / assistant search): fill the field and search once. Not again
        // after a configuration change or process restore (the results are restored instead).
        val initialQuery = savedStateHandle.get<String>(ARG_QUERY)
        if (!initialQuery.isNullOrBlank() && savedStateHandle.get<Boolean>(KEY_QUERY_HANDLED) != true) {
            savedStateHandle[KEY_QUERY_HANDLED] = true
            submitQuery(initialQuery)
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun selectHistory(query: String) = submitQuery(query)

    /** A recognised voice query: fills the field and searches. */
    fun onVoiceResult(query: String) = submitQuery(query)

    private fun submitQuery(query: String) {
        _uiState.update { it.copy(query = query.trim()) }
        search()
    }

    fun removeHistory(query: String) {
        searchHistoryManager.remove(query)
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        searchHistoryManager.add(query)

        // A new search supersedes the previous one, including its pending load-more.
        searchJob?.cancel()
        resultsQuery = query
        songs.reset(null)
        _uiState.update { it.copy(isLoading = true, error = null) }

        searchJob = viewModelScope.launch {
            val songsDeferred = async { repository.searchSongs(query, SONGS_PAGE_SIZE, 0) }
            val artistsDeferred = async { repository.searchArtists(query, SECTION_LIMIT, 0) }
            val playlistsDeferred = async { repository.searchPlaylists(query, SECTION_LIMIT, 0) }

            val songsResult = songsDeferred.await()
            val artistsResult = artistsDeferred.await()
            val playlistsResult = playlistsDeferred.await()

            val error = when {
                songsResult.isFailure && artistsResult.isFailure && playlistsResult.isFailure ->
                    userMessageFor(TAG, songsResult.exceptionOrNull(), UserMessages.SEARCH_FAILED)
                else -> null
            }

            songs.reset(songsResult.getOrNull())
            _uiState.update {
                it.copy(
                    isLoading = false,
                    artists = artistsResult.itemsOrEmpty(),
                    playlists = playlistsResult.itemsOrEmpty(),
                    error = error,
                    resultsGeneration = it.resultsGeneration + 1,
                )
            }
        }
    }

    fun loadMoreSongs() = songs.loadMore()

    fun retryLoadMoreSongs() = songs.retryLoadMore()

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun <T> Result<Page<T>>.itemsOrEmpty(): List<T> = getOrNull()?.items ?: emptyList()

    companion object {
        /** Navigation argument: a query to search for right away. */
        const val ARG_QUERY = "q"
        private const val KEY_QUERY_HANDLED = "q_handled"
        private const val TAG = "SearchViewModel"
        private const val SONGS_PAGE_SIZE = 20
        private const val SECTION_LIMIT = 5
    }
}

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val songs: List<Song> = emptyList(),
    val songsLoadMore: LoadMoreState = LoadMoreState(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val error: String? = null,
    /**
     * Incremented for every completed search. The screen moves focus to the first result when
     * it changes (but not when more songs are appended to the same results).
     */
    val resultsGeneration: Int = 0,
) {
    val hasResults: Boolean get() = songs.isNotEmpty() || artists.isNotEmpty() || playlists.isNotEmpty()
    val isEmpty: Boolean get() = songs.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}
