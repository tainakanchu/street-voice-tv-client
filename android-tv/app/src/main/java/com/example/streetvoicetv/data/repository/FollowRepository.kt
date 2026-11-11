package com.example.streetvoicetv.data.repository

import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.appendDistinctBy
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** The artists [owner] follows, newest follow first. */
data class FollowingSnapshot(
    val owner: String,
    val artists: List<Artist>,
) {
    val usernames: Set<String> = artists.mapTo(HashSet()) { it.username }
}

/** A follow action or a follow list needs a logged-in user. */
class NotLoggedInException : IllegalStateException("not logged in")

/**
 * Who the logged-in user follows, shared by the screens (artist 追蹤 button, MyPage row,
 * Home 「追蹤音樂人的新歌」).
 *
 * The artist detail's `is_follow` is only meaningful with a session, so the authoritative
 * source is `/user/{me}/following/` (public, paged), cached here per user: a login change
 * (another username, or logout) invalidates it on the next [ensureLoaded]. Follow / unfollow
 * through [setFollowing] keep the cache in sync and bump [version].
 */
@Singleton
class FollowRepository @Inject constructor(
    private val repository: StreetVoiceRepository,
    private val sessionManager: SessionManager,
) {
    private val mutex = Mutex()

    private val _state = MutableStateFlow<FollowingSnapshot?>(null)

    /** The cached follow list (null: not loaded, or loaded for a user who is no longer logged in). */
    val state: StateFlow<FollowingSnapshot?> = _state.asStateFlow()

    private val _version = MutableStateFlow(0)

    /** Incremented after every successful follow / unfollow (invalidates derived caches). */
    val version: StateFlow<Int> = _version.asStateFlow()

    /** The logged-in username, or null (logged out, or the username is not known yet). */
    fun currentUser(): String? = sessionManager.session.value.takeIf { it.isLoggedIn }?.username

    /** Whether the logged-in user follows [username]; null when not known (not loaded / logged out). */
    fun isFollowing(username: String): Boolean? =
        _state.value?.takeIf { it.owner == currentUser() }?.let { username in it.usernames }

    /**
     * Returns the follow list of the logged-in user, loading it (all pages, up to
     * [MAX_PAGES] × [PAGE_SIZE]) unless it is cached for that user. Concurrent callers share
     * one load.
     */
    suspend fun ensureLoaded(forceRefresh: Boolean = false): Result<FollowingSnapshot> {
        val me = currentUser() ?: run {
            _state.value = null
            return Result.failure(NotLoggedInException())
        }
        return mutex.withLock {
            val cached = _state.value
            if (cached != null && cached.owner == me && !forceRefresh) return@withLock Result.success(cached)
            if (cached != null && cached.owner != me) _state.value = null

            var artists = emptyList<Artist>()
            var offset: Int? = 0
            var pages = 0
            while (offset != null && pages < MAX_PAGES) {
                val page = repository.getFollowing(me, PAGE_SIZE, offset).getOrElse { return@withLock Result.failure(it) }
                artists = artists.appendDistinctBy(page.items) { it.username }
                offset = page.nextOffset.takeIf { page.items.isNotEmpty() }
                pages++
            }
            // The session changed while loading: the list belongs to nobody on screen.
            if (currentUser() != me) return@withLock Result.failure(NotLoggedInException())
            val snapshot = FollowingSnapshot(me, artists)
            _state.value = snapshot
            Result.success(snapshot)
        }
    }

    /**
     * Follows (POST) / unfollows (DELETE) [artist] and, on success, updates the cached list.
     * CSRF is added by the session interceptor.
     */
    suspend fun setFollowing(artist: Artist, follow: Boolean): Result<Unit> {
        val me = currentUser() ?: return Result.failure(NotLoggedInException())
        val result = if (follow) repository.followArtist(artist.username) else repository.unfollowArtist(artist.username)
        result.onSuccess {
            _state.update { snapshot ->
                if (snapshot == null || snapshot.owner != me) {
                    snapshot
                } else {
                    val others = snapshot.artists.filterNot { it.username == artist.username }
                    snapshot.copy(artists = if (follow) listOf(artist) + others else others)
                }
            }
            _version.update { it + 1 }
        }
        return result
    }

    companion object {
        const val PAGE_SIZE = StreetVoiceRepository.MAX_PAGE_SIZE
        const val MAX_PAGES = 5
    }
}
