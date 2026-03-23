package com.example.streetvoicetv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class HomeViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val chartDeferred = async { repository.getRealtimeChart(limit = 10) }
            val editorDeferred = async { repository.getEditorChoice(limit = 10) }
            val playlistsDeferred = async { repository.getRecommendedPlaylists(limit = 10) }

            val chart = chartDeferred.await().getOrDefault(emptyList())
            val editor = editorDeferred.await().getOrDefault(emptyList())
            val playlists = playlistsDeferred.await().getOrDefault(emptyList())

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                chartSongs = chart,
                editorPicks = editor,
                playlists = playlists,
            )
        }
    }

    fun retry() = load()
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val chartSongs: List<Song> = emptyList(),
    val editorPicks: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val error: String? = null,
)
