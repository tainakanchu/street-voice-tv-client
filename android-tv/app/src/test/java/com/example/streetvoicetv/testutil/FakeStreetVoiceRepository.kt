package com.example.streetvoicetv.testutil

import com.example.streetvoicetv.domain.model.Album
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.ChartPeriod
import com.example.streetvoicetv.domain.model.Genre
import com.example.streetvoicetv.domain.model.Page
import com.example.streetvoicetv.domain.model.PlayableStream
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository

/** Paginated fake endpoint: (offset, limit) -> page. Suspending, so tests can hold a page back. */
typealias FakePages<T> = suspend (offset: Int, limit: Int) -> Result<Page<T>>

/** A single, last page with [items]. */
fun <T> lastPage(items: List<T>): Result<Page<T>> = Result.success(Page(items, items.size, null))

/** Serves [all] in pages like the server does (next offset until the list is exhausted). */
fun <T> pagesOf(all: List<T>): FakePages<T> = { offset, limit ->
    val items = all.drop(offset).take(limit)
    val next = (offset + items.size).takeIf { items.isNotEmpty() && it < all.size }
    Result.success(Page(items, all.size, next))
}

/**
 * In-memory [StreetVoiceRepository] for ViewModel tests. Every call returns the
 * configured result (default: failure "not stubbed") and is recorded in [calls]
 * (paginated calls for a later page with their offset, e.g. `getAlbumSongs(1, offset=30)`).
 *
 * Paginated endpoints: the `...Result` list is served as one last page; setting the
 * paged hook (e.g. [albumSongs], built with [pagesOf]) replaces it.
 * The `on...` hooks, when set, replace the fixed result and may suspend (e.g. on a
 * CompletableDeferred) to simulate slow / out-of-order responses.
 */
class FakeStreetVoiceRepository : StreetVoiceRepository {

    val calls = mutableListOf<String>()

    var searchSongsResult: Result<List<Song>> = notStubbed()
    var searchArtistsResult: Result<List<Artist>> = notStubbed()
    var searchPlaylistsResult: Result<List<Playlist>> = notStubbed()
    var albumDetailResult: Result<Album> = notStubbed()
    var albumSongsResult: Result<List<Song>> = notStubbed()
    var currentUsernameResult: Result<String> = notStubbed()
    var artistDetailResult: Result<Artist> = notStubbed()
    var artistSongsResult: Result<List<Song>> = notStubbed()
    var artistAlbumsResult: Result<List<Album>> = notStubbed()
    var likedSongsResult: Result<List<Song>> = notStubbed()
    var playlistDetailResult: Result<Playlist> = notStubbed()
    var playlistSongsResult: Result<List<Song>> = notStubbed()
    var chartResult: Result<List<Song>> = notStubbed()
    var editorChoiceResult: Result<List<Song>> = notStubbed()
    var recommendedPlaylistsResult: Result<List<Playlist>> = notStubbed()

    var onSearchSongs: (suspend (String) -> Result<List<Song>>)? = null
    var onArtistPlaylists: (suspend (String) -> Result<List<Playlist>>)? = null
    var onChart: (suspend () -> Result<List<Song>>)? = null

    var searchSongs: FakePages<Song>? = null
    var albumSongs: FakePages<Song>? = null
    var artistSongs: FakePages<Song>? = null
    var artistAlbums: FakePages<Album>? = null
    var likedSongs: FakePages<Song>? = null
    var playlistSongs: FakePages<Song>? = null

    private fun record(call: String, offset: Int) {
        calls += if (offset == 0) "$call)" else "$call, offset=$offset)"
    }

    private suspend fun <T> paged(
        hook: FakePages<T>?,
        offset: Int,
        limit: Int,
        fallback: suspend () -> Result<List<T>>,
    ): Result<Page<T>> = hook?.invoke(offset, limit) ?: fallback().map { Page(it, it.size, null) }

    override suspend fun searchSongs(query: String, limit: Int, offset: Int): Result<Page<Song>> {
        record("searchSongs($query", offset)
        return paged(searchSongs, offset, limit) { onSearchSongs?.invoke(query) ?: searchSongsResult }
    }

    override suspend fun searchArtists(query: String, limit: Int, offset: Int): Result<Page<Artist>> {
        calls += "searchArtists($query)"
        return searchArtistsResult.map { Page(it, it.size, null) }
    }

    override suspend fun searchPlaylists(query: String, limit: Int, offset: Int): Result<Page<Playlist>> {
        calls += "searchPlaylists($query)"
        return searchPlaylistsResult.map { Page(it, it.size, null) }
    }

