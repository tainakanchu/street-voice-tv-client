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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.ui.platform.testTag
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.streetvoicetv.domain.model.Album
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.ui.components.ArtistListItem
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.ui.common.PlayAllMode
import com.example.streetvoicetv.ui.components.AlbumListItem
import com.example.streetvoicetv.ui.components.LoadMoreEffect
import com.example.streetvoicetv.ui.components.PlayAllButtons
import com.example.streetvoicetv.ui.components.holdFocusWhileLoadingMore
import com.example.streetvoicetv.ui.components.loadMoreFooter
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
    onPlayAll: (List<Song>, PlayAllMode) -> Unit,
    onAlbumSelected: (Album) -> Unit,
    onBack: () -> Unit,
    onArtistSelected: (Artist) -> Unit = {},
    onLoginRequired: () -> Unit = {},
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
        onPlayAll = { mode -> viewModel.playAll(mode, onPlayAll) },
        onLoadMoreSongs = { viewModel.loadMoreSongs() },
        onRetryLoadMoreSongs = { viewModel.retryLoadMoreSongs() },
        onLoadMoreAlbums = { viewModel.loadMoreAlbums() },
        onRetryLoadMoreAlbums = { viewModel.retryLoadMoreAlbums() },
        onArtistSelected = onArtistSelected,
        onToggleFollow = { viewModel.toggleFollow(onLoginRequired) },
        onLoadMoreFollowing = { viewModel.loadMoreFollowing() },
        onRetryLoadMoreFollowing = { viewModel.retryLoadMoreFollowing() },
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
    onPlayAll: (PlayAllMode) -> Unit = {},
    onLoadMoreSongs: () -> Unit = {},
    onRetryLoadMoreSongs: () -> Unit = {},
    onLoadMoreAlbums: () -> Unit = {},
    onRetryLoadMoreAlbums: () -> Unit = {},
    onArtistSelected: (Artist) -> Unit = {},
    onToggleFollow: () -> Unit = {},
    onLoadMoreFollowing: () -> Unit = {},
    onRetryLoadMoreFollowing: () -> Unit = {},
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
                ArtistTab.Following -> uiState.following.isEmpty()
            }

            val listState = rememberLazyListState()
            when (uiState.selectedTab) {
                ArtistTab.Songs -> LoadMoreEffect(listState, uiState.songsLoadMore, onLoadMoreSongs)
                ArtistTab.Albums -> LoadMoreEffect(listState, uiState.albumsLoadMore, onLoadMoreAlbums)
                ArtistTab.Following -> LoadMoreEffect(listState, uiState.followingLoadMore, onLoadMoreFollowing)
            }

            LazyColumn(
                state = listState,
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
                            if (uiState.follow.error != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = uiState.follow.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (!uiState.follow.isSelf) {
                            Spacer(modifier = Modifier.width(20.dp))
                            FollowButton(follow = uiState.follow, onClick = onToggleFollow)
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
                            .focusProperties { onEnter = { tabFocus[selectedIndex].requestFocus() } }
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
                                        ArtistTab.Songs -> "歌曲 (${uiState.songsLoadMore.total ?: uiState.songs.size})"
                                        ArtistTab.Albums -> "專輯 (${uiState.albumsLoadMore.total ?: uiState.albums.size})"
                                        ArtistTab.Following -> "追蹤中 (${uiState.followingLoadMore.total ?: uiState.following.size})"
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
                            item(key = "play_all") {
                                PlayAllButtons(
                                    pending = uiState.playAllPending,
                                    onPlayAll = onPlayAll,
                                    modifier = Modifier.padding(bottom = 8.dp),
                                )
                            }
                            // Playing a song queues the songs loaded so far (not all of the artist's songs).
                            itemsIndexed(uiState.songs, key = { _, it -> "song_${it.id}" }) { index, song ->
                                SongListItem(
                                    song = song,
                                    onClick = { onSongSelected(song, uiState.songs) },
                                    modifier = Modifier
                                        .initialFocus(firstItemFocus, enabled = index == 0)
                                        .holdFocusWhileLoadingMore(index == uiState.songs.lastIndex, uiState.songsLoadMore),
                                )
                            }
                            loadMoreFooter(uiState.songsLoadMore, onRetryLoadMoreSongs)
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
                                    modifier = Modifier
                                        .initialFocus(firstItemFocus, enabled = index == 0)
                                        .holdFocusWhileLoadingMore(index == uiState.albums.lastIndex, uiState.albumsLoadMore),
                                )
                            }
                            loadMoreFooter(uiState.albumsLoadMore, onRetryLoadMoreAlbums)
                        }
                    }

                    ArtistTab.Following -> {
                        if (uiState.following.isEmpty()) {
                            item(key = "empty_following") {
                                EmptyTab(uiState.followingError ?: "沒有追蹤中的音樂人", isError = uiState.followingError != null)
                            }
                        } else {
                            itemsIndexed(uiState.following, key = { _, it -> "following_${it.username}" }) { index, followed ->
                                ArtistListItem(
                                    artist = followed,
                                    onClick = { onArtistSelected(followed) },
                                    modifier = Modifier
                                        .initialFocus(firstItemFocus, enabled = index == 0)
                                        .holdFocusWhileLoadingMore(index == uiState.following.lastIndex, uiState.followingLoadMore),
                                )
                            }
                            loadMoreFooter(uiState.followingLoadMore, onRetryLoadMoreFollowing)
                        }
                    }
                }
            }
        }
    }
}

const val ARTIST_FOLLOW_BUTTON_TAG = "artist_follow_button"

/** 追蹤 (filled) / 追蹤中 (outlined). Stays focusable while a request runs (presses are ignored). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FollowButton(follow: FollowUiState, onClick: () -> Unit) {
    val modifier = Modifier.testTag(ARTIST_FOLLOW_BUTTON_TAG)
    val content: @Composable () -> Unit = {
        if (follow.inFlight) {
            CircularProgressIndicator(
                modifier = Modifier.size(ButtonDefaults.IconSize),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = if (follow.isFollowing) Icons.Default.Check else Icons.Default.PersonAdd,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        }
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(if (follow.isFollowing) "追蹤中" else "追蹤")
    }
    // One button whose colours change: swapping composables would drop the focus on toggle.
    val colors = if (follow.isFollowing) {
        ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        ButtonDefaults.colors()
    }
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = colors,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        content()
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
