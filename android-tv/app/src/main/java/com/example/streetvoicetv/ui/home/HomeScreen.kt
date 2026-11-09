package com.example.streetvoicetv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.ui.components.CardRowVerticalPadding
import com.example.streetvoicetv.ui.components.MessageAction
import com.example.streetvoicetv.ui.components.MessageState
import com.example.streetvoicetv.ui.components.PlaylistCard
import com.example.streetvoicetv.ui.components.SongCard
import com.example.streetvoicetv.ui.components.InitialFocus
import com.example.streetvoicetv.ui.components.initialFocus
import com.example.streetvoicetv.ui.components.rememberInitialFocus
import com.example.streetvoicetv.ui.theme.Dimens
import com.example.streetvoicetv.ui.theme.LocalBottomOverlayInset

private val ScreenHPadding = Dimens.SafeHorizontal

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onSongSelected: (Song, List<Song>) -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onSearchTap: () -> Unit,
    onLoginTap: () -> Unit,
    onMyPageTap: () -> Unit,
    isLoggedIn: Boolean,
    profileImageUrl: String?,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    HomeContent(
        uiState = uiState,
        isLoggedIn = isLoggedIn,
        profileImageUrl = profileImageUrl,
        onSongSelected = onSongSelected,
        onPlaylistSelected = onPlaylistSelected,
        onSearchTap = onSearchTap,
        onLoginTap = onLoginTap,
        onMyPageTap = onMyPageTap,
        onRetry = { viewModel.retry() },
    )
}

/** Stateless home content (testable without Hilt / network). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HomeContent(
    uiState: HomeUiState,
    isLoggedIn: Boolean,
    profileImageUrl: String?,
    onSongSelected: (Song, List<Song>) -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onSearchTap: () -> Unit,
    onLoginTap: () -> Unit,
    onMyPageTap: () -> Unit,
    onRetry: () -> Unit = {},
) {
    val bottomInset = LocalBottomOverlayInset.current
    val firstItemFocus = rememberInitialFocus()
    val hasContent = uiState.chartSongs.isNotEmpty() || uiState.editorPicks.isNotEmpty() ||
        uiState.myPlaylists.isNotEmpty() || uiState.playlists.isNotEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar: search + account
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = ScreenHPadding, end = ScreenHPadding, top = Dimens.SafeVertical),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                onClick = onSearchTap,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = "搜尋歌曲與音樂人...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            if (isLoggedIn) {
                Surface(
                    onClick = onMyPageTap,
                    shape = ClickableSurfaceDefaults.shape(CircleShape),
                    modifier = Modifier.size(44.dp),
                ) {
                    AsyncImage(
                        model = profileImageUrl,
                        contentDescription = "我的頁面",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                }
            } else {
                Surface(
                    onClick = onLoginTap,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                ) {
                    Text(
                        text = "登入",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            !hasContent -> {
                MessageState(
                    title = if (uiState.error != null) "無法載入首頁" else null,
                    message = uiState.error ?: "目前沒有可顯示的內容",
                    isError = uiState.error != null,
                    primaryAction = MessageAction("重試", onRetry),
                    modifier = Modifier.padding(bottom = bottomInset),
                )
            }

            else -> {
                // The first section's first card gets the initial focus.
                val firstSection = when {
                    uiState.chartSongs.isNotEmpty() -> 0
                    uiState.editorPicks.isNotEmpty() -> 1
                    uiState.myPlaylists.isNotEmpty() -> 2
                    else -> 3
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = Dimens.SafeVertical + bottomInset),
                ) {
                    if (uiState.chartSongs.isNotEmpty()) {
                        item(key = "chart") {
                            SongCarouselSection(
                                title = "即時排行",
                                songs = uiState.chartSongs,
                                onSongSelected = onSongSelected,
                                showRank = true,
                                firstItemFocus = firstItemFocus.takeIf { firstSection == 0 },
                            )
                        }
                    }

                    if (uiState.editorPicks.isNotEmpty()) {
                        item(key = "editor") {
                            SongCarouselSection(
                                title = "推薦單曲",
                                songs = uiState.editorPicks,
                                onSongSelected = onSongSelected,
                                firstItemFocus = firstItemFocus.takeIf { firstSection == 1 },
                            )
                        }
                    }

                    if (uiState.myPlaylists.isNotEmpty()) {
                        item(key = "my_playlists") {
                            PlaylistSection(
                                title = "我的歌單",
                                playlists = uiState.myPlaylists,
                                onPlaylistSelected = onPlaylistSelected,
                                firstItemFocus = firstItemFocus.takeIf { firstSection == 2 },
                            )
                        }
                    }

                    if (uiState.playlists.isNotEmpty()) {
                        item(key = "playlists") {
                            PlaylistSection(
                                title = "歌單",
                                playlists = uiState.playlists,
                                onPlaylistSelected = onPlaylistSelected,
                                firstItemFocus = firstItemFocus.takeIf { firstSection == 3 },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = ScreenHPadding),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SongCarouselSection(
    title: String,
    songs: List<Song>,
    onSongSelected: (Song, List<Song>) -> Unit,
    showRank: Boolean = false,
    firstItemFocus: InitialFocus? = null,
) {
    Column {
        SectionTitle(title)
        LazyRow(
            modifier = Modifier.focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = ScreenHPadding, vertical = CardRowVerticalPadding),
        ) {
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongCard(
                    song = song,
                    rank = if (showRank) index + 1 else null,
                    onClick = { onSongSelected(song, songs) },
                    modifier = Modifier.initialFocus(firstItemFocus, enabled = index == 0),
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun PlaylistSection(
    title: String,
    playlists: List<Playlist>,
    onPlaylistSelected: (Playlist) -> Unit,
    firstItemFocus: InitialFocus? = null,
) {
    Column {
        SectionTitle(title)
        LazyRow(
            modifier = Modifier.focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = ScreenHPadding, vertical = CardRowVerticalPadding),
        ) {
            itemsIndexed(playlists, key = { _, it -> it.id }) { index, playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistSelected(playlist) },
                    modifier = Modifier.initialFocus(firstItemFocus, enabled = index == 0),
                )
            }
        }
    }
}
