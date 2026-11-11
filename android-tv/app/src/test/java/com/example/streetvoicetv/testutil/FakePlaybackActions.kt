package com.example.streetvoicetv.testutil

import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.playback.QueueSnapshot
import com.example.streetvoicetv.playback.RecentlyPlayedEntry
import com.example.streetvoicetv.playback.RestorableSession
import com.example.streetvoicetv.ui.common.PlaybackActions
import kotlinx.coroutines.flow.MutableStateFlow

/** [PlaybackActions] for ViewModel / UI tests; records every call in [calls]. */
class FakePlaybackActions(
    session: RestorableSession? = null,
    recent: List<RecentlyPlayedEntry> = emptyList(),
) : PlaybackActions {
    override val restorable = MutableStateFlow(session)
    override val recentlyPlayedEntries = MutableStateFlow(recent)
    val calls = mutableListOf<String>()

    override fun resumeLastSession(play: Boolean): Boolean {
        calls += "resumeLastSession(play=$play)"
        val had = restorable.value != null
        // Like the manager: once a queue is active nothing is restorable any more.
        restorable.value = null
        return had
    }

    override fun playNext(song: Song) {
        calls += "playNext(${song.id})"
    }

    override fun addToQueue(song: Song) {
        calls += "addToQueue(${song.id})"
    }
}

/** A saved session of [songs] at [currentIndex], [positionMs] into the current song. */
fun restorableSession(songs: List<Song>, currentIndex: Int = 0, positionMs: Long = 0L) = RestorableSession(
    queue = QueueSnapshot(
        songs = songs,
        currentIndex = currentIndex,
        shuffleEnabled = false,
        shuffleOrder = songs.indices.toList(),
        shufflePosition = currentIndex,
        autoplayFlags = List(songs.size) { false },
    ),
    positionMs = positionMs,
    savedAtMs = 0L,
)
