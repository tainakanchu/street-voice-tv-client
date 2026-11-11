package com.example.streetvoicetv.ui.common

import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.playback.PlaybackManager
import com.example.streetvoicetv.playback.RecentlyPlayedEntry
import com.example.streetvoicetv.playback.RestorableSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What the browse screens (Home rows, the song context menu) use from [PlaybackManager].
 * A thin seam so their ViewModels can be tested with a fake instead of a whole manager;
 * [PlaybackManagerActions] is the production implementation (plain delegation).
 */
interface PlaybackActions {
    /** See [PlaybackManager.restorable]. */
    val restorable: StateFlow<RestorableSession?>

    /** See [PlaybackManager.recentlyPlayedEntries]. */
    val recentlyPlayedEntries: StateFlow<List<RecentlyPlayedEntry>>

    /** See [PlaybackManager.resumeLastSession]. */
    fun resumeLastSession(play: Boolean = true): Boolean

    /** See [PlaybackManager.playNext] (song). */
    fun playNext(song: Song)

    /** See [PlaybackManager.addToQueue]. */
    fun addToQueue(song: Song)

    /** Nothing restorable, no history; queue edits do nothing (previews / tests). */
    object None : PlaybackActions {
        override val restorable: StateFlow<RestorableSession?> = MutableStateFlow(null)
        override val recentlyPlayedEntries: StateFlow<List<RecentlyPlayedEntry>> = MutableStateFlow(emptyList())
        override fun resumeLastSession(play: Boolean) = false
        override fun playNext(song: Song) = Unit
        override fun addToQueue(song: Song) = Unit
    }
}

/** [PlaybackActions] backed by the app's [PlaybackManager]. */
class PlaybackManagerActions(private val manager: PlaybackManager) : PlaybackActions {
    override val restorable: StateFlow<RestorableSession?> get() = manager.restorable
    override val recentlyPlayedEntries: StateFlow<List<RecentlyPlayedEntry>> get() = manager.recentlyPlayedEntries
    override fun resumeLastSession(play: Boolean) = manager.resumeLastSession(play)
    override fun playNext(song: Song) = manager.playNext(song)
    override fun addToQueue(song: Song) = manager.addToQueue(song)
}
