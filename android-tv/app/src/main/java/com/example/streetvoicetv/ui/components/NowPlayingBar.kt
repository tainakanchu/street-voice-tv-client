package com.example.streetvoicetv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.ui.theme.Dimens

/** Test tag of the NowPlayingBar container. */
const val NOW_PLAYING_BAR_TAG = "now_playing_bar"

/** Convenience overload taking the whole [PlaybackState]; prefer the narrow one (fewer recompositions). */
@Composable
fun NowPlayingBar(
    playbackState: PlaybackState,
    onTap: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = playbackState.song ?: return
    NowPlayingBar(
        song = song,
        isPlaying = playbackState.isPlaying,
        error = playbackState.error,
        onTap = onTap,
        onTogglePlayPause = onTogglePlayPause,
        modifier = modifier,
    )
}

/**
 * Compact "now playing" chip shown at the bottom-end of the screen.
 * Width is capped at [Dimens.NowPlayingBarMaxWidth]; long titles are ellipsized.
 * Takes only what it shows (no position), so playback progress never recomposes it.
 * On a playback [error] the artist line shows the error and the button retries.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NowPlayingBar(
    song: Song,
    isPlaying: Boolean,
    error: String?,
    onTap: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {

    Row(
        modifier = modifier
            .testTag(NOW_PLAYING_BAR_TAG)
            .widthIn(max = Dimens.NowPlayingBarMaxWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            onClick = onTap,
            modifier = Modifier.weight(1f, fill = false),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (song.imageUrl != null) {
                    AsyncImage(
                        model = song.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = LocalContentColor.current.copy(alpha = 0.6f),
                    )
                }
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = song.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = error ?: song.artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (error != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            LocalContentColor.current.copy(alpha = 0.7f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        IconButton(
            onClick = onTogglePlayPause,
            scale = IconButtonDefaults.scale(focusedScale = 1.05f),
        ) {
            Icon(
                imageVector = when {
                    error != null -> Icons.Default.Refresh
                    isPlaying -> Icons.Default.Pause
                    else -> Icons.Default.PlayArrow
                },
                contentDescription = when {
                    error != null -> "重試"
                    isPlaying -> "暫停"
                    else -> "播放"
                },
            )
        }
    }
}
