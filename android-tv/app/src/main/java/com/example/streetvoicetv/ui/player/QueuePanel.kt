package com.example.streetvoicetv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.streetvoicetv.playback.QueueItem
import com.example.streetvoicetv.playback.QueueView
import com.example.streetvoicetv.playback.RepeatMode
import com.example.streetvoicetv.ui.components.MessageState
import com.example.streetvoicetv.ui.components.requestFocusWhenReady
import com.example.streetvoicetv.ui.theme.Dimens
import com.example.streetvoicetv.ui.theme.LocalBottomOverlayInset

/** Test tag of the queue panel (right-hand side of the player). */
const val QUEUE_PANEL_TAG = "player_queue_panel"

/** Test tag of the queue list (LazyColumn) inside [QUEUE_PANEL_TAG]. */
const val QUEUE_LIST_TAG = "player_queue_list"

/** Test tag of a queue row: `player_queue_row_<queueIndex>`. */
fun queueRowTag(queueIndex: Int) = "player_queue_row_$queueIndex"

/** Test tag of the "下一首：…" hint below the controls. */
const val NEXT_SONG_HINT_TAG = "player_next_song_hint"

/** Label of the "正在播放" marker on the current queue row. */
const val NOW_PLAYING_LABEL = "正在播放"

/**
 * The play queue (播放佇列) in play order: played songs dimmed above, the current song selected
 * with a 「正在播放」 marker, then what comes next. Selecting a row jumps to that song.
 *
 * When opened, the list starts scrolled so the current song is near the top (one played song
 * above it for context). With [focusOnOpen], focus lands on the next song (or the current one
 * when nothing follows); re-entering the list from the controls returns to the row focus left.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun QueuePanel(
    queue: QueueView,
    onSelect: (queueIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    focusOnOpen: Boolean = false,
    onOpenFocusHandled: () -> Unit = {},
) {
    Column(modifier = modifier.testTag(QUEUE_PANEL_TAG)) {
        QueueHeader(queue)
        if (queue.isEmpty) {
            MessageState(
                title = "佇列是空的",
                message = "從歌曲清單選擇歌曲即可加入播放佇列",
                requestInitialFocus = false,
                modifier = Modifier.weight(1f),
            )
            LaunchedEffect(Unit) { onOpenFocusHandled() }
            return@Column
        }

        val current = queue.currentPosition.coerceAtLeast(0)
        val target = if (queue.next != null) current + 1 else current
        val targetFocus = remember { FocusRequester() }
        // Created once per opening of the panel: current song near the top.
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (current - 1).coerceAtLeast(0))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(QUEUE_LIST_TAG)
                // Coming back from the controls returns to the row focus left from; the first
                // time it lands on the next song.
                .focusRestorer(targetFocus),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = 8.dp + LocalBottomOverlayInset.current,
                start = 4.dp,
                end = 4.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(queue.items, key = { _, item -> item.queueIndex }) { position, item ->
                QueueRow(
                    item = item,
                    isCurrent = position == queue.currentPosition,
                    isPlayed = queue.isPlayed(position),
                    onClick = { onSelect(item.queueIndex) },
                    modifier = if (position == target) Modifier.focusRequester(targetFocus) else Modifier,
                )
            }
            if (queue.wrapsAround) {
                item(key = "footer") {
                    Text(
                        text = if (queue.reshufflesAtEnd) "播完後將重新隨機播放整個佇列" else "播完後將從頭重複播放",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        if (focusOnOpen) {
            LaunchedEffect(Unit) {
                targetFocus.requestFocusWhenReady()
                onOpenFocusHandled()
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QueueHeader(queue: QueueView) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = primary, modifier = Modifier.size(22.dp))
        Text("播放佇列", style = MaterialTheme.typography.titleMedium, color = primary)
        Box(Modifier.weight(1f))
        if (queue.shuffleEnabled) {
            ModeChip(Icons.Default.Shuffle, "隨機")
        }
        when (queue.repeatMode) {
            RepeatMode.ALL -> ModeChip(Icons.Default.Repeat, "全部重複")
            RepeatMode.ONE -> ModeChip(Icons.Default.RepeatOne, "單曲重複")
            RepeatMode.OFF -> Unit
        }
        if (!queue.isEmpty) {
            Text(
                text = "還有 ${queue.upcomingCount} 首",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ModeChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QueueRow(
    item: QueueItem,
    isCurrent: Boolean,
    isPlayed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = item.song
    var focused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    ListItem(
        selected = isCurrent,
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag(queueRowTag(item.queueIndex))
            .onFocusChanged { focused = it.isFocused }
            // Already played: dimmed (fully readable again while focused).
            .alpha(if (isPlayed && !focused) 0.5f else 1f),
        scale = ListItemDefaults.scale(focusedScale = Dimens.ListItemFocusedScale),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            selectedContainerColor = primary.copy(alpha = 0.22f),
            selectedContentColor = Color.White,
        ),
        headlineContent = {
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = song.artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingContent = {
            QueueArt(imageUrl = song.imageUrl, isCurrent = isCurrent, contentDescription = song.name)
        },
        trailingContent = {
            if (isCurrent) {
                Text(
                    text = NOW_PLAYING_LABEL,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    modifier = Modifier
                        .background(primary, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            } else {
                Text(
                    text = song.formattedDuration,
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalContentColor.current,
                    maxLines = 1,
                )
            }
        },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QueueArt(imageUrl: String?, isCurrent: Boolean, contentDescription: String) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier.size(44.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(Icons.Default.MusicNote, null, Modifier.size(22.dp), tint = Color.White.copy(alpha = 0.5f))
        }
        if (isCurrent) {
            // "Playing" overlay on the art of the current song.
            Box(
                Modifier
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.GraphicEq, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
