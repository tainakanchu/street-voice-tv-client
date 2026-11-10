package com.example.streetvoicetv.testutil

import com.example.streetvoicetv.domain.model.Album
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.PlayableStream
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository

/**
 * In-memory [StreetVoiceRepository] for ViewModel tests. Every call returns the
 * configured result (default: failure "not stubbed") and is recorded in [calls].
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
    var chartResult: Result<List<Song>> = notStubbed()
    var editorChoiceResult: Result<List<Song>> = notStubbed()
    var recommendedPlaylistsResult: Result<List<Playlist>> = notStubbed()

    var onSearchSongs: (suspend (String) -> Result<List<Song>>)? = null
    var onArtistPlaylists: (suspend (String) -> Result<List<Playlist>>)? = null
    var onChart: (suspend () -> Result<List<Song>>)? = null

    override suspend fun searchSongs(query: String, limit: Int, offset: Int): Result<List<Song>> {
        calls += "searchSongs($query)"
        return onSearchSongs?.invoke(query) ?: searchSongsResult
    }

    override suspend fun searchArtists(query: String, limit: Int, offset: Int) =
        searchArtistsResult.also { calls += "searchArtists($query)" }

    override suspend fun searchPlaylists(query: String, limit: Int, offset: Int) =
        searchPlaylistsResult.also { calls += "searchPlaylists($query)" }

    override suspend fun getAlbumDetail(albumId: Int) =
        albumDetailResult.also { calls += "getAlbumDetail($albumId)" }

    override suspend fun getAlbumSongs(albumId: Int, limit: Int, offset: Int) =
        albumSongsResult.also { calls += "getAlbumSongs($albumId)" }

    override suspend fun getCurrentUsername(): Result<String> =
        currentUsernameResult.also { calls += "getCurrentUsername" }

    override suspend fun getArtistDetail(username: String): Result<Artist> =
        artistDetailResult.also { calls += "getArtistDetail($username)" }

    override suspend fun getArtistSongs(username: String, limit: Int, offset: Int): Result<List<Song>> =
        artistSongsResult.also { calls += "getArtistSongs($username)" }

    override suspend fun getArtistAlbums(username: String, limit: Int, offset: Int): Result<List<Album>> =
        artistAlbumsResult.also { calls += "getArtistAlbums($username)" }

    override suspend fun getArtistPlaylists(username: String, limit: Int, offset: Int): Result<List<Playlist>> {
        calls += "getArtistPlaylists($username)"
        return onArtistPlaylists?.invoke(username) ?: notStubbed()
    }

    override suspend fun getLikedSongs(username: String, limit: Int, offset: Int): Result<List<Song>> =
        likedSongsResult.also { calls += "getLikedSongs($username)" }

    override suspend fun getRealtimeChart(limit: Int): Result<List<Song>> {
        calls += "getRealtimeChart"
        return onChart?.invoke() ?: chartResult
    }

    override suspend fun getEditorChoice(limit: Int): Result<List<Song>> = editorChoiceResult
    override suspend fun getRecommendedPlaylists(limit: Int): Result<List<Playlist>> = recommendedPlaylistsResult

    override suspend fun getSongDetail(songId: Int): Result<Song> = notStubbed()
    override suspend fun getStreamUrl(songId: Int): Result<PlayableStream> = notStubbed()
    override suspend fun likeSong(songId: Int): Result<Unit> = notStubbed()
    override suspend fun unlikeSong(songId: Int): Result<Unit> = notStubbed()
    override suspend fun getPlaylistDetail(playlistId: Int): Result<Playlist> = notStubbed()
    override suspend fun getPlaylistSongs(playlistId: Int, limit: Int): Result<List<Song>> = notStubbed()

    private fun <T> notStubbed(): Result<T> = Result.failure(IllegalStateException("not stubbed"))
}
