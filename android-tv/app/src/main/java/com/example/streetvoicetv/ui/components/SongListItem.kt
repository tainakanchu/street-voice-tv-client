package com.example.streetvoicetv.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.streetvoicetv.domain.model.Song

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SongListItem(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Chart position, shown before the cover (null: none). */
    rank: Int? = null,
) {
    ListItem(
        selected = false,
        onClick = onClick,
        // Long-press OK / MENU: the song context menu.
        modifier = modifier.songMenuTrigger(song).fillMaxWidth(),
        headlineContent = {
            Text(
                text = song.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = { SupportingRow(song.artistName, song.formattedDuration) },
        scale = listItemScale(),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (rank != null) {
                    Text(
                        text = rank.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        modifier = Modifier
                            .width(36.dp)
                            .padding(end = 12.dp),
                    )
                }
                AsyncImage(
                    model = song.imageUrl,
                    contentDescription = song.name,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        },
    )
}
