package com.example.streetvoicetv.ui.player

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Visibility
import com.example.streetvoicetv.playback.RepeatMode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    onArtistSelected: (String) -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val loadingState by viewModel.loadingState.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val isLiked by viewModel.isLiked.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()

    // Keep screen on while playing
    KeepScreenOn()

    when {
        loadingState.isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("載入歌曲中...", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }

        loadingState.error != null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("播放錯誤", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
                    Text(loadingState.error!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { viewModel.retry() }) { Text("重試") }
                        Button(onClick = onBack) { Text("返回") }
                    }
                }
            }
        }

        loadingState.isLoaded && playbackState.song != null -> {
            val song = playbackState.song!!

            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Background: blurred album art
                if (song.imageUrl != null) {
                    AsyncImage(
                        model = song.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(60.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                            .drawWithContent {
                                drawContent()
                                // Dark scrim over blurred image
                                drawRect(Color.Black.copy(alpha = 0.7f))
                            },
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                }

                // Subtle gradient from bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                                startY = 0f,
                                endY = Float.MAX_VALUE,
                            )
                        )
                )

                // Content
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(40.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                ) {
                    // Left: Album art + controls
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // Album art with shadow
                        if (song.imageUrl != null) {
                            AsyncImage(
                                model = song.imageUrl,
                                contentDescription = song.name,
                                modifier = Modifier
                                    .size(220.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(220.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.MusicNote, null, Modifier.size(64.dp), tint = Color.White.copy(alpha = 0.5f))
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Song title
                        Text(
                            text = song.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            onClick = { onArtistSelected(song.artistUsername) },
                            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                            colors = ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = Color.White.copy(alpha = 0.15f),
                            ),
                        ) {
                            Text(
                                text = song.artistName,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        if (song.albumName != null) {
                            Text(
                                text = song.albumName,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f),
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Stats
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Visibility, null, Modifier.size(14.dp), tint = Color.White.copy(alpha = 0.5f))
                                Text(song.formattedPlaysCount, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                            }
                            if (song.likesCount > 0) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.Favorite, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("${song.likesCount}", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Progress bar
                        Column(modifier = Modifier.fillMaxWidth(0.9f)) {
                            LinearProgressIndicator(
                                progress = { playbackState.progress },
                                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = Color.White.copy(alpha = 0.15f),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(playbackState.formattedPosition, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                                Text(playbackState.formattedDuration, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Controls
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Button(onClick = { viewModel.toggleShuffle() }) {
                                Icon(
                                    Icons.Default.Shuffle,
                                    "隨機播放",
                                    tint = if (playbackState.shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White,
                                )
                            }
                            Button(
                                onClick = { viewModel.skipPrevious() },
                                enabled = playbackState.hasPrevious || playbackState.positionMs > 3000,
                            ) {
                                Icon(Icons.Default.SkipPrevious, "上一首")
                            }
                            Button(onClick = { viewModel.togglePlayPause() }) {
                                Icon(
                                    if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    if (playbackState.isPlaying) "暫停" else "播放",
                                    Modifier.size(32.dp),
                                )
                            }
                            Button(
                                onClick = { viewModel.skipNext() },
                                enabled = playbackState.hasNext,
                            ) {
                                Icon(Icons.Default.SkipNext, "下一首")
                            }
                            Button(onClick = { viewModel.toggleRepeatMode() }) {
                                Icon(
                                    when (playbackState.repeatMode) {
                                        RepeatMode.ONE -> Icons.Default.RepeatOne
                                        else -> Icons.Default.Repeat
                                    },
                                    "重複播放",
                                    tint = if (playbackState.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else Color.White,
                                )
                            }
                            if (isLoggedIn) {
                                Button(onClick = { viewModel.toggleLike() }) {
                                    Icon(
                                        if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        if (isLiked) "取消喜歡" else "喜歡",
                                        tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White,
                                    )
                                }
                            }
                        }
                        if (playbackState.queueSize > 1) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${playbackState.queueIndex + 1} / ${playbackState.queueSize}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.4f),
                            )
                        }
                    }

                    // Right: Lyrics / Synopsis
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        if (!song.synopsis.isNullOrBlank()) {
                            Text("關於", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = song.synopsis,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        if (!song.lyrics.isNullOrBlank()) {
                            Text("歌詞", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = song.lyrics,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
                            )
                        }

                        if (song.synopsis.isNullOrBlank() && song.lyrics.isNullOrBlank()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    "沒有簡介或歌詞",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.4f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** FLAG_KEEP_SCREEN_ON を再生画面表示中だけ設定する */
@Composable
private fun KeepScreenOn() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? ComponentActivity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
