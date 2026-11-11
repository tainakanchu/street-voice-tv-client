package com.example.streetvoicetv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song

/** Fixed height of the text block below a card image, so CJK / Latin cards align. */
private val CardTextBlockHeight = 60.dp

/** Vertical padding for LazyRows of [MediaCard]s, leaving room for the focus scale. */
val CardRowVerticalPadding: Dp = 12.dp

/**
 * Image card with a title + optional subtitle, used for songs and playlists
 * in horizontal carousels (Home / MyPage).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaCard(
    title: String,
    subtitle: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    width: Dp,
    imageHeight: Dp,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    /** Text badge on the image (e.g. 「3 天前」); shown instead of the rank. */
    badge: String? = null,
) {
    val badgeText = badge ?: rank?.let { "#$it" }
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        // Modest focus scale so a focused card stays clear of the section header
        // and (for the first card) close to the safe area.
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = modifier.width(width),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .size(width, imageHeight)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop,
                )
                if (badgeText != null) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CardTextBlockHeight)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun SongCard(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rank: Int? = null,
    badge: String? = null,
) {
    MediaCard(
        title = song.name,
        subtitle = song.artistName,
        imageUrl = song.imageUrl,
        onClick = onClick,
        width = 150.dp,
        imageHeight = 150.dp,
        rank = rank,
        badge = badge,
        // Long-press OK / MENU: the song context menu.
        modifier = modifier.songMenuTrigger(song),
    )
}

@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    MediaCard(
        title = playlist.name,
        subtitle = playlist.curatorName,
        imageUrl = playlist.imageUrl,
        onClick = onClick,
        width = 180.dp,
        imageHeight = 120.dp,
        modifier = modifier,
    )
}
