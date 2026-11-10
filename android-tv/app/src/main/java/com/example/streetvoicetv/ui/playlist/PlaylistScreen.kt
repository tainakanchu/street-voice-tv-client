package com.example.streetvoicetv.ui.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.ui.common.PlayAllMode
import com.example.streetvoicetv.ui.components.LoadMoreEffect
import com.example.streetvoicetv.ui.components.MessageAction
import com.example.streetvoicetv.ui.components.PlayAllButtons
import com.example.streetvoicetv.ui.components.holdFocusWhileLoadingMore
import com.example.streetvoicetv.ui.components.loadMoreFooter
import com.example.streetvoicetv.ui.components.MessageState
import com.example.streetvoicetv.ui.components.SongListItem
import com.example.streetvoicetv.ui.components.focusRoot
import com.example.streetvoicetv.ui.components.initialFocus
import com.example.streetvoicetv.ui.components.rememberInitialFocus
import com.example.streetvoicetv.ui.theme.Dimens
import com.example.streetvoicetv.ui.theme.LocalBottomOverlayInset
import com.example.streetvoicetv.ui.theme.screenListPadding

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlaylistScreen(
    onSongSelected: (Song, List<Song>) -> Unit,
    onPlayAll: (List<Song>, PlayAllMode) -> Unit,
    onBack: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    PlaylistContent(
        uiState = uiState,
        onSongSelected = onSongSelected,
        onRetry = { viewModel.retry() },
        onBack = onBack,
        onPlayAll = { mode -> viewModel.playAll(mode, onPlayAll) },
        onLoadMore = { viewModel.loadMore() },
        onRetryLoadMore = { viewModel.retryLoadMore() },
    )
}

/** Stateless playlist content (testable without Hilt / network). */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun PlaylistContent(
    uiState: PlaylistUiState,
    onSongSelected: (Song, List<Song>) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPlayAll: (PlayAllMode) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRetryLoadMore: () -> Unit = {},
) {
    val bottomInset = LocalBottomOverlayInset.current
    when {
        uiState.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        uiState.error != null && uiState.songs.isEmpty() -> {
            MessageState(
                title = "無法載入歌單",
                message = uiState.error,
                isError = true,
                primaryAction = MessageAction("重試", onRetry),
                secondaryAction = MessageAction("返回", onBack),
                modifier = Modifier.padding(bottom = bottomInset),
            )
        }

        else -> {
            val playlist = uiState.playlist
            val firstSongFocus = rememberInitialFocus()
            val listState = rememberLazyListState()
            LoadMoreEffect(listState, uiState.loadMore, onLoadMore)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRoot(firstSongFocus),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = screenListPadding(bottomInset),
            ) {
                // Header scrolls away with the list.
                if (playlist != null) {
                    item(key = "header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            AsyncImage(
                                model = playlist.imageUrl,
                                contentDescription = playlist.name,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop,
                            )

                            Spacer(modifier = Modifier.width(20.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (playlist.curatorName != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${playlist.curatorName} 策劃",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${playlist.songsCount} 首歌曲",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (!playlist.description.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = playlist.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (uiState.songs.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    PlayAllButtons(pending = uiState.playAllPending, onPlayAll = onPlayAll)
                                }
                            }
                        }
                    }
                }

                if (playlist == null && uiState.songs.isNotEmpty()) {
                    item(key = "play_all") {
                        PlayAllButtons(
                            pending = uiState.playAllPending,
                            onPlayAll = onPlayAll,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }

                if (uiState.songs.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 64.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "這個歌單沒有歌曲",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    // Playing a song queues the songs loaded so far (not the whole playlist).
                    itemsIndexed(uiState.songs, key = { _, it -> it.id }) { index, song ->
                        SongListItem(
                            song = song,
                            onClick = { onSongSelected(song, uiState.songs) },
                            modifier = Modifier
                                .initialFocus(firstSongFocus, enabled = index == 0)
                                .holdFocusWhileLoadingMore(index == uiState.songs.lastIndex, uiState.loadMore),
                        )
                    }
                    loadMoreFooter(uiState.loadMore, onRetryLoadMore)
                }
            }
        }
    }
}
