package com.example.streetvoicetv.data.repository

import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.model.parsePublishAt
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton

/** New songs of the followed artists, newest first, as loaded at [fetchedAtMillis]. */
data class FollowedSongs(
    val owner: String,
    val songs: List<Song>,
    val fetchedAtMillis: Long,
    internal val followVersion: Int,
)

/**
 * Home 「追蹤音樂人的新歌」: the first page of songs of up to [MAX_ARTISTS] followed artists
 * (most recently followed first), fetched with at most [CONCURRENCY] requests at a time and
 * merged newest first (by `publish_at`; songs without a date are interleaved per artist).
 *
 * The result is cached for the session: [load] serves it while it is younger than [TTL_MILLIS],
 * belongs to the logged-in user and no follow / unfollow happened since.
 */
@Singleton
class FollowedSongsRepository @Inject constructor(
    private val repository: StreetVoiceRepository,
    private val followRepository: FollowRepository,
) {
    /** Clock (replaceable in tests). */
    internal var now: () -> Long = System::currentTimeMillis

    private val mutex = Mutex()

    @Volatile
    private var cached: FollowedSongs? = null

    /** The cached result if it is still fresh for the logged-in user, else null. */
    fun fresh(): FollowedSongs? = cached?.takeIf { isFresh(it) }

    private fun isFresh(value: FollowedSongs): Boolean =
        value.owner == followRepository.currentUser() &&
            value.followVersion == followRepository.version.value &&
            now() - value.fetchedAtMillis in 0 until TTL_MILLIS

    /**
     * The followed artists' new songs (empty when nobody is followed). Fails with
     * [NotLoggedInException] when logged out, or with the first error when the follow list or
     * every artist's songs failed to load. A partial failure returns what did load.
     */
    suspend fun load(forceRefresh: Boolean = false): Result<FollowedSongs> = mutex.withLock {
        if (!forceRefresh) fresh()?.let { return@withLock Result.success(it) }
        val versionAtStart = followRepository.version.value
        val following = followRepository.ensureLoaded().getOrElse { return@withLock Result.failure(it) }
        val artists = following.artists.take(MAX_ARTISTS)

        val perArtist: List<Result<List<Song>>> = coroutineScope {
            val permits = Semaphore(CONCURRENCY)
            artists.map { artist ->
                async {
                    permits.withPermit {
                        repository.getArtistSongs(artist.username, limit = SONGS_PER_ARTIST, offset = 0).map { it.items }
                    }
                }
            }.awaitAll()
        }
        if (perArtist.isNotEmpty() && perArtist.all { it.isFailure }) {
            return@withLock Result.failure(perArtist.first().exceptionOrNull()!!)
        }
        val songs = mergeNewestFirst(perArtist.mapNotNull { it.getOrNull() }, MAX_SONGS)
        val result = FollowedSongs(following.owner, songs, now(), versionAtStart)
        cached = result
        Result.success(result)
    }

    companion object {
        const val MAX_ARTISTS = 10
        const val CONCURRENCY = 4
        const val SONGS_PER_ARTIST = 10
        const val MAX_SONGS = 20
        const val TTL_MILLIS = 10 * 60 * 1000L
    }
}

/**
 * Merges per-artist song lists newest first: interleaved round robin (so songs without a date
 * are spread across artists), then stably sorted by `publish_at` (undated songs last).
 * Duplicates (by id) are dropped; at most [limit] songs.
 */
internal fun mergeNewestFirst(lists: List<List<Song>>, limit: Int): List<Song> {
    val interleaved = ArrayList<Song>()
    val longest = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until longest) {
        for (list in lists) list.getOrNull(i)?.let { interleaved += it }
    }
    val seen = HashSet<Int>()
    return interleaved
        .filter { seen.add(it.id) }
        .map { it to (parsePublishAt(it.publishAt) ?: Long.MIN_VALUE) }
        .sortedByDescending { it.second }
        .take(limit)
        .map { it.first }
}
