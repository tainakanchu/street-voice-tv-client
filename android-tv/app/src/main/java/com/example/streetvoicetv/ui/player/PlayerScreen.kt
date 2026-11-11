package com.example.streetvoicetv.ui.player

import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ErrorOutline
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.playback.PlaybackManager
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.playback.QueueView
import com.example.streetvoicetv.playback.RepeatMode
import com.example.streetvoicetv.playback.SleepTimerOption
import com.example.streetvoicetv.playback.SleepTimerState
import com.example.streetvoicetv.playback.formatMs
import com.example.streetvoicetv.playback.progressOf
import com.example.streetvoicetv.ui.components.MessageAction
import com.example.streetvoicetv.ui.components.MessageState
import com.example.streetvoicetv.ui.components.requestFocusWhenReady
import com.example.streetvoicetv.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** D-pad seek step on the progress bar. */
private const val SEEK_STEP_MS = 10_000L

/** Test tag of the focusable lyrics / synopsis panel. */
const val LYRICS_PANEL_TAG = "player_lyrics_panel"

/** Test tag of the focusable seek bar. */
const val SEEK_BAR_TAG = "player_seek_bar"

/** Scroll step for the lyrics panel per D-pad press. */
private const val LYRICS_SCROLL_STEP_PX = 240f


/** Test tag of the playback error banner (error + 重試). */
const val PLAYBACK_ERROR_TAG = "player_playback_error"

/** Content description of the 佇列 (queue) toggle in the controls row. */
const val QUEUE_BUTTON_DESCRIPTION = "佇列"

/**
 * Icon-only control buttons: slimmer than tv-material's 58dp minimum width so all eight
 * (7 x 44 + 56 + 7 x 6 = 406dp) fit the left column (408dp at 960dp wide).
 */
private val ControlButtonPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
private val ControlButton = Modifier.width(44.dp)
private val PlayButtonWidth = 56.dp
private val ControlSpacing = 6.dp

/** Decode size (px) of the blurred background art. */
private const val BACKGROUND_DECODE_PX = 64


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
    val isDetailLoading by viewModel.isDetailLoading.collectAsState()
    val upNext by viewModel.upNext.collectAsState()
    // Collected here but only read (deferred) inside the progress UI, so a tick recomposes just that.
    val position = viewModel.positionMs.collectAsState()
    // Same for the sleep timer countdown (read only by the small status line / menu).
    val sleepTimer = viewModel.sleepTimer.collectAsState()

    // Keep the screen on only while playing (paused: let the TV screensaver start).
    KeepScreenOn(enabled = playbackState.isPlaying)

    Box(
        Modifier.onKeyEvent { event ->
            // Media keys that no focused element consumed and that did not go to the session.
            event.type == KeyEventType.KeyDown && viewModel.handleMediaKey(event.key.nativeKeyCode)
        },
    ) {
        PlayerContent(
            loadingState = loadingState,
            playbackState = playbackState,
            positionMs = { position.value },
            isLiked = isLiked,
            isLoggedIn = isLoggedIn,
            isDetailLoading = isDetailLoading,
            onRetry = { viewModel.retry() },
            onRetryPlayback = { viewModel.retryPlayback() },
            onBack = onBack,
            onArtistSelected = onArtistSelected,
            onToggleShuffle = { viewModel.toggleShuffle() },
            onSkipPrevious = { viewModel.skipPrevious() },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSkipNext = { viewModel.skipNext() },
            onToggleRepeatMode = { viewModel.toggleRepeatMode() },
            onToggleLike = { viewModel.toggleLike() },
            onSeekBy = { viewModel.seekBy(it) },
            upNext = upNext,
            onPlayQueueItem = { viewModel.playQueueItem(it) },
            onRemoveQueueItem = { viewModel.removeFromQueue(it) },
            onToggleAutoplay = { viewModel.toggleAutoplay() },
            sleepTimer = { sleepTimer.value },
            onSetSleepTimer = { viewModel.setSleepTimer(it) },
            onSeekTo = { viewModel.seekTo(it) },
        )
    }
}

