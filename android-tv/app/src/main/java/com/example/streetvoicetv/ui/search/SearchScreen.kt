package com.example.streetvoicetv.ui.search

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
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.ui.components.ArtistListItem
import com.example.streetvoicetv.ui.components.SongListItem

private val HPad = 48.dp

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    onSongSelected: (Song, List<Song>) -> Unit,
    onArtistSelected: (Artist) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

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
                    androidx.compose.material3.Text("Search songs & artists...", color = Color.Gray)
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
                Text("Search")
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
                    Text("No results for \"${uiState.query}\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            uiState.hasResults -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(horizontal = HPad, vertical = 8.dp),
                ) {
                    if (uiState.artists.isNotEmpty()) {
                        item { SectionHeader("Artists") }
                        items(uiState.artists, key = { "artist_${it.id}" }) { artist ->
                            ArtistListItem(artist = artist, onClick = { onArtistSelected(artist) })
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }

                    if (uiState.songs.isNotEmpty()) {
                        item { SectionHeader("Songs") }
                        items(uiState.songs, key = { "song_${it.id}" }) { song ->
                            SongListItem(song = song, onClick = { onSongSelected(song, uiState.songs) })
                        }
                    }
                }
            }

            else -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Search for music on StreetVoice", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("Something went wrong", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onRetry) { Text("Retry") }
                Button(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}
