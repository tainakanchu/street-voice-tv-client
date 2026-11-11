package com.example.streetvoicetv.ui.components

import android.view.KeyEvent as NativeKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.focusGroup
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.DenseListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.example.streetvoicetv.domain.model.Song

/** Test tag of the song context menu panel. */
const val SONG_MENU_TAG = "song_menu"

/** Opens the song context menu for a song; [returnFocus] gets the focus back when it closes. */
fun interface SongMenuOpener {
    fun open(song: Song, returnFocus: FocusRequester)
}

/** The opener of the enclosing [SongMenuLayer]; null outside one (no context menu then). */
val LocalSongMenu = staticCompositionLocalOf<SongMenuOpener?> { null }

private fun isConfirmKey(keyCode: Int) = keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER ||
    keyCode == NativeKeyEvent.KEYCODE_ENTER || keyCode == NativeKeyEvent.KEYCODE_NUMPAD_ENTER

/**
 * Opens the song context menu for [song] on a long press of OK (DPAD_CENTER / ENTER held: the
 * remote repeats the key down) or on the MENU key. The long press is consumed, including its
 * key up, so the element's normal click does not fire as well. No-op outside a [SongMenuLayer].
 * Put it on the focusable element (before the clickable, e.g. as a Surface's modifier).
 */
@Composable
fun Modifier.songMenuTrigger(song: Song): Modifier {
    val opener = LocalSongMenu.current ?: return this
    val currentSong by rememberUpdatedState(song)
    val requester = remember { FocusRequester() }
    // Plain holder: read and written only from key handlers (no recomposition needed).
    val longPress = remember { BooleanArray(1) }
    return this
        .focusRequester(requester)
        .onFocusChanged { if (!it.isFocused) longPress[0] = false }
        .onPreviewKeyEvent { event ->
            val native = event.nativeKeyEvent
            when {
                native.keyCode == NativeKeyEvent.KEYCODE_MENU -> {
                    if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) {
                        opener.open(currentSong, requester)
                    }
                    true
                }
                !isConfirmKey(native.keyCode) -> false
                event.type == KeyEventType.KeyDown && native.repeatCount == 0 && !native.isLongPress -> {
                    // A new press: an earlier long press whose key up went elsewhere is over.
                    longPress[0] = false
                    false
                }
                event.type == KeyEventType.KeyDown -> {
                    if (!longPress[0]) {
                        longPress[0] = true
                        opener.open(currentSong, requester)
                    }
                    true
                }
                event.type == KeyEventType.KeyUp && longPress[0] -> {
                    longPress[0] = false
                    true
                }
                else -> false
            }
        }
}

/**
 * Hosts the song context menu over [content]: provides [LocalSongMenu] (so [songMenuTrigger]
 * works in every song row / card below), keeps focus out of [content] while the menu is open
 * and gives it back to the song that opened the menu when it closes. Navigation entries
 * (前往音樂人 / 前往專輯) run after focus is back on the song, like a click on it would.
 */
