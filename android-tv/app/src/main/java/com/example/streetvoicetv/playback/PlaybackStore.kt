package com.example.streetvoicetv.playback

import androidx.compose.runtime.Immutable
import com.example.streetvoicetv.domain.model.Song

/**
 * The last playback session, as saved by [PlaybackManager] (see [PlaybackManager.restorable]).
 * [songs] carry enough metadata to render rows / the player without refetching; lyrics and
 * synopsis are not saved (the player refetches the song detail).
 */
@Immutable
data class RestorableSession(
    val queue: QueueSnapshot,
    /** Position in the current song (ms) when saved. */
    val positionMs: Long,
    /** Wall-clock time (epoch ms) of the save. */
    val savedAtMs: Long,
) {
    val songs: List<Song> get() = queue.songs
    val currentSong: Song get() = queue.songs[queue.currentIndex]
    val currentIndex: Int get() = queue.currentIndex
}

/** A song of [PlaybackManager.recentlyPlayed] with when it was (last) played (epoch ms). */
@Immutable
data class RecentlyPlayedEntry(val song: Song, val playedAtMs: Long)

/**
 * Local persistence of the playback session and the recently played list. Implementations do
 * blocking I/O off the main thread themselves (they are suspend functions called from the main
 * thread); failures are swallowed (load returns null / empty).
 */
interface PlaybackStore {
    suspend fun loadSession(): RestorableSession?
    suspend fun saveSession(session: RestorableSession)
    suspend fun loadRecentlyPlayed(): List<RecentlyPlayedEntry>
    suspend fun saveRecentlyPlayed(entries: List<RecentlyPlayedEntry>)

    /** Keeps nothing (tests / previews). */
    object None : PlaybackStore {
        override suspend fun loadSession(): RestorableSession? = null
        override suspend fun saveSession(session: RestorableSession) = Unit
        override suspend fun loadRecentlyPlayed(): List<RecentlyPlayedEntry> = emptyList()
        override suspend fun saveRecentlyPlayed(entries: List<RecentlyPlayedEntry>) = Unit
    }
}
