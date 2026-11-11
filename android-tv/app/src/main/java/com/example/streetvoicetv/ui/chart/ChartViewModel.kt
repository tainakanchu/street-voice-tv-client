package com.example.streetvoicetv.ui.chart

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.domain.model.ChartCategory
import com.example.streetvoicetv.domain.model.ChartPeriod
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import com.example.streetvoicetv.ui.common.LoadMoreState
import com.example.streetvoicetv.ui.common.PagedListLoader
import com.example.streetvoicetv.ui.common.PlayAllMode
import com.example.streetvoicetv.ui.common.userMessageFor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 排行榜: one chart (period × category), paged by [StreetVoiceRepository.CHART_PAGE_SIZE].
 * Switching the period or the category cancels the running load (and any load-more), so a
 * superseded chart can never show up.
 */
@HiltViewModel
class ChartViewModel @Inject constructor(
    private val repository: StreetVoiceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var period = ChartPeriod.fromPathSegment(savedStateHandle.get<String>(ARG_PERIOD))
    private var category = ChartCategory.bySlug(savedStateHandle.get<String>(ARG_GENRE))
        .let { if (it.availableFor(period)) it else ChartCategory.bySlug(ChartCategory.ALL_SLUG) }

    private val _uiState = MutableStateFlow(stateFor(period, category))
    val uiState: StateFlow<ChartUiState> = _uiState.asStateFlow()

    private val songs = PagedListLoader(
        scope = viewModelScope,
        tag = TAG,
        pageSize = StreetVoiceRepository.CHART_PAGE_SIZE,
        key = Song::id,
        fetch = { offset, limit -> repository.getChart(category.slug, period, offset, limit) },
        onChange = { items, loadMore -> _uiState.update { it.copy(songs = items, loadMore = loadMore) } },
    )
    private var loadJob: Job? = null

    init {
        load()
    }

    private fun stateFor(period: ChartPeriod, category: ChartCategory) = ChartUiState(
        period = period,
        category = category,
        categories = ChartCategory.forPeriod(period),
    )

    private fun load() {
        loadJob?.cancel()
        songs.reset(null)
        _uiState.update {
            stateFor(period, category).copy(isLoading = true)
        }
        loadJob = viewModelScope.launch {
            val result = songs.fetchFirst()
            result
                .onSuccess { page ->
                    songs.reset(page)
                    _uiState.update { it.copy(isLoading = false) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = userMessageFor(TAG, e)) }
                }
        }
    }

    fun selectPeriod(newPeriod: ChartPeriod) {
        if (newPeriod == period) return
        period = newPeriod
        // A realtime-only category has no weekly chart: fall back to 全部.
        if (!category.availableFor(newPeriod)) category = ChartCategory.bySlug(ChartCategory.ALL_SLUG)
        load()
    }

    fun selectCategory(newCategory: ChartCategory) {
        if (newCategory == category || !newCategory.availableFor(period)) return
        category = newCategory
        load()
    }

    fun retry() = load()

    fun loadMore() = songs.loadMore()

    fun retryLoadMore() = songs.retryLoadMore()

    /** Loads the rest of the chart (at most 50 songs) and hands it to [play]. */
    fun playAll(mode: PlayAllMode, play: (List<Song>, PlayAllMode) -> Unit) {
        if (_uiState.value.playAllPending != null) return
        _uiState.update { it.copy(playAllPending = mode) }
        songs.loadUpTo(ChartCategory.MAX_ENTRIES) { loaded ->
            _uiState.update { it.copy(playAllPending = null) }
            if (loaded.isNotEmpty()) play(loaded, mode)
        }
    }

    companion object {
        const val ARG_GENRE = "genre"
        const val ARG_PERIOD = "period"
        private const val TAG = "ChartViewModel"
    }
}

data class ChartUiState(
    val period: ChartPeriod = ChartPeriod.Realtime,
    val category: ChartCategory = ChartCategory.ALL.first(),
    /** The categories offered for [period]. */
    val categories: List<ChartCategory> = ChartCategory.forPeriod(period),
    val isLoading: Boolean = false,
    val songs: List<Song> = emptyList(),
    val loadMore: LoadMoreState = LoadMoreState(),
    val playAllPending: PlayAllMode? = null,
    val error: String? = null,
)