    override suspend fun getAlbumDetail(albumId: Int) =
        albumDetailResult.also { calls += "getAlbumDetail($albumId)" }

    override suspend fun getAlbumSongs(albumId: Int, limit: Int, offset: Int): Result<Page<Song>> {
        record("getAlbumSongs($albumId", offset)
        return paged(albumSongs, offset, limit) { albumSongsResult }
    }

    override suspend fun getCurrentUsername(): Result<String> =
        currentUsernameResult.also { calls += "getCurrentUsername" }

    override suspend fun getArtistDetail(username: String): Result<Artist> =
        artistDetailResult.also { calls += "getArtistDetail($username)" }

    override suspend fun getArtistSongs(username: String, limit: Int, offset: Int): Result<Page<Song>> {
        record("getArtistSongs($username", offset)
        return paged(artistSongs, offset, limit) { artistSongsResult }
    }

    override suspend fun getArtistAlbums(username: String, limit: Int, offset: Int): Result<Page<Album>> {
        record("getArtistAlbums($username", offset)
        return paged(artistAlbums, offset, limit) { artistAlbumsResult }
    }

    override suspend fun getArtistPlaylists(username: String, limit: Int, offset: Int): Result<List<Playlist>> {
        calls += "getArtistPlaylists($username)"
        return onArtistPlaylists?.invoke(username) ?: notStubbed()
    }

    override suspend fun getLikedSongs(username: String, limit: Int, offset: Int): Result<Page<Song>> {
        record("getLikedSongs($username", offset)
        return paged(likedSongs, offset, limit) { likedSongsResult }
    }

    override suspend fun getPlaylistDetail(playlistId: Int): Result<Playlist> =
        playlistDetailResult.also { calls += "getPlaylistDetail($playlistId)" }

    override suspend fun getPlaylistSongs(playlistId: Int, limit: Int, offset: Int): Result<Page<Song>> {
        record("getPlaylistSongs($playlistId", offset)
        return paged(playlistSongs, offset, limit) { playlistSongsResult }
    }

    override suspend fun getRealtimeChart(limit: Int): Result<List<Song>> {
        calls += "getRealtimeChart"
        return onChart?.invoke() ?: chartResult
    }

    override suspend fun getEditorChoice(limit: Int): Result<List<Song>> = editorChoiceResult
    override suspend fun getRecommendedPlaylists(limit: Int): Result<List<Playlist>> = recommendedPlaylistsResult

    // --- Charts / follow ---

    var genresResult: Result<List<Genre>> = notStubbed()

    /** (slug, period, offset, limit) -> page. */
    var onChartPage: (suspend (String, ChartPeriod, Int, Int) -> Result<Page<Song>>)? = null

    /** username -> pages of the users they follow. */
    var following: (suspend (String, Int, Int) -> Result<Page<Artist>>)? = null

    /** (username, follow) -> result; default success. */
    var onFollow: suspend (String, Boolean) -> Result<Unit> = { _, _ -> Result.success(Unit) }

    override suspend fun getGenres(): Result<List<Genre>> = genresResult.also { calls += "getGenres" }

    override suspend fun getChart(genreSlug: String, period: ChartPeriod, offset: Int, limit: Int): Result<Page<Song>> {
        record("getChart($genreSlug, ${period.pathSegment}", offset)
        return onChartPage?.invoke(genreSlug, period, offset, limit) ?: notStubbed()
    }

    override suspend fun getFollowing(username: String, limit: Int, offset: Int): Result<Page<Artist>> {
        record("getFollowing($username", offset)
        return following?.invoke(username, offset, limit) ?: notStubbed()
    }

    override suspend fun followArtist(username: String): Result<Unit> {
        calls += "followArtist($username)"
        return onFollow(username, true)
    }

    override suspend fun unfollowArtist(username: String): Result<Unit> {
        calls += "unfollowArtist($username)"
        return onFollow(username, false)
    }

    override suspend fun getSongDetail(songId: Int): Result<Song> = notStubbed()
    override suspend fun getStreamUrl(songId: Int): Result<PlayableStream> = notStubbed()
    override suspend fun likeSong(songId: Int): Result<Unit> = notStubbed()
    override suspend fun unlikeSong(songId: Int): Result<Unit> = notStubbed()

    private fun <T> notStubbed(): Result<T> = Result.failure(IllegalStateException("not stubbed"))
}
