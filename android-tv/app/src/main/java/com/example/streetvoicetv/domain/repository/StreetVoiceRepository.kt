package com.example.streetvoicetv.domain.repository

import com.example.streetvoicetv.domain.model.Album
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.PlayableStream
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song

interface StreetVoiceRepository {

    // Search
    suspend fun searchSongs(query: String, limit: Int = 10, offset: Int = 0): Result<List<Song>>
    suspend fun searchArtists(query: String, limit: Int = 5, offset: Int = 0): Result<List<Artist>>

    // Song
    suspend fun getSongDetail(songId: Int): Result<Song>
    suspend fun getStreamUrl(songId: Int): Result<PlayableStream>

    // Artist
    suspend fun getArtistDetail(username: String): Result<Artist>
    suspend fun getArtistSongs(username: String, limit: Int = 20, offset: Int = 0): Result<List<Song>>
    suspend fun getArtistAlbums(username: String, limit: Int = 20, offset: Int = 0): Result<List<Album>>

    // Album
    suspend fun getAlbumDetail(albumId: Int): Result<Album>
    suspend fun getAlbumSongs(albumId: Int, limit: Int = 50, offset: Int = 0): Result<List<Song>>

    // Home / Discover
    suspend fun getRealtimeChart(limit: Int = 20): Result<List<Song>>
    suspend fun getEditorChoice(limit: Int = 10): Result<List<Song>>
    suspend fun getPlaylistDetail(playlistId: Int): Result<Playlist>
    suspend fun getRecommendedPlaylists(limit: Int = 10): Result<List<Playlist>>
    suspend fun getPlaylistSongs(playlistId: Int, limit: Int = 50): Result<List<Song>>
}
