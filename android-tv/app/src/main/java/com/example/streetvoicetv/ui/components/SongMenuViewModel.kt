package com.example.streetvoicetv.ui.components

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import com.example.streetvoicetv.playback.PlaybackManager
import com.example.streetvoicetv.ui.common.PlaybackActions
import com.example.streetvoicetv.ui.common.PlaybackManagerActions
import com.example.streetvoicetv.ui.common.UserMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Entries of the song context menu (in display order). */
enum class SongMenuAction(val label: String) {
    PlayNext("下一首播放"),
    AddToQueue("加入佇列"),
    GoToArtist("前往音樂人"),
    GoToAlbum("前往專輯"),
    Like("喜歡"),
    Unlike("取消喜歡"),
}

/** The open song context menu. */
@Immutable
data class SongMenuState(
    val song: Song,
    val isLoggedIn: Boolean,
    /** Liked by the user (as far as known: the list item, an earlier toggle, or the song detail). */
    val isLiked: Boolean,
) {
    /** The entries shown: 前往專輯 only with a known album, 喜歡 / 取消喜歡 only when logged in. */
    val actions: List<SongMenuAction> = buildList {
        add(SongMenuAction.PlayNext)
        add(SongMenuAction.AddToQueue)
        add(SongMenuAction.GoToArtist)
        if (song.albumId != null && song.albumId > 0) add(SongMenuAction.GoToAlbum)
        if (isLoggedIn) add(if (isLiked) SongMenuAction.Unlike else SongMenuAction.Like)
    }
}

/**
 * The song context menu (long-press OK / MENU on a song row or card), one per activity.
 * Queue edits go to the [PlaybackActions]; 喜歡 / 取消喜歡 are optimistic (the menu and later
 * menus show the new state at once; a failure reverts it and reports [UserMessages.LIKE_FAILED]).
 * 前往音樂人 / 前往專輯 are navigation: the UI handles them (see [SongMenuLayer]).
 * Short confirmations (「已加入佇列：…」) are emitted on [messages].
 */
@HiltViewModel
class SongMenuViewModel internal constructor(
    private val playback: PlaybackActions,
    private val repository: StreetVoiceRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    @Inject
    constructor(
        playbackManager: PlaybackManager,
        repository: StreetVoiceRepository,
        sessionManager: SessionManager,
    ) : this(PlaybackManagerActions(playbackManager), repository, sessionManager)

    private val _menu = MutableStateFlow<SongMenuState?>(null)

    /** The open menu, or null. */
    val menu: StateFlow<SongMenuState?> = _menu.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** One-off confirmations / errors to show briefly (each is delivered once). */
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Like states set from this menu (song id → liked); list songs rarely know theirs. */
    private val likeOverrides = mutableMapOf<Int, Boolean>()

    /** The song detail fetch of the open menu (like state / album). */
    private var detailJob: Job? = null

    fun open(song: Song) {
        detailJob?.cancel()
        val loggedIn = sessionManager.isLoggedIn.value
        val override = likeOverrides[song.id]
        _menu.value = SongMenuState(song, isLoggedIn = loggedIn, isLiked = override ?: song.isLiked)
        // List endpoints carry neither the like state nor (mostly) the album: the detail has both.
        val needsLike = loggedIn && override == null && !song.isLiked
        val needsAlbum = song.albumId == null
        if (!needsLike && !needsAlbum) return
        detailJob = viewModelScope.launch {
            val detail = repository.getSongDetail(song.id).getOrNull() ?: return@launch
            _menu.update { menu ->
                if (menu == null || menu.song.id != song.id) return@update menu
                menu.copy(
                    song = if (menu.song.albumId == null) menu.song.copy(albumId = detail.albumId, albumName = detail.albumName) else menu.song,
                    // A toggle in the meantime wins over the (older) server state.
                    isLiked = likeOverrides[song.id] ?: (menu.isLiked || detail.isLiked),
                )
            }
        }
    }

    fun dismiss() {
        detailJob?.cancel()
        _menu.value = null
    }

    /**
     * Runs [action] on the open menu's song and closes the menu. Navigation entries only close
     * it (the caller navigates).
     */
    fun onAction(action: SongMenuAction) {
        val menu = _menu.value ?: return
        val song = menu.song
        dismiss()
        when (action) {
            SongMenuAction.PlayNext -> {
                playback.playNext(song)
                message("下一首播放：${song.name}")
            }
            SongMenuAction.AddToQueue -> {
                playback.addToQueue(song)
                message("已加入佇列：${song.name}")
            }
            SongMenuAction.Like -> setLiked(song, true)
            SongMenuAction.Unlike -> setLiked(song, false)
            SongMenuAction.GoToArtist, SongMenuAction.GoToAlbum -> Unit
        }
    }

    /** The like state the menu would show for [song] (for tests / callers). */
    fun isLiked(song: Song): Boolean = likeOverrides[song.id] ?: song.isLiked

    private fun setLiked(song: Song, liked: Boolean) {
        val previous = likeOverrides[song.id]
        likeOverrides[song.id] = liked
        message(if (liked) "已加入喜歡：${song.name}" else "已取消喜歡：${song.name}")
        viewModelScope.launch {
            val result = if (liked) repository.likeSong(song.id) else repository.unlikeSong(song.id)
            if (result.isFailure && likeOverrides[song.id] == liked) {
                if (previous == null) likeOverrides.remove(song.id) else likeOverrides[song.id] = previous
                message(UserMessages.LIKE_FAILED)
            }
        }
    }

    private fun message(text: String) {
        _messages.trySend(text)
    }
}
