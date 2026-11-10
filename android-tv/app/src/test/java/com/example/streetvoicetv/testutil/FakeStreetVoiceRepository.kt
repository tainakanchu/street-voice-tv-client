package com.example.streetvoicetv.testutil

import com.example.streetvoicetv.domain.model.Album
import com.example.streetvoicetv.domain.model.Artist
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
 * (paginated calls with their offset, e.g. `getAlbumSongs(1, offset=30)`).
 */
class FakeStreetVoiceRepository : StreetVoiceRepository {

    val calls = mutableListOf<String>()

    var searchSongs: FakePages<Song> = { _, _ -> notStubbed() }
    var searchArtistsResult: Result<Page<Artist>> = notStubbed()
    var searchPlaylistsResult: Result<Page<Playlist>> = notStubbed()
    var albumDetailResult: Result<Album> = notStubbed()
    var albumSongs: FakePages<Song> = { _, _ -> notStubbed() }
    var playlistDetailResult: Result<Playlist> = notStubbed()
    var playlistSongs: FakePages<Song> = { _, _ -> notStubbed() }
    var artistDetailResult: Result<Artist> = notStubbed()
    var artistSongs: FakePages<Song> = { _, _ -> notStubbed() }
    var artistAlbums: FakePages<Album> = { _, _ -> notStubbed() }

    override suspend fun searchSongs(query: String, limit: Int, offset: Int): Result<Page<Song>> {
        calls += if (offset == 0) "searchSongs($query)" else "searchSongs($query, offset=$offset)"
        return searchSongs(offset, limit)
    }

    override suspend fun searchArtists(query: String, limit: Int, offset: Int) =
        searchArtistsResult.also { calls += "searchArtists($query)" }

    override suspend fun searchPlaylists(query: String, limit: Int, offset: Int) =
        searchPlaylistsResult.also { calls += "searchPlaylists($query)" }

    override suspend fun getAlbumDetail(albumId: Int) =
        albumDetailResult.also { calls += "getAlbumDetail($albumId)" }

    override suspend fun getAlbumSongs(albumId: Int, limit: Int, offset: Int): Result<Page<Song>> {
        calls += "getAlbumSongs($albumId, offset=$offset)"
        return albumSongs(offset, limit)
    }

    override suspend fun getPlaylistDetail(playlistId: Int): Result<Playlist> =
        playlistDetailResult.also { calls += "getPlaylistDetail($playlistId)" }

    override suspend fun getPlaylistSongs(playlistId: Int, limit: Int, offset: Int): Result<Page<Song>> {
        calls += "getPlaylistSongs($playlistId, offset=$offset)"
        return playlistSongs(offset, limit)
    }

    override suspend fun getArtistDetail(username: String): Result<Artist> =
        artistDetailResult.also { calls += "getArtistDetail($username)" }

    override suspend fun getArtistSongs(username: String, limit: Int, offset: Int): Result<Page<Song>> {
        calls += "getArtistSongs($username, offset=$offset)"
        return artistSongs(offset, limit)
    }

    override suspend fun getArtistAlbums(username: String, limit: Int, offset: Int): Result<Page<Album>> {
        calls += "getArtistAlbums($username, offset=$offset)"
        return artistAlbums(offset, limit)
    }

    override suspend fun getSongDetail(songId: Int): Result<Song> = notStubbed()
    override suspend fun getStreamUrl(songId: Int): Result<PlayableStream> = notStubbed()
    override suspend fun likeSong(songId: Int): Result<Unit> = notStubbed()
    override suspend fun unlikeSong(songId: Int): Result<Unit> = notStubbed()
    override suspend fun getArtistPlaylists(username: String, limit: Int, offset: Int): Result<List<Playlist>> = notStubbed()
    override suspend fun getLikedSongs(username: String, limit: Int, offset: Int): Result<Page<Song>> = notStubbed()
    override suspend fun getRealtimeChart(limit: Int): Result<List<Song>> = notStubbed()
    override suspend fun getEditorChoice(limit: Int): Result<List<Song>> = notStubbed()
    override suspend fun getRecommendedPlaylists(limit: Int): Result<List<Playlist>> = notStubbed()

    private fun <T> notStubbed(): Result<T> = Result.failure(IllegalStateException("not stubbed"))
}
