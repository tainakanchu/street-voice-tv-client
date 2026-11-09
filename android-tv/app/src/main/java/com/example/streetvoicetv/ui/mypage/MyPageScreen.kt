package com.example.streetvoicetv.ui.mypage

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.ui.common.UserMessages
import com.example.streetvoicetv.ui.components.BlockFocusEntry
import com.example.streetvoicetv.ui.components.CardRowVerticalPadding
import com.example.streetvoicetv.ui.components.ConfirmDialog
import com.example.streetvoicetv.ui.components.MessageAction
import com.example.streetvoicetv.ui.components.MessageState
import com.example.streetvoicetv.ui.components.PlaylistCard
import com.example.streetvoicetv.ui.components.SongListItem
import com.example.streetvoicetv.ui.components.initialFocus
import com.example.streetvoicetv.ui.components.rememberInitialFocus
import com.example.streetvoicetv.ui.theme.Dimens
import com.example.streetvoicetv.ui.theme.LocalBottomOverlayInset

private val HPad = Dimens.SafeHorizontal

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MyPageScreen(
    onSongSelected: (Song, List<Song>) -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: MyPageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    MyPageContent(
        uiState = uiState,
        onSongSelected = onSongSelected,
        onPlaylistSelected = onPlaylistSelected,
        onLogout = {
            viewModel.logout()
            onLoggedOut()
        },
        onRetry = { viewModel.retry() },
    )
}

/** Stateless my-page content (testable without Hilt / network). */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MyPageContent(
    uiState: MyPageUiState,
    onSongSelected: (Song, List<Song>) -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onLogout: () -> Unit,
    onRetry: () -> Unit = {},
    initialShowLogoutConfirm: Boolean = false,
) {
    var showLogoutConfirm by rememberSaveable { mutableStateOf(initialShowLogoutConfirm) }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (showLogoutConfirm) BlockFocusEntry else Modifier),
        ) {
            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                uiState.profile == null -> {
                    MessageState(
                        message = UserMessages.PROFILE_FAILED,
                        isError = true,
                        primaryAction = MessageAction("重試", onRetry),
                        secondaryAction = MessageAction("登出") { showLogoutConfirm = true },
                        requestInitialFocus = !showLogoutConfirm,
                    )
                }

                else -> MyPageList(
                    uiState = uiState,
                    onSongSelected = onSongSelected,
                    onPlaylistSelected = onPlaylistSelected,
                    onLogoutClick = { showLogoutConfirm = true },
                    requestInitialFocus = !showLogoutConfirm,
                )
            }
        }

        if (showLogoutConfirm) {
            ConfirmDialog(
                title = "確定要登出嗎？",
                message = "登出後需要重新登入才能查看喜歡的歌曲與歌單。",
                confirmLabel = "登出",
                dismissLabel = "取消",
                onConfirm = {
                    showLogoutConfirm = false
                    onLogout()
                },
                onDismiss = { showLogoutConfirm = false },
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun MyPageList(
    uiState: MyPageUiState,
    onSongSelected: (Song, List<Song>) -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onLogoutClick: () -> Unit,
    requestInitialFocus: Boolean,
) {
    val profile = uiState.profile ?: return
    val bottomInset = LocalBottomOverlayInset.current
    // Initial focus: first playlist, else first liked song (never the logout button).
    val firstItemFocus = rememberInitialFocus()
    val hasItems = uiState.playlists.isNotEmpty() || uiState.likedSongs.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = Dimens.SafeVertical, bottom = Dimens.SafeVertical + bottomInset),
    ) {
        // Profile header
        item(key = "profile") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = HPad),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                AsyncImage(
                    model = profile.profileImageUrl,
                    contentDescription = profile.displayName,
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!profile.introduction.isNullOrBlank()) {
                        Text(
                            text = profile.introduction,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "粉絲 ${profile.followersCount}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "追蹤中 ${profile.followingCount}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedButton(onClick = onLogoutClick) {
                    Text("登出")
                }
            }
        }

        // Playlists
        if (uiState.playlists.isNotEmpty()) {
            item(key = "playlists_gap") { Spacer(Modifier.height(Dimens.SectionSpacing)) }
            item(key = "playlists_header") { SectionHeader("我的歌單") }
            item(key = "playlists") {
                LazyRow(
                    modifier = Modifier.focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = HPad, vertical = CardRowVerticalPadding),
                ) {
                    itemsIndexed(uiState.playlists, key = { _, it -> it.id }) { index, playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistSelected(playlist) },
                            modifier = Modifier.initialFocus(firstItemFocus.takeIf { requestInitialFocus }, enabled = index == 0),
                        )
                    }
                }
            }
        }

        // Liked songs
        if (uiState.likedSongs.isNotEmpty()) {
            item(key = "liked_gap") { Spacer(Modifier.height(Dimens.SectionSpacing)) }
            item(key = "liked_header") { SectionHeader("喜歡的歌曲") }
            itemsIndexed(uiState.likedSongs, key = { _, it -> "liked_${it.id}" }) { index, song ->
                val focusModifier = Modifier.initialFocus(
                    firstItemFocus.takeIf { requestInitialFocus },
                    enabled = index == 0 && uiState.playlists.isEmpty(),
                )
                SongListItem(
                    song = song,
                    onClick = { onSongSelected(song, uiState.likedSongs) },
                    modifier = Modifier
                        .padding(horizontal = HPad, vertical = 2.dp)
                        .then(focusModifier),
                )
            }
        }

        if (!hasItems) {
            item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp, start = HPad, end = HPad),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "還沒有喜歡的歌曲或歌單",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = HPad, end = HPad, bottom = 4.dp),
    )
}
