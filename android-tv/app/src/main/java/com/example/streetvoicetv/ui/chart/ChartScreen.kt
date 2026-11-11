package com.example.streetvoicetv.ui.chart

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.example.streetvoicetv.domain.model.ChartCategory
import com.example.streetvoicetv.domain.model.ChartPeriod
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.ui.common.PlayAllMode
import com.example.streetvoicetv.ui.components.LoadMoreEffect
import com.example.streetvoicetv.ui.components.PlayAllButtons
import com.example.streetvoicetv.ui.components.SongListItem
import com.example.streetvoicetv.ui.components.focusRoot
import com.example.streetvoicetv.ui.components.holdFocusWhileLoadingMore
import com.example.streetvoicetv.ui.components.initialFocus
import com.example.streetvoicetv.ui.components.loadMoreFooter
import com.example.streetvoicetv.ui.components.rememberInitialFocus
import com.example.streetvoicetv.ui.theme.LocalBottomOverlayInset
import com.example.streetvoicetv.ui.theme.screenListPadding

const val CHART_GENRES_TAG = "chart_genres"
const val CHART_TABS_TAG = "chart_tabs"

@Composable
fun ChartScreen(
    onSongSelected: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>, PlayAllMode) -> Unit,
    viewModel: ChartViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChartContent(
        uiState = uiState,
        onSongSelected = onSongSelected,
        onSelectPeriod = viewModel::selectPeriod,
        onSelectCategory = viewModel::selectCategory,
        onRetry = viewModel::retry,
        onPlayAll = { mode -> viewModel.playAll(mode, onPlayAll) },
        onLoadMore = viewModel::loadMore,
        onRetryLoadMore = viewModel::retryLoadMore,
    )
}

/**
 * Stateless 排行榜 content: title, period tabs (即時 / 週間, focusing a tab selects it like on
 * the artist screen), the category chips (selected by click) and the ranked song list.
 */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ChartContent(
    uiState: ChartUiState,
    onSongSelected: (Song, List<Song>) -> Unit,
    onSelectPeriod: (ChartPeriod) -> Unit,
    onSelectCategory: (ChartCategory) -> Unit,
    onRetry: () -> Unit = {},
    onPlayAll: (PlayAllMode) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRetryLoadMore: () -> Unit = {},
) {
    val bottomInset = LocalBottomOverlayInset.current
    val firstItemFocus = rememberInitialFocus()
    val periods = ChartPeriod.entries
    val selectedPeriodIndex = periods.indexOf(uiState.period)
    val tabFocus = remember { periods.map { FocusRequester() } }
    val chipFocus = remember { ChartCategory.ALL.associate { it.slug to FocusRequester() } }
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, uiState.loadMore, onLoadMore)

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .focusRoot(firstItemFocus),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = screenListPadding(bottomInset),
    ) {
        item(key = "title") {
            Text(
                text = "排行榜",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        item(key = "tabs") {
            TabRow(
                selectedTabIndex = selectedPeriodIndex,
                modifier = Modifier
                    .testTag(CHART_TABS_TAG)
                    .padding(bottom = 8.dp)
                    // Entering the row lands on the selected tab (focusing a tab selects it).
                    .focusProperties { onEnter = { tabFocus[selectedPeriodIndex].requestFocus() } }
                    .focusGroup(),
            ) {
                periods.forEachIndexed { index, period ->
                    Tab(
                        selected = index == selectedPeriodIndex,
                        onFocus = { onSelectPeriod(period) },
                        modifier = Modifier
                            .focusRequester(tabFocus[index])
                            // An empty chart has nothing below to focus: start on the tab.
                            .initialFocus(
                                firstItemFocus,
                                enabled = index == selectedPeriodIndex && !uiState.isLoading &&
                                    uiState.error == null && uiState.songs.isEmpty(),
                            ),
                    ) {
                        Text(
                            text = period.label,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        item(key = "genres") {
            LazyRow(
                modifier = Modifier
                    .testTag(CHART_GENRES_TAG)
                    // Back into the row: the chip focused last, else the selected one.
                    .focusRestorer(chipFocus.getValue(uiState.category.slug)),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                itemsIndexed(uiState.categories, key = { _, it -> it.slug }) { _, category ->
                    FilterChip(
                        selected = category == uiState.category,
                        onClick = { onSelectCategory(category) },
                        modifier = Modifier.focusRequester(chipFocus.getValue(category.slug)),
                    ) {
                        Text(category.label)
                    }
                }
            }
        }

        when {
            uiState.isLoading -> item(key = "loading") {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            uiState.error != null -> item(key = "error") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = uiState.error,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onRetry, modifier = Modifier.initialFocus(firstItemFocus)) {
                        Text("重試")
                    }
                }
            }

            uiState.songs.isEmpty() -> item(key = "empty") {
                Text(
                    text = "這個排行榜目前沒有歌曲",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                )
            }

            else -> {
                item(key = "play_all") {
                    PlayAllButtons(
                        pending = uiState.playAllPending,
                        onPlayAll = onPlayAll,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                itemsIndexed(uiState.songs, key = { _, it -> "song_${it.id}" }) { index, song ->
                    SongListItem(
                        song = song,
                        rank = index + 1,
                        onClick = { onSongSelected(song, uiState.songs) },
                        modifier = Modifier
                            .initialFocus(firstItemFocus, enabled = index == 0)
                            .holdFocusWhileLoadingMore(index == uiState.songs.lastIndex, uiState.loadMore),
                    )
                }
                loadMoreFooter(uiState.loadMore, onRetryLoadMore)
            }
        }
    }
}
