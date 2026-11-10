package com.example.streetvoicetv.ui.player

import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.playback.RepeatMode
import com.example.streetvoicetv.ui.components.MessageAction
import com.example.streetvoicetv.ui.components.MessageState
import com.example.streetvoicetv.ui.components.requestFocusWhenReady
import com.example.streetvoicetv.ui.theme.Dimens
import kotlinx.coroutines.launch

/** D-pad seek step on the progress bar. */
private const val SEEK_STEP_MS = 10_000L

/** Test tag of the focusable lyrics / synopsis panel. */
const val LYRICS_PANEL_TAG = "player_lyrics_panel"

/** Test tag of the focusable seek bar. */
const val SEEK_BAR_TAG = "player_seek_bar"

/** Scroll step for the lyrics panel per D-pad press. */
private const val LYRICS_SCROLL_STEP_PX = 240f

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

    PlayerContent(
        loadingState = loadingState,
        playbackState = playbackState,
        isLiked = isLiked,
        isLoggedIn = isLoggedIn,
        onRetry = { viewModel.retry() },
        onBack = onBack,
        onArtistSelected = onArtistSelected,
        onToggleShuffle = { viewModel.toggleShuffle() },
        onSkipPrevious = { viewModel.skipPrevious() },
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onSkipNext = { viewModel.skipNext() },
        onToggleRepeatMode = { viewModel.toggleRepeatMode() },
        onToggleLike = { viewModel.toggleLike() },
        onSeek = { viewModel.seekTo(it) },
    )
}

