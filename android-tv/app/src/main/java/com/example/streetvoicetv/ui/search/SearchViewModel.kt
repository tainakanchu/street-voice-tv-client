package com.example.streetvoicetv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.domain.model.Artist
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isBlank()) return

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            error = null,
        )

        viewModelScope.launch {
            val songsDeferred = async { repository.searchSongs(query) }
            val artistsDeferred = async { repository.searchArtists(query) }

            val songsResult = songsDeferred.await()
            val artistsResult = artistsDeferred.await()

            val songs = songsResult.getOrDefault(emptyList())
            val artists = artistsResult.getOrDefault(emptyList())

            val error = when {
                songsResult.isFailure && artistsResult.isFailure ->
                    songsResult.exceptionOrNull()?.message ?: "Search failed"
                else -> null
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                songs = songs,
                artists = artists,
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
    val error: String? = null,
) {
    val hasResults: Boolean get() = songs.isNotEmpty() || artists.isNotEmpty()
    val isEmpty: Boolean get() = songs.isEmpty() && artists.isEmpty()
}
