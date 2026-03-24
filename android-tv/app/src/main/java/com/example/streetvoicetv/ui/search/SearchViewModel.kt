package com.example.streetvoicetv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.data.SearchHistoryManager
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    private val searchHistoryManager: SearchHistoryManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<String>> = searchHistoryManager.history

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun selectHistory(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        search()
    }

    fun removeHistory(query: String) {
        searchHistoryManager.remove(query)
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        searchHistoryManager.add(query)

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
        )

        viewModelScope.launch {
            val songsDeferred = async { repository.searchSongs(query) }
            val artistsDeferred = async { repository.searchArtists(query) }
            val playlistsDeferred = async { repository.searchPlaylists(query) }

            val songsResult = songsDeferred.await()
            val artistsResult = artistsDeferred.await()
            val playlistsResult = playlistsDeferred.await()

            val songs = songsResult.getOrDefault(emptyList())
            val artists = artistsResult.getOrDefault(emptyList())
            val playlists = playlistsResult.getOrDefault(emptyList())

            val error = when {
                songsResult.isFailure && artistsResult.isFailure && playlistsResult.isFailure ->
                    songsResult.exceptionOrNull()?.message ?: "Search failed"
                else -> null
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                songs = songs,
                artists = artists,
                playlists = playlists,
                error = error,
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val songs: List<Song> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val error: String? = null,
) {
    val hasResults: Boolean get() = songs.isNotEmpty() || artists.isNotEmpty() || playlists.isNotEmpty()
    val isEmpty: Boolean get() = songs.isEmpty() && artists.isEmpty() && playlists.isEmpty()
}