/** Stateless player content (testable without Hilt / ExoPlayer / network). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerContent(
    loadingState: PlayerLoadingState,
    playbackState: PlaybackState,
    isLiked: Boolean,
    isLoggedIn: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onArtistSelected: (String) -> Unit,
    onToggleShuffle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleRepeatMode: () -> Unit,
    onToggleLike: () -> Unit,
    onSeek: (Long) -> Unit = {},
    lyricsScrollState: ScrollState = rememberScrollState(),
) {
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
            MessageState(
                title = "播放錯誤",
                message = loadingState.error,
                isError = true,
                primaryAction = MessageAction("重試", onRetry),
                secondaryAction = MessageAction("返回", onBack),
            )
        }

        loadingState.isLoaded && playbackState.song != null -> {
            val song = playbackState.song

            Box(modifier = Modifier.fillMaxSize()) {
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

                // Content (inside the TV safe area)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimens.SafeHorizontal, vertical = Dimens.SafeVertical),
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                ) {
                    // Left: song info (flexible) + progress & controls (pinned at the bottom)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // Coming back from the lyrics panel returns to the element focus
                            // left from, not to whatever is level with the panel's centre.
                            .focusRestorer()
                            .focusGroup(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SongInfo(
                            playbackState = playbackState,
                            onArtistSelected = onArtistSelected,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SeekBar(
                            playbackState = playbackState,
                            onSeek = onSeek,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PlayerControls(
                            playbackState = playbackState,
                            isLiked = isLiked,
                            isLoggedIn = isLoggedIn,
                            onToggleShuffle = onToggleShuffle,
                            onSkipPrevious = onSkipPrevious,
                            onTogglePlayPause = onTogglePlayPause,
                            onSkipNext = onSkipNext,
                            onToggleRepeatMode = onToggleRepeatMode,
                            onToggleLike = onToggleLike,
                        )
                        if (playbackState.queueSize > 1) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "${playbackState.queueIndex + 1} / ${playbackState.queueSize}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                        }
                    }

                    // Right: Lyrics / Synopsis (D-pad scrollable)
                    LyricsPanel(
                        synopsis = song.synopsis,
                        lyrics = song.lyrics,
                        scrollState = lyricsScrollState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SongInfo(
    playbackState: PlaybackState,
    onArtistSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = playbackState.song ?: return
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Album art: at most 180dp, shrinks (keeping 1:1) when the text needs the space.
        val artModifier = Modifier
            .weight(1f, fill = false)
            .sizeIn(maxWidth = 180.dp, maxHeight = 180.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
        if (song.imageUrl != null) {
            AsyncImage(
                model = song.imageUrl,
                contentDescription = song.name,
                modifier = artModifier,
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = artModifier.background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MusicNote, null, Modifier.size(64.dp), tint = Color.White.copy(alpha = 0.5f))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = song.name,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            onClick = { onArtistSelected(song.artistUsername) },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = Color.White.copy(alpha = 0.8f),
                focusedContainerColor = Color.White.copy(alpha = 0.9f),
                focusedContentColor = Color.Black,
            ),
        ) {
            Text(
                text = song.artistName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        if (song.albumName != null) {
            Text(
                text = song.albumName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Stats
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Default.Visibility, null, Modifier.size(16.dp), tint = Color.White.copy(alpha = 0.7f))
                Text(song.formattedPlaysCount, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            }
            if (song.likesCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Favorite, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("${song.likesCount}", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

/**
 * Progress bar. Focusable: D-pad LEFT / RIGHT seeks -/+ 10 seconds.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeekBar(
    playbackState: PlaybackState,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val canSeek = playbackState.durationMs > 0

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .testTag(SEEK_BAR_TAG)
                .onFocusChanged { focused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    if (!canSeek || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val delta = when (event.key.nativeKeyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> -SEEK_STEP_MS
                        KeyEvent.KEYCODE_DPAD_RIGHT -> SEEK_STEP_MS
                        else -> return@onPreviewKeyEvent false
                    }
                    onSeek((playbackState.positionMs + delta).coerceIn(0L, playbackState.durationMs))
                    true
                }
                .focusable(enabled = canSeek),
            contentAlignment = Alignment.CenterStart,
        ) {
            val trackHeight = if (focused) 8.dp else 6.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
            )
            Box(
                Modifier
                    .fillMaxWidth(playbackState.progress.coerceIn(0f, 1f))
                    .height(trackHeight)
                    .clip(RoundedCornerShape(4.dp))
                    .background(primary),
            )
            if (focused) {
                // Thumb at the current position
                Row(Modifier.fillMaxWidth()) {
                    val p = playbackState.progress.coerceIn(0f, 1f)
                    if (p > 0f) Spacer(Modifier.weight(p))
                    Box(
                        Modifier
                            .offset(x = (-8).dp)
                            .size(16.dp)
                            .background(Color.White, CircleShape)
                            .border(2.dp, primary, CircleShape),
                    )
                    if (p < 1f) Spacer(Modifier.weight(1f - p))
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(playbackState.formattedPosition, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            if (focused) {
                Text("◀ ▶ 快轉 / 倒轉 10 秒", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            }
            Text(playbackState.formattedDuration, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerControls(
    playbackState: PlaybackState,
    isLiked: Boolean,
    isLoggedIn: Boolean,
    onToggleShuffle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleRepeatMode: () -> Unit,
    onToggleLike: () -> Unit,
) {
    val playPauseFocus = remember { FocusRequester() }
    val primary = MaterialTheme.colorScheme.primary

    Row(
        // Down from the seek bar returns to the control focus came from (play / pause by default).
        modifier = Modifier
            .focusRestorer(playPauseFocus)
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onToggleShuffle) {
            Icon(
                Icons.Default.Shuffle,
                "隨機播放",
                tint = if (playbackState.shuffleEnabled) primary else LocalContentColor.current,
            )
        }
        Button(
            onClick = onSkipPrevious,
            enabled = playbackState.hasPrevious || playbackState.positionMs > 3000,
        ) {
            Icon(Icons.Default.SkipPrevious, "上一首")
        }
        Button(
            onClick = onTogglePlayPause,
            modifier = Modifier.focusRequester(playPauseFocus),
        ) {
            Icon(
                if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (playbackState.isPlaying) "暫停" else "播放",
                Modifier.size(32.dp),
            )
        }
        Button(
            onClick = onSkipNext,
            enabled = playbackState.hasNext,
        ) {
            Icon(Icons.Default.SkipNext, "下一首")
        }
        Button(onClick = onToggleRepeatMode) {
            Icon(
                when (playbackState.repeatMode) {
                    RepeatMode.ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                },
                "重複播放",
                tint = if (playbackState.repeatMode != RepeatMode.OFF) primary else LocalContentColor.current,
            )
        }
        if (isLoggedIn) {
            Button(onClick = onToggleLike) {
                Icon(
                    if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (isLiked) "取消喜歡" else "喜歡",
                    tint = if (isLiked) primary else LocalContentColor.current,
                )
            }
        }
    }

    // Initial focus on play / pause.
    LaunchedEffect(Unit) { playPauseFocus.requestFocusWhenReady() }
}

/**
 * Synopsis + lyrics. The whole panel is one focus target: D-pad UP / DOWN scrolls it
 * (returning focus handling to the system at the ends), with a visible focus outline.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LyricsPanel(
    synopsis: String?,
    lyrics: String?,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    if (synopsis.isNullOrBlank() && lyrics.isNullOrBlank()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "沒有簡介或歌詞",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (focused) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .border(2.dp, if (focused) primary.copy(alpha = 0.8f) else Color.Transparent, shape)
            .testTag(LYRICS_PANEL_TAG)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val delta = when (event.key.nativeKeyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> -LYRICS_SCROLL_STEP_PX
                    KeyEvent.KEYCODE_DPAD_DOWN -> LYRICS_SCROLL_STEP_PX
                    else -> return@onPreviewKeyEvent false
                }
                val canScroll = if (delta < 0) scrollState.canScrollBackward else scrollState.canScrollForward
                if (canScroll) scope.launch { scrollState.animateScrollBy(delta) }
                canScroll
            }
            .focusable(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (!synopsis.isNullOrBlank()) {
                Text("關於", style = MaterialTheme.typography.titleMedium, color = primary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = synopsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (!lyrics.isNullOrBlank()) {
                Text("歌詞", style = MaterialTheme.typography.titleMedium, color = primary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = lyrics,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
        if (focused) {
            Text(
                "▲▼ 捲動",
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
