package com.example.streetvoicetv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.ui.components.ArtistListItem
import com.example.streetvoicetv.ui.components.PlaylistListItem
import com.example.streetvoicetv.ui.components.MessageAction
import com.example.streetvoicetv.ui.components.MessageState
import com.example.streetvoicetv.ui.components.SongListItem
import com.example.streetvoicetv.ui.components.requestFocusWhenReady
import com.example.streetvoicetv.ui.theme.Dimens
import com.example.streetvoicetv.ui.theme.LocalBottomOverlayInset
import com.example.streetvoicetv.ui.theme.screenListPadding

private val HPad = Dimens.SafeHorizontal

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onSongSelected: (Song, List<Song>) -> Unit,
    onArtistSelected: (Artist) -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchHistory by viewModel.history.collectAsState()

    SearchContent(
        uiState = uiState,
        searchHistory = searchHistory,
        onQueryChange = viewModel::onQueryChange,
        onSearch = { viewModel.search() },
        onClearError = { viewModel.clearError() },
        onSelectHistory = { viewModel.selectHistory(it) },
        onSongSelected = onSongSelected,
        onArtistSelected = onArtistSelected,
        onPlaylistSelected = onPlaylistSelected,
    )
}

/** Stateless search content (testable without Hilt / network). */
@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchContent(
    uiState: SearchUiState,
    searchHistory: List<String>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearError: () -> Unit,
    onSelectHistory: (String) -> Unit,
    onSongSelected: (Song, List<Song>) -> Unit,
    onArtistSelected: (Artist) -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
) {
    val bottomInset = LocalBottomOverlayInset.current

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "StreetVoice",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = HPad, top = Dimens.SafeVertical),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Search bar (individually padded)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    androidx.compose.material3.Text("搜尋歌曲、音樂人與歌單...", color = Color.Gray)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray,
                ),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Button(onClick = onSearch) {
                Text("搜尋")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            uiState.error != null -> {
                MessageState(
                    title = "發生錯誤",
                    message = uiState.error,
                    isError = true,
                    primaryAction = MessageAction("重試", onSearch),
                    secondaryAction = MessageAction("關閉", onClearError),
                    modifier = Modifier.padding(bottom = bottomInset),
                )
            }

            uiState.isEmpty && uiState.query.isNotBlank() -> {
                MessageState(
                    message = "找不到「${uiState.query.trim().ellipsize(MAX_QUERY_DISPLAY_CHARS)}」的結果",
                    modifier = Modifier.padding(bottom = bottomInset),
                )
            }

            uiState.hasResults -> {
                val firstResultFocus = remember { FocusRequester() }
                val firstSection = when {
                    uiState.artists.isNotEmpty() -> 0
                    uiState.playlists.isNotEmpty() -> 1
                    else -> 2
                }
                fun Modifier.firstResult(section: Int, index: Int): Modifier =
                    if (section == firstSection && index == 0) focusRequester(firstResultFocus) else this

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = screenListPadding(bottomInset, top = 8.dp),
                ) {
                    if (uiState.artists.isNotEmpty()) {
                        item(key = "h_artists") { SectionHeader("音樂人") }
                        itemsIndexed(uiState.artists, key = { _, it -> "artist_${it.id}" }) { index, artist ->
                            ArtistListItem(
                                artist = artist,
                                onClick = { onArtistSelected(artist) },
                                modifier = Modifier.firstResult(0, index),
                            )
                        }
                        item(key = "s_artists") { Spacer(modifier = Modifier.height(12.dp)) }
                    }

                    if (uiState.playlists.isNotEmpty()) {
                        item(key = "h_playlists") { SectionHeader("歌單") }
                        itemsIndexed(uiState.playlists, key = { _, it -> "playlist_${it.id}" }) { index, playlist ->
                            PlaylistListItem(
                                playlist = playlist,
                                onClick = { onPlaylistSelected(playlist) },
                                modifier = Modifier.firstResult(1, index),
                            )
                        }
                        item(key = "s_playlists") { Spacer(modifier = Modifier.height(12.dp)) }
                    }

                    if (uiState.songs.isNotEmpty()) {
                        item(key = "h_songs") { SectionHeader("歌曲") }
                        itemsIndexed(uiState.songs, key = { _, it -> "song_${it.id}" }) { index, song ->
                            SongListItem(
                                song = song,
                                onClick = { onSongSelected(song, uiState.songs) },
                                modifier = Modifier.firstResult(2, index),
                            )
                        }
                    }
                }

                // Move focus to the first result whenever a new result set arrives.
                LaunchedEffect(uiState.artists, uiState.playlists, uiState.songs) {
                    firstResultFocus.requestFocusWhenReady()
                }
            }

            else -> {
                if (searchHistory.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = HPad),
                    ) {
                        Text(
                            text = "最近搜尋",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            searchHistory.take(MAX_HISTORY_CHIPS).forEach { query ->
                                Surface(
                                    onClick = { onSelectHistory(query) },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                                    modifier = Modifier.widthIn(max = 280.dp),
                                ) {
                                    // Fixed height so CJK and Latin chips line up.
                                    Box(
                                        modifier = Modifier
                                            .height(40.dp)
                                            .padding(horizontal = 16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = query,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    MessageState(
                        message = "在 StreetVoice 上搜尋音樂",
                        modifier = Modifier.padding(bottom = bottomInset),
                    )
                }
            }
        }
    }
}

private const val MAX_HISTORY_CHIPS = 10
private const val MAX_QUERY_DISPLAY_CHARS = 30

private fun String.ellipsize(max: Int): String = if (length > max) take(max - 1) + "…" else this

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}
