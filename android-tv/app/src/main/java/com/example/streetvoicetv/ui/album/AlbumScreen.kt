package com.example.streetvoicetv.ui.album

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.ui.components.MessageAction
import com.example.streetvoicetv.ui.components.MessageState
import com.example.streetvoicetv.ui.components.SongListItem
import com.example.streetvoicetv.ui.components.initialFocus
import com.example.streetvoicetv.ui.components.rememberInitialFocus
import com.example.streetvoicetv.ui.theme.Dimens
import com.example.streetvoicetv.ui.theme.LocalBottomOverlayInset
import com.example.streetvoicetv.ui.theme.screenListPadding

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AlbumScreen(
    onSongSelected: (Song, List<Song>) -> Unit,
    onBack: () -> Unit,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    AlbumContent(
        uiState = uiState,
        onSongSelected = onSongSelected,
        onRetry = { viewModel.retry() },
        onBack = onBack,
    )
}

/** Stateless album content (testable without Hilt / network). */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun AlbumContent(
    uiState: AlbumUiState,
    onSongSelected: (Song, List<Song>) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val bottomInset = LocalBottomOverlayInset.current
    when {
        uiState.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        uiState.error != null && uiState.album == null -> {
            MessageState(
                title = "無法載入專輯",
                message = uiState.error,
                isError = true,
                primaryAction = MessageAction("重試", onRetry),
                secondaryAction = MessageAction("返回", onBack),
                modifier = Modifier.padding(bottom = bottomInset),
            )
        }

        uiState.album != null -> {
            val album = uiState.album
            val firstSongFocus = rememberInitialFocus()

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = screenListPadding(bottomInset),
            ) {
                // Header scrolls away with the list.
                item(key = "header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = album.imageUrl,
                            contentDescription = album.name,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )

                        Spacer(modifier = Modifier.width(20.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = album.name,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (album.artistName != null) {
                                Text(
                                    text = album.artistName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${album.songsCount} 首歌曲",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                                text = "這張專輯沒有歌曲",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    itemsIndexed(uiState.songs, key = { _, it -> it.id }) { index, song ->
                        SongListItem(
                            song = song,
                            onClick = { onSongSelected(song, uiState.songs) },
                            modifier = Modifier.initialFocus(firstSongFocus, enabled = index == 0),
                        )
                    }
                }
            }
        }
    }
}