/**
 * Stateless player content (testable without Hilt / ExoPlayer / network).
 *
 * [positionMs] is read lazily, only by the progress bar / time text, so position ticks do not
 * recompose the rest of the screen. [onSeekBy] gets the relative D-pad seek (ms).
 *
 * The 佇列 button switches the right-hand panel between the lyrics and the play queue
 * ([upNext]); Back while the queue is shown returns to the lyrics (and focus to the 佇列 button
 * if it was in the queue) instead of leaving the player. [initialQueueOpen] is for tests.
 *
 * Synced (LRC) lyrics are shown line by line following [positionMs] ([SyncedLyricsPanel]);
 * OK on a line calls [onSeekTo]. The 睡眠計時 button opens [SleepTimerMenu]; [sleepTimer] is
 * read lazily (it ticks every second) by the status line under the controls only.
 *
 * Ambient mode: after [ambientTimeoutMs] without a key while playing, the controls fade out
 * and [AmbientOverlay] is shown. The first key only wakes the player (focus stays where it
 * was); media keys still act (and wake it), volume keys pass through.
 */
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
    onSeekBy: (Long) -> Unit = {},
    positionMs: () -> Long = { playbackState.positionMs },
    isDetailLoading: Boolean = false,
    onRetryPlayback: () -> Unit = onRetry,
    lyricsScrollState: ScrollState = rememberScrollState(),
    songInfoProbe: () -> Unit = {},
    upNext: QueueView = QueueView(),
    onPlayQueueItem: (queueIndex: Int) -> Unit = {},
    initialQueueOpen: Boolean = false,
    onRemoveQueueItem: (queueIndex: Int) -> Unit = {},
    onToggleAutoplay: () -> Unit = {},
    sleepTimer: () -> SleepTimerState = { SleepTimerState.Off },
    onSetSleepTimer: (SleepTimerOption) -> Unit = {},
    onSeekTo: (Long) -> Unit = {},
    ambientTimeoutMs: Long = AMBIENT_TIMEOUT_MS,
    initialAmbient: Boolean = false,
    initialSleepMenuOpen: Boolean = false,
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
            var queueOpen by rememberSaveable { mutableStateOf(initialQueueOpen) }
            var focusQueueOnOpen by remember { mutableStateOf(false) }
            var rightPanelHasFocus by remember { mutableStateOf(false) }
            val queueButtonFocus = remember { FocusRequester() }
            val sleepButtonFocus = remember { FocusRequester() }
            var sleepMenuOpen by remember { mutableStateOf(initialSleepMenuOpen) }
            var refocusSleepButton by remember { mutableStateOf(false) }
            val syncedLyrics = remember(song.lyrics) { LrcParser.parse(song.lyrics) }
            val sleepActive by remember(sleepTimer) { derivedStateOf { sleepTimer().isActive } }

            // Ambient mode: entered after a while without keys while playing; any key leaves it.
            var ambient by remember { mutableStateOf(initialAmbient && playbackState.isPlaying) }
            var keyCount by remember { mutableIntStateOf(0) }
            var swallowKeyUp by remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(keyCount, playbackState.isPlaying, ambient, sleepMenuOpen) {
                if (!playbackState.isPlaying) {
                    ambient = false
                } else if (!ambient && !sleepMenuOpen) {
                    delay(ambientTimeoutMs)
                    ambient = true
                }
            }
            val controlsAlpha by animateFloatAsState(
                targetValue = if (ambient) 0f else 1f,
                animationSpec = tween(if (ambient) 1_200 else 250),
                label = "controlsAlpha",
            )

            // Back closes the queue (back to the lyrics) instead of leaving the player.
            BackHandler(enabled = queueOpen) {
                if (rightPanelHasFocus) queueButtonFocus.requestFocus()
                queueOpen = false
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        val code = event.key.nativeKeyCode
                        if (event.type == KeyEventType.KeyUp && swallowKeyUp == code) {
                            swallowKeyUp = null
                            return@onPreviewKeyEvent true
                        }
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        if (isAmbientPassThroughKey(code)) {
                            // Media keys act (and wake); volume keys are not UI input.
                            if (isMediaKey(code)) {
                                keyCount++
                                ambient = false
                            }
                            return@onPreviewKeyEvent false
                        }
                        keyCount++
                        if (ambient) {
                            // The first key only wakes the player.
                            ambient = false
                            swallowKeyUp = code
                            return@onPreviewKeyEvent true
                        }
                        false
                    },
            ) {
                PlayerBackground(imageUrl = song.imageUrl)

                // Content (inside the TV safe area)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = controlsAlpha }
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
                            song = song,
                            onArtistSelected = onArtistSelected,
                            probe = songInfoProbe,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        SeekBar(
                            positionMs = positionMs,
                            durationMs = playbackState.durationMs,
                            isLoading = playbackState.isLoading,
                            onSeekBy = onSeekBy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        // Previous restarts the song after 3 s, so it is enabled then even without a previous song.
                        val pastRestartThreshold by remember(positionMs) {
                            derivedStateOf { positionMs() > PlaybackManager.RESTART_THRESHOLD_MS }
                        }
                        PlayerControls(
                            isPlaying = playbackState.isPlaying,
                            shuffleEnabled = playbackState.shuffleEnabled,
                            repeatMode = playbackState.repeatMode,
                            hasNext = playbackState.hasNext,
                            previousEnabled = playbackState.hasPrevious || pastRestartThreshold,
                            isLiked = isLiked,
                            isLoggedIn = isLoggedIn,
                            onToggleShuffle = onToggleShuffle,
                            onSkipPrevious = onSkipPrevious,
                            onTogglePlayPause = onTogglePlayPause,
                            onSkipNext = onSkipNext,
                            onToggleRepeatMode = onToggleRepeatMode,
                            onToggleLike = onToggleLike,
                            queueOpen = queueOpen,
                            queueButtonFocus = queueButtonFocus,
                            onToggleQueue = {
                                focusQueueOnOpen = !queueOpen
                                queueOpen = !queueOpen
                            },
                            sleepActive = sleepActive,
                            sleepButtonFocus = sleepButtonFocus,
                            onOpenSleepTimer = { sleepMenuOpen = true },
                        )
                        if (sleepActive) {
                            Spacer(modifier = Modifier.height(6.dp))
                            SleepTimerStatus(sleepTimer)
                        }
                        if (playbackState.error != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            PlaybackErrorBanner(message = playbackState.error, onRetry = onRetryPlayback)
                        } else if (playbackState.queueSize > 1 || upNext.next != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            QueueStatusLine(
                                position = if (playbackState.queueSize > 1) {
                                    "${playbackState.queueIndex + 1} / ${playbackState.queueSize}"
                                } else {
                                    null
                                },
                                nextTitle = upNext.next?.song?.name,
                            )
                        }
                    }

                    // Right: lyrics / synopsis (D-pad scrollable), or the play queue.
                    val rightModifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .onFocusChanged { rightPanelHasFocus = it.hasFocus }
                    if (queueOpen) {
                        QueuePanel(
                            queue = upNext,
                            onSelect = onPlayQueueItem,
                            focusOnOpen = focusQueueOnOpen,
                            onOpenFocusHandled = { focusQueueOnOpen = false },
                            onRemove = onRemoveQueueItem,
                            onToggleAutoplay = onToggleAutoplay,
                            modifier = rightModifier,
                        )
                    } else if (syncedLyrics != null) {
                        SyncedLyricsPanel(
                            lyrics = syncedLyrics,
                            synopsis = song.synopsis,
                            positionMs = positionMs,
                            onSeekTo = onSeekTo,
                            modifier = rightModifier,
                        )
                    } else {
                        LyricsPanel(
                            synopsis = song.synopsis,
                            lyrics = song.lyrics,
                            isLoading = isDetailLoading,
                            scrollState = lyricsScrollState,
                            modifier = rightModifier,
                        )
                    }
                }

                AnimatedVisibility(visible = ambient, enter = fadeIn(tween(1_200)), exit = fadeOut(tween(250))) {
                    AmbientOverlay(
                        song = song,
                        nextTitle = upNext.next?.song?.name,
                        syncedLyrics = syncedLyrics,
                        positionMs = positionMs,
                    )
                }

                if (sleepMenuOpen) {
                    SleepTimerMenu(
                        state = sleepTimer(),
                        onSelect = {
                            onSetSleepTimer(it)
                            sleepMenuOpen = false
                            refocusSleepButton = true
                        },
                        onDismiss = {
                            sleepMenuOpen = false
                            refocusSleepButton = true
                        },
                    )
                }
                if (refocusSleepButton) {
                    // After the menu (and its focus trap) is gone.
                    LaunchedEffect(Unit) {
                        sleepButtonFocus.requestFocusWhenReady()
                        refocusSleepButton = false
                    }
                }
            }
        }
    }
}

