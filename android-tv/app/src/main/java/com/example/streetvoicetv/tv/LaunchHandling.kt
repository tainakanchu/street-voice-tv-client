package com.example.streetvoicetv.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.example.streetvoicetv.playback.PlaybackManager
import com.example.streetvoicetv.playback.RestorableSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The song the resume deep link should open the player on: the loaded song (made to play),
 * else the saved session once it has loaded (resumed playing). Null when there is nothing to
 * resume within [timeoutMs].
 */
suspend fun resumeTargetSongId(
    currentSongId: () -> Int?,
    restorable: StateFlow<RestorableSession?>,
    playCurrent: () -> Unit,
    resumeLastSession: () -> Boolean,
    timeoutMs: Long = StartupAutoResume.WAIT_FOR_SESSION_MS,
): Int? {
    currentSongId()?.let {
        playCurrent()
        return it
    }
    val session = withTimeoutOrNull(timeoutMs) { restorable.first { it != null } } ?: return null
    currentSongId()?.let { return it } // started meanwhile
    return if (resumeLastSession()) session.currentSong.id else null
}

/**
 * Carries out [action] (from the launch / new intent) once: the resume deep link resumes the
 * last session and opens the player; a voice search opens the search screen with the query.
 */
@Composable
fun LaunchActionEffect(
    action: LaunchAction?,
    playbackManager: PlaybackManager,
    onOpenPlayer: (songId: Int) -> Unit,
    onOpenSearch: (query: String) -> Unit,
    onHandled: () -> Unit,
) {
    val openPlayer by rememberUpdatedState(onOpenPlayer)
    val openSearch by rememberUpdatedState(onOpenSearch)
    val handled by rememberUpdatedState(onHandled)
    LaunchedEffect(action) {
        when (action) {
            null -> return@LaunchedEffect
            is LaunchAction.Search -> openSearch(action.query)
            LaunchAction.ResumeLastSession -> {
                val songId = resumeTargetSongId(
                    currentSongId = { playbackManager.state.value.song?.id },
                    restorable = playbackManager.restorable,
                    playCurrent = { if (!playbackManager.state.value.isPlaying) playbackManager.resume() },
                    resumeLastSession = { playbackManager.resumeLastSession(play = true) },
                )
                if (songId != null) openPlayer(songId)
            }
        }
        handled()
    }
}
