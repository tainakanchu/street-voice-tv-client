package com.example.streetvoicetv.ui.player

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/** Test tag of a synced lyric line: `player_lyric_line_<index>`. */
fun lyricLineTag(index: Int) = "player_lyric_line_$index"

/** After the last D-pad key in the lyrics, auto-follow resumes after this long. */
const val LYRICS_FOLLOW_RESUME_MS = 5_000L

/**
 * Synced (LRC) lyrics: the line being sung is highlighted and kept at about a third of the
 * panel height. [positionMs] is read only here (through a derived line index), so playback
 * ticks recompose this panel only when the line changes.
 *
 * The lines are focusable: entering the panel focuses the current line, UP / DOWN move
 * through the lines (manual scrolling; auto-follow resumes [LYRICS_FOLLOW_RESUME_MS] after
 * the last key, moving focus back to the current line), and OK seeks to the focused line.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SyncedLyricsPanel(
    lyrics: SyncedLyrics,
    synopsis: String?,
    positionMs: () -> Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    val currentIndex by remember(lyrics, positionMs) { derivedStateOf { lyrics.indexAt(positionMs()) } }
    val lineTops = remember(lyrics) { IntArray(lyrics.lines.size) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var hasFocus by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var keyCount by remember { mutableIntStateOf(0) }
    val currentLineFocus = remember { FocusRequester() }
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(12.dp)

    // Resume following the song a while after the last key.
    LaunchedEffect(keyCount) {
        if (!manual) return@LaunchedEffect
        delay(LYRICS_FOLLOW_RESUME_MS)
        manual = false
    }

    // Follow: keep the current line at ~1/3 of the height (focus follows it while focused).
    LaunchedEffect(currentIndex, manual, viewportHeight) {
        if (manual || viewportHeight == 0) return@LaunchedEffect
        if (hasFocus && currentIndex >= 0) currentLineFocus.requestFocus()
        val top = if (currentIndex >= 0) lineTops[currentIndex] else 0
        val target = (top - viewportHeight / 3).coerceIn(0, scrollState.maxValue)
        if (target != scrollState.value) scrollState.animateScrollTo(target)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (hasFocus) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .border(2.dp, if (hasFocus) primary.copy(alpha = 0.8f) else Color.Transparent, shape)
            .testTag(LYRICS_PANEL_TAG)
            .onSizeChanged { viewportHeight = it.height }
            .onFocusChanged { hasFocus = it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    manual = true
                    keyCount++
                }
                false
            }
            // Coming in from the controls lands on the line being sung.
            .focusProperties { onEnter = { currentLineFocus.requestFocus() } },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            if (!synopsis.isNullOrBlank()) {
                Text("關於", style = MaterialTheme.typography.titleMedium, color = primary)
                Spacer(Modifier.height(6.dp))
                Text(synopsis, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(20.dp))
            }
            Text("歌詞", style = MaterialTheme.typography.titleMedium, color = primary)
            Spacer(Modifier.height(6.dp))
            val focusIndex = currentIndex.coerceAtLeast(0)
            lyrics.lines.forEachIndexed { index, line ->
                LyricLine(
                    text = line.text,
                    isCurrent = index == currentIndex,
                    // Following resumes with the usual delay, by when the position has moved there.
                    onClick = { onSeekTo(line.timeMs) },
                    modifier = Modifier
                        .onPlaced { lineTops[index] = it.positionInParent().y.toInt() }
                        .testTag(lyricLineTag(index))
                        // The line being sung is marked selected (for accessibility and tests).
                        .semantics { selected = index == currentIndex }
                        .then(if (index == focusIndex) Modifier.focusRequester(currentLineFocus) else Modifier),
                )
            }
            // Room so the last lines can still be pulled up to the 1/3 mark.
            Spacer(Modifier.height(160.dp))
        }
        if (hasFocus) {
            Text(
                "▲▼ 選擇 · OK 跳至此句",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LyricLine(text: String, isCurrent: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (isCurrent) Color.White else Color.White.copy(alpha = 0.55f),
            focusedContainerColor = Color.White.copy(alpha = 0.16f),
            focusedContentColor = Color.White,
        ),
    ) {
        Text(
            text = text.ifBlank { "♪" },
            style = if (isCurrent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else androidx.tv.material3.LocalContentColor.current,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}
