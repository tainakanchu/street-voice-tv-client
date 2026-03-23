package com.example.streetvoicetv.ui.artist

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.streetvoicetv.domain.model.Album
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.ui.components.AlbumListItem
import com.example.streetvoicetv.ui.components.SongListItem

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ArtistScreen(
    onSongSelected: (Song, List<Song>) -> Unit,
    onAlbumSelected: (Album) -> Unit,
    onBack: () -> Unit,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    when {
        uiState.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        uiState.error != null && uiState.artist == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = uiState.error!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { viewModel.retry() }) { Text("Retry") }
                        Button(onClick = onBack) { Text("Back") }
                    }
                }
            }
        }

        uiState.artist != null -> {
            val artist = uiState.artist!!

            Column(modifier = Modifier.fillMaxSize()) {
                // Artist header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 48.dp, end = 48.dp, top = 32.dp),
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
                                text = "${artist.songsCount} songs",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${artist.followersCount} followers",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (!artist.introduction.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = artist.introduction,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab row
                val tabs = ArtistTab.entries.toList()
                val selectedIndex = tabs.indexOf(uiState.selectedTab)

                TabRow(
                    selectedTabIndex = selectedIndex,
                    modifier = Modifier.padding(horizontal = 48.dp),
                ) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = index == selectedIndex,
                            onFocus = { viewModel.selectTab(tab) },
                        ) {
                            Text(
                                text = when (tab) {
                                    ArtistTab.Songs -> "Songs (${uiState.songs.size})"
                                    ArtistTab.Albums -> "Albums (${uiState.albums.size})"
                                },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tab content
                when (uiState.selectedTab) {
                    ArtistTab.Songs -> {
                        if (uiState.songs.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No songs",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                            ) {
                                items(uiState.songs, key = { it.id }) { song ->
                                    SongListItem(
                                        song = song,
                                        onClick = { onSongSelected(song, uiState.songs) },
                                    )
                                }
                            }
                        }
                    }

                    ArtistTab.Albums -> {
                        if (uiState.albums.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No albums",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
                            ) {
                                items(uiState.albums, key = { it.id }) { album ->
                                    AlbumListItem(
                                        album = album,
                                        onClick = { onAlbumSelected(album) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
