package com.example.streetvoicetv.ui.player

import android.view.KeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.streetvoicetv.domain.model.Song

/** Test tag of the ambient (screensaver-like) overlay of the player. */
const val AMBIENT_TAG = "player_ambient"

/** Without key input this long while playing, the player switches to ambient mode. */
const val AMBIENT_TIMEOUT_MS = 45_000L

/** The ambient content moves to another spot about this often (burn-in protection). */
const val AMBIENT_SHIFT_INTERVAL_MS = 120_000L

/** Spots the ambient content cycles through (offsets from the centre, dp). */
private val AmbientSpots = listOf(0 to 0, -60 to -36, 60 to 30, -40 to 40, 50 to -40, 0 to 44)

/** Media keys act directly in ambient mode (and wake it); volume keys pass through untouched. */
internal fun isAmbientPassThroughKey(keyCode: Int): Boolean = isMediaKey(keyCode) || keyCode in setOf(
    KeyEvent.KEYCODE_VOLUME_UP,
    KeyEvent.KEYCODE_VOLUME_DOWN,
    KeyEvent.KEYCODE_VOLUME_MUTE,
)

internal fun isMediaKey(keyCode: Int): Boolean = keyCode in setOf(
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    KeyEvent.KEYCODE_MEDIA_PLAY,
    KeyEvent.KEYCODE_MEDIA_PAUSE,
    KeyEvent.KEYCODE_MEDIA_NEXT,
    KeyEvent.KEYCODE_MEDIA_PREVIOUS,
    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
    KeyEvent.KEYCODE_MEDIA_REWIND,
    KeyEvent.KEYCODE_HEADSETHOOK,
)

/**
 * Ambient mode: big art, title / artist, the lyric line being sung (synced lyrics only) and
 * 「下一首：」. The block moves to another spot every [AMBIENT_SHIFT_INTERVAL_MS] of playback
 * and on every song change, derived from the playback position (no timer of its own).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun AmbientOverlay(
    song: Song,
    nextTitle: String?,
    syncedLyrics: SyncedLyrics?,
    positionMs: () -> Long,
    modifier: Modifier = Modifier,
) {
    val spot by remember(song.id, positionMs) {
        derivedStateOf {
            val step = (positionMs() / AMBIENT_SHIFT_INTERVAL_MS).toInt()
            AmbientSpots[Math.floorMod(song.id + step, AmbientSpots.size)]
        }
    }
    val x by animateDpAsState(spot.first.dp, tween(2_000), label = "ambientX")
    val y by animateDpAsState(spot.second.dp, tween(2_000), label = "ambientY")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            .testTag(AMBIENT_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .offset(x = x, y = y)
                .padding(horizontal = 96.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            val artShape = RoundedCornerShape(20.dp)
            if (song.imageUrl != null) {
                AsyncImage(
                    model = song.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.size(260.dp).clip(artShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier.size(260.dp).clip(artShape).background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.MusicNote, null, Modifier.size(96.dp), tint = Color.White.copy(alpha = 0.5f))
                }
            }
            Column(modifier = Modifier.widthIn(max = 440.dp)) {
                Text(
                    text = song.name,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = song.artistName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (syncedLyrics != null) {
                    Spacer(Modifier.height(24.dp))
                    AmbientLyric(syncedLyrics, positionMs)
                }
                if (nextTitle != null) {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "下一首：$nextTitle",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AmbientLyric(lyrics: SyncedLyrics, positionMs: () -> Long) {
    val line by remember(lyrics, positionMs) {
        derivedStateOf { lyrics.lines.getOrNull(lyrics.indexAt(positionMs()))?.text.orEmpty() }
    }
    Text(
        text = line.ifBlank { "♪" },
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}
