package com.example.streetvoicetv.ui.artist

import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.streetvoicetv.domain.model.Album
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.ui.components.AlbumListItem
import com.example.streetvoicetv.ui.components.MessageAction
import com.example.streetvoicetv.ui.components.MessageState
import com.example.streetvoicetv.ui.components.SongListItem
import com.example.streetvoicetv.ui.components.focusRoot
import com.example.streetvoicetv.ui.components.initialFocus
import com.example.streetvoicetv.ui.components.rememberInitialFocus
import com.example.streetvoicetv.ui.theme.LocalBottomOverlayInset
import com.example.streetvoicetv.ui.theme.screenListPadding

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ArtistScreen(
    onSongSelected: (Song, List<Song>) -> Unit,
    onAlbumSelected: (Album) -> Unit,
    onBack: () -> Unit,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ArtistContent(
        uiState = uiState,
        onSongSelected = onSongSelected,
        onAlbumSelected = onAlbumSelected,
        onRetry = { viewModel.retry() },
        onBack = onBack,
        onSelectTab = { viewModel.selectTab(it) },
    )
}

/** Stateless artist content (testable without Hilt / network). */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ArtistContent(
    uiState: ArtistUiState,
    onSongSelected: (Song, List<Song>) -> Unit,
    onAlbumSelected: (Album) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onSelectTab: (ArtistTab) -> Unit,
) {
    val bottomInset = LocalBottomOverlayInset.current
    when {
        uiState.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        uiState.error != null && uiState.artist == null -> {
            MessageState(
                title = "無法載入音樂人",
                message = uiState.error,
                isError = true,
                primaryAction = MessageAction("重試", onRetry),
                secondaryAction = MessageAction("返回", onBack),
                modifier = Modifier.padding(bottom = bottomInset),
            )
        }

        uiState.artist != null -> {
            val artist = uiState.artist
            val firstItemFocus = rememberInitialFocus()
            val tabs = ArtistTab.entries.toList()
            val selectedIndex = tabs.indexOf(uiState.selectedTab)
            val tabFocus = remember { tabs.map { FocusRequester() } }
            val selectedListEmpty = when (uiState.selectedTab) {
                ArtistTab.Songs -> uiState.songs.isEmpty()
                ArtistTab.Albums -> uiState.albums.isEmpty()
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRoot(firstItemFocus),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = screenListPadding(bottomInset),
            ) {
                // Header + tabs scroll away with the list.
                item(key = "header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = artist.profileImageUrl,
                            contentDescription = artist.displayName,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )

                        Spacer(modifier = Modifier.width(20.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artist.displayName,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    text = "${artist.songsCount} 首歌曲",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "粉絲 ${artist.followersCount}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (!artist.introduction.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = artist.introduction,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }

                item(key = "tabs") {
                    TabRow(
                        selectedTabIndex = selectedIndex,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            // Entering the tab row (e.g. Up from the list) lands on the selected
                            // tab. Otherwise the geometrically nearest tab would get focus and,
                            // since focusing a tab selects it, switch the list underneath.
                            .focusProperties { enter = { tabFocus[selectedIndex] } }
                            .focusGroup(),
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = index == selectedIndex,
                                onFocus = { onSelectTab(tab) },
                                modifier = Modifier
                                    .focusRequester(tabFocus[index])
                                    // Nothing to focus in an empty tab: start on the tab itself.
                                    .initialFocus(firstItemFocus, enabled = index == selectedIndex && selectedListEmpty),
                            ) {
                                Text(
                                    text = when (tab) {
                                        ArtistTab.Songs -> "歌曲 (${uiState.songs.size})"
                                        ArtistTab.Albums -> "專輯 (${uiState.albums.size})"
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }

                when (uiState.selectedTab) {
                    ArtistTab.Songs -> {
                        if (uiState.songs.isEmpty()) {
                            item(key = "empty_songs") {
                                EmptyTab(uiState.songsError ?: "沒有歌曲", isError = uiState.songsError != null)
                            }
                        } else {
                            itemsIndexed(uiState.songs, key = { _, it -> "song_${it.id}" }) { index, song ->
                                SongListItem(
                                    song = song,
                                    onClick = { onSongSelected(song, uiState.songs) },
                                    modifier = Modifier.initialFocus(firstItemFocus, enabled = index == 0),
                                )
                            }
                        }
                    }

                    ArtistTab.Albums -> {
                        if (uiState.albums.isEmpty()) {
                            item(key = "empty_albums") {
                                EmptyTab(uiState.albumsError ?: "沒有專輯", isError = uiState.albumsError != null)
                            }
                        } else {
                            itemsIndexed(uiState.albums, key = { _, it -> "album_${it.id}" }) { index, album ->
                                AlbumListItem(
                                    album = album,
                                    onClick = { onAlbumSelected(album) },
                                    modifier = Modifier.initialFocus(firstItemFocus, enabled = index == 0),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EmptyTab(text: String, isError: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