@Composable
fun SongMenuLayer(
    menu: SongMenuState?,
    onOpen: (Song) -> Unit,
    /** An entry was chosen for the song (navigation entries: after the menu closed). */
    onAction: (SongMenuAction, Song) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    var returnFocus by remember { mutableStateOf<FocusRequester?>(null) }
    var afterClose by remember { mutableStateOf<(() -> Unit)?>(null) }
    var wasOpen by remember { mutableStateOf(false) }
    val contentFocus = remember { FocusRequester() }
    val currentOnOpen by rememberUpdatedState(onOpen)
    val opener = remember {
        SongMenuOpener { song, focus ->
            returnFocus = focus
            currentOnOpen(song)
        }
    }
    val open = menu != null

    LaunchedEffect(open) {
        if (open) {
            wasOpen = true
        } else if (wasOpen) {
            wasOpen = false
            val restored = returnFocus?.requestFocusWhenReady(maxFrames = 3) == true
            if (!restored) contentFocus.requestFocusWhenReady()
            returnFocus = null
            afterClose?.let {
                afterClose = null
                it()
            }
        }
    }

    CompositionLocalProvider(LocalSongMenu provides opener) {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(contentFocus)
                    .focusRestorer()
                    .focusProperties { onEnter = { if (open) cancelFocusChange() } }
                    .focusGroup(),
            ) {
                content()
            }
            if (menu != null) {
                SongContextMenu(
                    menu = menu,
                    onAction = { action ->
                        val song = menu.song
                        when (action) {
                            SongMenuAction.GoToArtist, SongMenuAction.GoToAlbum -> {
                                afterClose = { onAction(action, song) }
                                onDismiss()
                            }
                            else -> onAction(action, song)
                        }
                    },
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/**
 * [SongMenuLayer] wired to the activity's [SongMenuViewModel]: its messages go to
 * [LocalAppMessages], navigation entries to [onGoToArtist] / [onGoToAlbum].
 */
@Composable
fun SongMenuHost(
    viewModel: SongMenuViewModel,
    onGoToArtist: (username: String) -> Unit,
    onGoToAlbum: (albumId: Int) -> Unit,
    content: @Composable () -> Unit,
) {
    val menu by viewModel.menu.collectAsStateWithLifecycle()
    val messages = LocalAppMessages.current
    LaunchedEffect(viewModel, messages) {
        viewModel.messages.collect { messages?.show(it) }
    }
    SongMenuLayer(
        menu = menu,
        onOpen = viewModel::open,
        onAction = { action, song ->
            when (action) {
                SongMenuAction.GoToArtist -> onGoToArtist(song.artistUsername)
                SongMenuAction.GoToAlbum -> song.albumId?.let(onGoToAlbum)
                else -> viewModel.onAction(action)
            }
        },
        onDismiss = viewModel::dismiss,
        content = content,
    )
}

/**
 * The menu itself: a modal panel (focus trapped, Back / MENU close it, focus starts on the
 * first entry). OK presses that did not start inside the menu (the key up / repeats of the
 * long press that opened it) are swallowed, so they never trigger an entry.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SongContextMenu(
    menu: SongMenuState,
    onAction: (SongMenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val firstFocus = remember { FocusRequester() }
    // Whether an OK press started inside the menu (its key up may then click an entry).
    val armed = remember { BooleanArray(1) }
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                when {
                    native.keyCode == NativeKeyEvent.KEYCODE_MENU -> {
                        if (event.type == KeyEventType.KeyDown && native.repeatCount == 0) onDismiss()
                        true
                    }
                    !isConfirmKey(native.keyCode) -> false
                    event.type == KeyEventType.KeyDown -> {
                        if (native.repeatCount == 0) {
                            armed[0] = true
                            false
                        } else {
                            true
                        }
                    }
                    event.type == KeyEventType.KeyUp -> {
                        val wasArmed = armed[0]
                        armed[0] = false
                        !wasArmed
                    }
                    else -> false
                }
            }
            .then(ModalFocusTrap),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .width(440.dp)
                .testTag(SONG_MENU_TAG),
        ) {
            Column(Modifier.padding(vertical = 20.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = menu.song.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = menu.song.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = menu.song.artistName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    menu.actions.forEachIndexed { index, action ->
                        DenseListItem(
                            selected = false,
                            onClick = { onAction(action) },
                            headlineContent = { Text(action.label, maxLines = 1) },
                            leadingContent = {
                                Icon(action.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            scale = listItemScale(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { firstFocus.requestFocusWhenReady() }
}

private fun SongMenuAction.icon(): ImageVector = when (this) {
    SongMenuAction.PlayNext -> Icons.AutoMirrored.Filled.PlaylistPlay
    SongMenuAction.AddToQueue -> Icons.AutoMirrored.Filled.PlaylistAdd
    SongMenuAction.GoToArtist -> Icons.Default.Person
    SongMenuAction.GoToAlbum -> Icons.Default.Album
    SongMenuAction.Like -> Icons.Default.FavoriteBorder
    SongMenuAction.Unlike -> Icons.Default.Favorite
}
