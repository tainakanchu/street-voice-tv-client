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
 */
class FakeStreetVoiceRepository : StreetVoiceRepository {

    val calls = mutableListOf<String>()

    var searchSongsResult: Result<List<Song>> = notStubbed()
    var searchArtistsResult: Result<List<Artist>> = notStubbed()
    var searchPlaylistsResult: Result<List<Playlist>> = notStubbed()
    var albumDetailResult: Result<Album> = notStubbed()
    var albumSongsResult: Result<List<Song>> = notStubbed()

    override suspend fun searchSongs(query: String, limit: Int, offset: Int) =
        searchSongsResult.also { calls += "searchSongs($query)" }

    override suspend fun searchArtists(query: String, limit: Int, offset: Int) =
        searchArtistsResult.also { calls += "searchArtists($query)" }

    override suspend fun searchPlaylists(query: String, limit: Int, offset: Int) =
        searchPlaylistsResult.also { calls += "searchPlaylists($query)" }

    override suspend fun getAlbumDetail(albumId: Int) =
        albumDetailResult.also { calls += "getAlbumDetail($albumId)" }

    override suspend fun getAlbumSongs(albumId: Int, limit: Int, offset: Int) =
        albumSongsResult.also { calls += "getAlbumSongs($albumId)" }

    override suspend fun getSongDetail(songId: Int): Result<Song> = notStubbed()
    override suspend fun getStreamUrl(songId: Int): Result<PlayableStream> = notStubbed()
    override suspend fun likeSong(songId: Int): Result<Unit> = notStubbed()
    override suspend fun unlikeSong(songId: Int): Result<Unit> = notStubbed()
    override suspend fun getArtistDetail(username: String): Result<Artist> = notStubbed()
    override suspend fun getArtistSongs(username: String, limit: Int, offset: Int): Result<List<Song>> = notStubbed()
    override suspend fun getArtistAlbums(username: String, limit: Int, offset: Int): Result<List<Album>> = notStubbed()
    override suspend fun getArtistPlaylists(username: String, limit: Int, offset: Int): Result<List<Playlist>> = notStubbed()
    override suspend fun getLikedSongs(username: String, limit: Int, offset: Int): Result<List<Song>> = notStubbed()
    override suspend fun getRealtimeChart(limit: Int): Result<List<Song>> = notStubbed()
    override suspend fun getEditorChoice(limit: Int): Result<List<Song>> = notStubbed()
    override suspend fun getPlaylistDetail(playlistId: Int): Result<Playlist> = notStubbed()
    override suspend fun getRecommendedPlaylists(limit: Int): Result<List<Playlist>> = notStubbed()
    override suspend fun getPlaylistSongs(playlistId: Int, limit: Int): Result<List<Song>> = notStubbed()

    private fun <T> notStubbed(): Result<T> = Result.failure(IllegalStateException("not stubbed"))
}
