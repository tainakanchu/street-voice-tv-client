package com.example.streetvoicetv.data.local

import android.util.Log
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.playback.PlaybackStore
import com.example.streetvoicetv.playback.QueueSnapshot
import com.example.streetvoicetv.playback.RecentlyPlayedEntry
import com.example.streetvoicetv.playback.RestorableSession
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * [PlaybackStore] as two small JSON files in [directory] (app-private files dir; resolved
 * lazily on the I/O dispatcher, since `Context.getFilesDir` may touch the disk).
 * All file access runs on [ioDispatcher]; writes go to a temp file that replaces the old one,
 * so a crash mid-write never leaves a truncated file. Unreadable files load as nothing.
 */
class JsonFilePlaybackStore(
    private val directoryProvider: () -> File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PlaybackStore {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
    }
    private val mutex = Mutex()
    private val directory by lazy(directoryProvider)
    private val sessionFile get() = File(directory, SESSION_FILE)
    private val recentFile get() = File(directory, RECENT_FILE)

    override suspend fun loadSession(): RestorableSession? = read({ sessionFile }) {
        json.decodeFromString(StoredSession.serializer(), it).toSession()
    }

    override suspend fun saveSession(session: RestorableSession) =
        write({ sessionFile }, json.encodeToString(StoredSession.serializer(), StoredSession.from(session)))

    override suspend fun loadRecentlyPlayed(): List<RecentlyPlayedEntry> = read({ recentFile }) {
        json.decodeFromString(StoredRecent.serializer(), it).entries.map { e ->
            RecentlyPlayedEntry(e.song.toSong(), e.playedAtMs)
        }
    } ?: emptyList()

    override suspend fun saveRecentlyPlayed(entries: List<RecentlyPlayedEntry>) = write(
        { recentFile },
        json.encodeToString(
            StoredRecent.serializer(),
            StoredRecent(entries = entries.map { StoredRecentEntry(StoredSong.from(it.song), it.playedAtMs) }),
        ),
    )

    private suspend fun <T> read(fileOf: () -> File, decode: (String) -> T?): T? = withContext(ioDispatcher) {
        mutex.withLock {
            val file = fileOf()
            try {
                if (file.exists()) decode(file.readText()) else null
            } catch (e: Exception) {
                Log.w(TAG, "Ignoring unreadable ${file.name}", e)
                null
            }
        }
    }

    private suspend fun write(fileOf: () -> File, text: String): Unit = withContext(ioDispatcher) {
        mutex.withLock {
            val file = fileOf()
            try {
                directory.mkdirs()
                val tmp = File(directory, "${file.name}.tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
                Unit
            } catch (e: Exception) {
                Log.w(TAG, "Could not save ${file.name}", e)
            }
        }
    }

    private companion object {
        const val TAG = "PlaybackStore"
        const val SESSION_FILE = "playback_session.json"
        const val RECENT_FILE = "recently_played.json"
    }
}

@Serializable
internal data class StoredSong(
    val id: Int,
    val name: String,
    val artistName: String,
    val artistUsername: String,
    val imageUrl: String? = null,
    val durationSeconds: Double = 0.0,
    val playsCount: Int = 0,
    val genre: Int? = null,
    val likesCount: Int = 0,
    val albumId: Int? = null,
    val albumName: String? = null,
    val albumImageUrl: String? = null,
    val artistProfileImageUrl: String? = null,
) {
    fun toSong() = Song(
        id = id,
        name = name,
        artistName = artistName,
        artistUsername = artistUsername,
        imageUrl = imageUrl,
        durationSeconds = durationSeconds,
        playsCount = playsCount,
        genre = genre,
        likesCount = likesCount,
        albumId = albumId,
        albumName = albumName,
        albumImageUrl = albumImageUrl,
        artistProfileImageUrl = artistProfileImageUrl,
    )

    companion object {
        /** Lyrics / synopsis / like state are not kept (refetched with the song detail). */
        fun from(song: Song) = StoredSong(
            id = song.id,
            name = song.name,
            artistName = song.artistName,
            artistUsername = song.artistUsername,
            imageUrl = song.imageUrl,
            durationSeconds = song.durationSeconds.takeIf { it.isFinite() } ?: 0.0,
            playsCount = song.playsCount,
            genre = song.genre,
            likesCount = song.likesCount,
            albumId = song.albumId,
            albumName = song.albumName,
            albumImageUrl = song.albumImageUrl,
            artistProfileImageUrl = song.artistProfileImageUrl,
        )
    }
}

@Serializable
internal data class StoredSession(
    val version: Int = 1,
    val songs: List<StoredSong>,
    val currentIndex: Int,
    val shuffleEnabled: Boolean = false,
    val shuffleOrder: List<Int> = emptyList(),
    val shufflePosition: Int = -1,
    val autoplay: List<Boolean> = emptyList(),
    val positionMs: Long = 0,
    val savedAtMs: Long = 0,
) {
    fun toSession(): RestorableSession? {
        if (songs.isEmpty() || currentIndex !in songs.indices) return null
        return RestorableSession(
            queue = QueueSnapshot(
                songs = songs.map { it.toSong() },
                currentIndex = currentIndex,
                shuffleEnabled = shuffleEnabled,
                shuffleOrder = shuffleOrder,
                shufflePosition = shufflePosition,
                autoplayFlags = autoplay,
            ),
            positionMs = positionMs.coerceAtLeast(0),
            savedAtMs = savedAtMs,
        )
    }

    companion object {
        fun from(session: RestorableSession) = StoredSession(
            songs = session.queue.songs.map(StoredSong::from),
            currentIndex = session.queue.currentIndex,
            shuffleEnabled = session.queue.shuffleEnabled,
            shuffleOrder = session.queue.shuffleOrder,
            shufflePosition = session.queue.shufflePosition,
            autoplay = session.queue.autoplayFlags,
            positionMs = session.positionMs,
            savedAtMs = session.savedAtMs,
        )
    }
}

@Serializable
internal data class StoredRecentEntry(val song: StoredSong, val playedAtMs: Long)

@Serializable
internal data class StoredRecent(val version: Int = 1, val entries: List<StoredRecentEntry> = emptyList())