/**
 * Blurred, darkened album art behind the player. Decoded tiny (the blur hides the detail
 * anyway), so it is cheap to load and still looks soft on API < 31 where blur() is a no-op.
 */
@Composable
private fun PlayerBackground(imageUrl: String?) {
    if (imageUrl == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val context = LocalContext.current
    val request = remember(imageUrl) {
        ImageRequest.Builder(context).data(imageUrl).size(BACKGROUND_DECODE_PX).build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .blur(12.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .drawWithContent {
                drawContent()
                // Dark scrim over blurred image
                drawRect(Color.Black.copy(alpha = 0.7f))
            },
        contentScale = ContentScale.Crop,
    )
}

/** "3 / 10 · 下一首：<title>" below the controls (single line; the title is ellipsized). */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QueueStatusLine(position: String?, nextTitle: String?) {
    val style = MaterialTheme.typography.labelMedium
    val dim = Color.White.copy(alpha = 0.7f)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (position != null) {
            Text(position, style = style, color = dim, maxLines = 1)
        }
        if (nextTitle != null) {
            if (position != null) Text("  ·  ", style = style, color = dim, maxLines = 1)
            Text(
                text = "下一首：$nextTitle",
                style = style,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .testTag(NEXT_SONG_HINT_TAG),
            )
        }
    }
}

/** Playback error (e.g. the stream failed mid-song) with a retry that resumes at the last position. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlaybackErrorBanner(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.testTag(PLAYBACK_ERROR_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Button(onClick = onRetry) {
            Text("重試")
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SongInfo(
    song: Song,
    onArtistSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    probe: () -> Unit = {},
) {
    SideEffect(probe) // recomposition probe for tests
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
 * [positionMs] is only read inside the small fill / thumb / time composables below.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SeekBar(
    positionMs: () -> Long,
    durationMs: Long,
    isLoading: Boolean,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val canSeek = durationMs > 0

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
                    onSeekBy(delta)
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
            ProgressFill(positionMs, durationMs, trackHeight, primary)
            if (focused) {
                // Thumb at the current position
                ProgressThumb(positionMs, durationMs, primary)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PositionText(positionMs)
            if (focused) {
                Text("◀ ▶ 快轉 / 倒轉 10 秒", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            } else if (isLoading) {
                Text("載入中…", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
            }
            Text(formatMs(durationMs), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun ProgressFill(positionMs: () -> Long, durationMs: Long, trackHeight: Dp, color: Color) {
    Box(
        Modifier
            .fillMaxWidth(progressOf(positionMs(), durationMs).coerceIn(0f, 1f))
            .height(trackHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(color),
    )
}

@Composable
private fun ProgressThumb(positionMs: () -> Long, durationMs: Long, color: Color) {
    Row(Modifier.fillMaxWidth()) {
        val p = progressOf(positionMs(), durationMs).coerceIn(0f, 1f)
        if (p > 0f) Spacer(Modifier.weight(p))
        Box(
            Modifier
                .offset(x = (-8).dp)
                .size(16.dp)
                .background(Color.White, CircleShape)
                .border(2.dp, color, CircleShape),
        )
        if (p < 1f) Spacer(Modifier.weight(1f - p))
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PositionText(positionMs: () -> Long) {
    // Recomposes only when the shown m:ss changes.
    val text by remember(positionMs) { derivedStateOf { formatMs(positionMs()) } }
    Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    hasNext: Boolean,
    previousEnabled: Boolean,
    isLiked: Boolean,
    isLoggedIn: Boolean,
    onToggleShuffle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleRepeatMode: () -> Unit,
    onToggleLike: () -> Unit,
    queueOpen: Boolean,
    queueButtonFocus: FocusRequester,
    onToggleQueue: () -> Unit,
    sleepActive: Boolean = false,
    sleepButtonFocus: FocusRequester = remember { FocusRequester() },
    onOpenSleepTimer: () -> Unit = {},
) {
    val playPauseFocus = remember { FocusRequester() }
    val primary = MaterialTheme.colorScheme.primary

    Row(
        // Down from the seek bar returns to the control focus came from (play / pause by default).
        modifier = Modifier
            .focusRestorer(playPauseFocus)
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(ControlSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onToggleShuffle, modifier = ControlButton, contentPadding = ControlButtonPadding) {
            Icon(
                Icons.Default.Shuffle,
                "隨機播放",
                tint = if (shuffleEnabled) primary else LocalContentColor.current,
            )
        }
        Button(
            onClick = onSkipPrevious,
            enabled = previousEnabled,
            modifier = ControlButton,
            contentPadding = ControlButtonPadding,
        ) {
            Icon(Icons.Default.SkipPrevious, "上一首")
        }
        Button(
            onClick = onTogglePlayPause,
            modifier = Modifier.width(PlayButtonWidth).focusRequester(playPauseFocus),
            contentPadding = ControlButtonPadding,
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                if (isPlaying) "暫停" else "播放",
                Modifier.size(32.dp),
            )
        }
        Button(
            onClick = onSkipNext,
            enabled = hasNext,
            modifier = ControlButton,
            contentPadding = ControlButtonPadding,
        ) {
            Icon(Icons.Default.SkipNext, "下一首")
        }
        Button(onClick = onToggleRepeatMode, modifier = ControlButton, contentPadding = ControlButtonPadding) {
            Icon(
                when (repeatMode) {
                    RepeatMode.ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                },
                "重複播放",
                tint = if (repeatMode != RepeatMode.OFF) primary else LocalContentColor.current,
            )
        }
        if (isLoggedIn) {
            Button(onClick = onToggleLike, modifier = ControlButton, contentPadding = ControlButtonPadding) {
                Icon(
                    if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    if (isLiked) "取消喜歡" else "喜歡",
                    tint = if (isLiked) primary else LocalContentColor.current,
                )
            }
        }
        Button(
            onClick = onOpenSleepTimer,
            modifier = ControlButton.focusRequester(sleepButtonFocus),
            contentPadding = ControlButtonPadding,
        ) {
            Icon(
                Icons.Default.Bedtime,
                SLEEP_TIMER_BUTTON_DESCRIPTION,
                tint = if (sleepActive) primary else LocalContentColor.current,
            )
        }
        // Switches the right-hand panel between the lyrics and the play queue.
        Button(
            onClick = onToggleQueue,
            modifier = ControlButton.focusRequester(queueButtonFocus),
            contentPadding = ControlButtonPadding,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                QUEUE_BUTTON_DESCRIPTION,
                tint = if (queueOpen) primary else LocalContentColor.current,
            )
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
    isLoading: Boolean = false,
) {
    if (synopsis.isNullOrBlank() && lyrics.isNullOrBlank()) {
        if (isLoading) {
            // Details still loading: don't flash "no lyrics".
            Box(modifier)
            return
        }
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


/** FLAG_KEEP_SCREEN_ON while [enabled] (playing) on the player screen. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = (context as? ComponentActivity)?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
