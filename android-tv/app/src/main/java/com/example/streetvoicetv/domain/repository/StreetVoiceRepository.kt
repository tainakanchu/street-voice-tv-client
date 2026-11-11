package com.example.streetvoicetv.domain.repository

import com.example.streetvoicetv.domain.model.Album
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.ChartPeriod
import com.example.streetvoicetv.domain.model.Genre
import com.example.streetvoicetv.domain.model.Page
import com.example.streetvoicetv.domain.model.PlayableStream
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song

/**
 * List endpoints are offset paginated (`limit` / `offset`, see docs/findings.md): methods that
 * return a [Page] take the offset of the page to load and report the offset of the next one.
 * The server clamps `limit` to [MAX_PAGE_SIZE].
 */
interface StreetVoiceRepository {

    // Search (the server reports at most 100 results per type)
    suspend fun searchSongs(query: String, limit: Int = 20, offset: Int = 0): Result<Page<Song>>
    suspend fun searchArtists(query: String, limit: Int = 5, offset: Int = 0): Result<Page<Artist>>
    suspend fun searchPlaylists(query: String, limit: Int = 5, offset: Int = 0): Result<Page<Playlist>>

    // Song
    suspend fun getSongDetail(songId: Int): Result<Song>
    suspend fun getStreamUrl(songId: Int): Result<PlayableStream>
    suspend fun likeSong(songId: Int): Result<Unit>
    suspend fun unlikeSong(songId: Int): Result<Unit>

    // Session
    /** Username of the logged-in user (`/user/me/`). Fails when the session is not valid. */
    suspend fun getCurrentUsername(): Result<String>

    // Artist
    suspend fun getArtistDetail(username: String): Result<Artist>
    suspend fun getArtistSongs(username: String, limit: Int = DEFAULT_PAGE_SIZE, offset: Int = 0): Result<Page<Song>>
    suspend fun getArtistAlbums(username: String, limit: Int = DEFAULT_PAGE_SIZE, offset: Int = 0): Result<Page<Album>>
    suspend fun getArtistPlaylists(username: String, limit: Int = 20, offset: Int = 0): Result<List<Playlist>>
    suspend fun getLikedSongs(username: String, limit: Int = DEFAULT_PAGE_SIZE, offset: Int = 0): Result<Page<Song>>

    // Album
    suspend fun getAlbumDetail(albumId: Int): Result<Album>
    suspend fun getAlbumSongs(albumId: Int, limit: Int = DEFAULT_PAGE_SIZE, offset: Int = 0): Result<Page<Song>>

    // Home / Discover
    suspend fun getRealtimeChart(limit: Int = 20): Result<List<Song>>
    suspend fun getEditorChoice(limit: Int = 10): Result<List<Song>>
    suspend fun getPlaylistDetail(playlistId: Int): Result<Playlist>
    suspend fun getRecommendedPlaylists(limit: Int = 10): Result<List<Playlist>>
    suspend fun getPlaylistSongs(playlistId: Int, limit: Int = DEFAULT_PAGE_SIZE, offset: Int = 0): Result<Page<Song>>

    // Charts / genres
    /** The 22 song genres (id 0 = 未分類). */
    suspend fun getGenres(): Result<List<Genre>>

    /**
     * One page of a chart. [genreSlug] is a [com.example.streetvoicetv.domain.model.ChartCategory]
     * slug ("all", "rock", ...); charts end after 50 entries.
     */
    suspend fun getChart(
        genreSlug: String,
        period: ChartPeriod,
        offset: Int = 0,
        limit: Int = CHART_PAGE_SIZE,
    ): Result<Page<Song>>

    // Follow
    /** Users [username] follows (public), newest follow first. */
    suspend fun getFollowing(username: String, limit: Int = DEFAULT_PAGE_SIZE, offset: Int = 0): Result<Page<Artist>>

    /** Follows [username] (login required). */
    suspend fun followArtist(username: String): Result<Unit>

    /** Unfollows [username] (login required). */
    suspend fun unfollowArtist(username: String): Result<Unit>

    companion object {
        /** Page size of the chart screen. */
        const val CHART_PAGE_SIZE = 20

        /** Page size of the detail lists (album / playlist / artist / liked songs). */
        const val DEFAULT_PAGE_SIZE = 30

        /** Largest `limit` the server honours. */
        const val MAX_PAGE_SIZE = 100
    }
}
