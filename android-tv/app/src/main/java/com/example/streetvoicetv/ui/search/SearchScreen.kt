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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.streetvoicetv.ui.components.SongListItem

private val HPad = 48.dp

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

    Column(modifier = Modifier.fillMaxSize()) {
        // Title (individually padded)
        Text(
            text = "StreetVoice",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = HPad, top = 32.dp),
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
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    androidx.compose.material3.Text("搜尋歌曲、音樂人與歌單...", color = Color.Gray)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray,
                ),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Button(onClick = { viewModel.search() }) {
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
                ErrorContent(
                    message = uiState.error!!,
                    onRetry = { viewModel.search() },
                    onDismiss = { viewModel.clearError() },
                )
            }

            uiState.isEmpty && uiState.query.isNotBlank() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("找不到「${uiState.query}」的結果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            uiState.hasResults -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = HPad, vertical = 8.dp),
                ) {
                    if (uiState.artists.isNotEmpty()) {
                        item { SectionHeader("音樂人") }
                        items(uiState.artists, key = { "artist_${it.id}" }) { artist ->
                            ArtistListItem(artist = artist, onClick = { onArtistSelected(artist) })
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }

                    if (uiState.playlists.isNotEmpty()) {
                        item { SectionHeader("歌單") }
                        items(uiState.playlists, key = { "playlist_${it.id}" }) { playlist ->
                            PlaylistListItem(playlist = playlist, onClick = { onPlaylistSelected(playlist) })
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }

                    if (uiState.songs.isNotEmpty()) {
                        item { SectionHeader("歌曲") }
                        items(uiState.songs, key = { "song_${it.id}" }) { song ->
                            SongListItem(song = song, onClick = { onSongSelected(song, uiState.songs) })
                        }
                    }
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
                            searchHistory.forEach { query ->
                                Surface(
                                    onClick = { viewModel.selectHistory(query) },
                                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(20.dp)),
                                ) {
                                    Text(
                                        text = query,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("在 StreetVoice 上搜尋音樂", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("發生錯誤", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry) { Text("重試") }
                Button(onClick = onDismiss) { Text("關閉") }
            }
        }
    }
}
