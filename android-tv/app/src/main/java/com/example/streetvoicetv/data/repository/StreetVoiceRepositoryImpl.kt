package com.example.streetvoicetv.data.repository

import com.example.streetvoicetv.data.api.StreetVoiceApi
import com.example.streetvoicetv.data.model.AlbumDetailResponse
import com.example.streetvoicetv.data.model.AlbumResult
import com.example.streetvoicetv.data.model.AlbumSongResult
import com.example.streetvoicetv.data.model.NestedSong
import com.example.streetvoicetv.data.model.PlaylistDetailResponse
import com.example.streetvoicetv.data.model.PlaylistResult
import com.example.streetvoicetv.data.model.SearchSongResult
import com.example.streetvoicetv.data.model.SongDetailResponse
import com.example.streetvoicetv.data.model.UserDetailResponse
import com.example.streetvoicetv.data.model.UserSearchResult
import com.example.streetvoicetv.data.model.UserSongResult
import com.example.streetvoicetv.domain.model.Album
import com.example.streetvoicetv.domain.model.Artist
import com.example.streetvoicetv.domain.model.PlayableStream
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreetVoiceRepositoryImpl @Inject constructor(
    private val api: StreetVoiceApi,
) : StreetVoiceRepository {

    // --- Search ---

    override suspend fun searchSongs(query: String, limit: Int, offset: Int): Result<List<Song>> {
        return runCatching {
            api.searchSongs(query, limit, offset).results.map { it.toDomain() }
        }
    }

    override suspend fun searchArtists(query: String, limit: Int, offset: Int): Result<List<Artist>> {
        return runCatching {
            api.searchUsers(query, limit, offset).results.map { it.toDomain() }
        }
    }

    override suspend fun searchPlaylists(query: String, limit: Int, offset: Int): Result<List<Playlist>> {
        return runCatching {
            api.searchPlaylists(query, limit, offset).results.map { it.toDomain() }
        }
    }

    // --- Song ---

    override suspend fun getSongDetail(songId: Int): Result<Song> {
        return runCatching {
            api.getSongDetail(songId).toDomain()
        }
    }

    override suspend fun getStreamUrl(songId: Int): Result<PlayableStream> {
        return runCatching {
            PlayableStream(songId = songId, hlsUrl = api.getStreamUrl(songId).file)
        }
    }

    override suspend fun likeSong(songId: Int): Result<Unit> {
        return runCatching { api.likeSong(songId) }
    }

    override suspend fun unlikeSong(songId: Int): Result<Unit> {
        return runCatching { api.unlikeSong(songId) }
    }

    // --- Artist ---

    override suspend fun getArtistDetail(username: String): Result<Artist> {
        return runCatching {
            api.getUserDetail(username).toDomain()
        }
    }

    override suspend fun getArtistSongs(username: String, limit: Int, offset: Int): Result<List<Song>> {
        return runCatching {
            api.getUserSongs(username, limit, offset).results.map { it.toDomain() }
        }
    }

    override suspend fun getArtistAlbums(username: String, limit: Int, offset: Int): Result<List<Album>> {
        return runCatching {
            api.getUserAlbums(username, limit, offset).results.map { it.toDomain() }
        }
    }

    override suspend fun getArtistPlaylists(username: String, limit: Int, offset: Int): Result<List<Playlist>> {
        return runCatching {
            api.getUserPlaylists(username, limit, offset).results.map { it.toDomain() }
        }
    }

    override suspend fun getLikedSongs(username: String, limit: Int, offset: Int): Result<List<Song>> {
        return runCatching {
            api.getUserLikedSongs(username, limit, offset).results.map { it.contentObject.toDomain() }
        }
    }

    // --- Album ---

    override suspend fun getAlbumDetail(albumId: Int): Result<Album> {
        return runCatching {
            api.getAlbumDetail(albumId).toDomain()
        }
    }

    override suspend fun getAlbumSongs(albumId: Int, limit: Int, offset: Int): Result<List<Song>> {
        return runCatching {
            api.getAlbumSongs(albumId, limit, offset).results.map { it.toDomain() }
        }
    }

    // --- Logged-in User Feed ---

    override suspend fun getFollowingFeed(limit: Int, offset: Int): Result<List<Song>> {
        return runCatching {
            api.getFollowingFeed(limit, offset).results.map { it.contentObject.toDomain() }
        }
    }

    override suspend fun getPlayHistory(username: String, limit: Int, offset: Int): Result<List<Song>> {
        return runCatching {
            api.getPlayHistory(username, limit, offset).results.map { it.contentObject.toDomain() }
        }
    }

    // --- Home / Discover ---

    override suspend fun getRealtimeChart(limit: Int): Result<List<Song>> {
        return runCatching {
            api.getRealtimeChart(limit = limit).results.map { it.song.toDomain() }
        }
    }

    override suspend fun getEditorChoice(limit: Int): Result<List<Song>> {
        return runCatching {
            api.getEditorChoice(limit = limit).results.map { it.contentObject.toDomain() }
        }
    }

    override suspend fun getPlaylistDetail(playlistId: Int): Result<Playlist> {
        return runCatching {
            api.getPlaylistDetail(playlistId).toDomain()
        }
    }

    override suspend fun getRecommendedPlaylists(limit: Int): Result<List<Playlist>> {
        return runCatching {
            api.getSectionPlaylists(sectionId = 1, limit = limit).results.map { it.toDomain() }
        }
    }

    override suspend fun getPlaylistSongs(playlistId: Int, limit: Int): Result<List<Song>> {
        return runCatching {
            api.getPlaylistSongs(playlistId, limit = limit).results.map { it.song.toDomain() }
        }
    }
}

// --- Mappers ---

private fun SearchSongResult.toDomain(): Song = Song(
    id = id,
    name = name,
    artistName = user.nickname ?: user.username,
    artistUsername = user.username,
    imageUrl = image,
    durationSeconds = length,
    playsCount = playsCount,
)

private fun SongDetailResponse.toDomain(): Song = Song(
    id = id,
    name = name,
    artistName = user.profile?.nickname ?: user.username,
    artistUsername = user.username,
    imageUrl = image,
    durationSeconds = length,
    playsCount = playsCount,
    genre = genre,
    synopsis = synopsis,
    lyrics = lyrics,
    likesCount = likesCount,
    commentsCount = commentsCount,
    shareCount = shareCount,
    publishAt = publishAt,
    albumId = album?.id,
    albumName = album?.name,
    albumImageUrl = album?.image,
    artistProfileImageUrl = user.profile?.image,
    isLiked = isLike,
)

private fun UserSearchResult.toDomain(): Artist = Artist(
    id = id,
    username = username,
    displayName = profile?.nickname ?: username,
    profileImageUrl = profile?.image ?: profileImage,
    coverImageUrl = coverImage,
    introduction = introduction,
    followersCount = followersCount,
    followingCount = followingCount,
    songsCount = songsCount,
)

private fun UserDetailResponse.toDomain(): Artist = Artist(
    id = id,
    username = username,
    displayName = nickname ?: username,
    profileImageUrl = profileImage,
    coverImageUrl = coverImage,
    introduction = introduction,
    followersCount = followersCount,
    followingCount = followingCount,
    songsCount = songsCount,
)

private fun UserSongResult.toDomain(): Song = Song(
    id = id,
    name = name,
    artistName = user.nickname ?: user.username,
    artistUsername = user.username,
    imageUrl = image,
    durationSeconds = length,
    playsCount = playsCount,
)

private fun AlbumSongResult.toDomain(): Song = Song(
    id = id,
    name = name,
    artistName = user.nickname ?: user.username,
    artistUsername = user.username,
    imageUrl = image,
    durationSeconds = length,
    playsCount = playsCount,
)

private fun AlbumResult.toDomain(): Album = Album(
    id = id,
    name = name,
    imageUrl = image,
    songsCount = songsCount,
    artistName = user?.nickname ?: user?.username,
    artistUsername = user?.username,
    createdAt = createdAt,
)

private fun AlbumDetailResponse.toDomain(): Album = Album(
    id = id,
    name = name,
    imageUrl = image,
    songsCount = songsCount,
    artistName = user?.nickname ?: user?.username,
    artistUsername = user?.username,
    createdAt = createdAt,
)

private fun NestedSong.toDomain(): Song = Song(
    id = id,
    name = name,
    artistName = user.profile?.nickname ?: user.username,
    artistUsername = user.username,
    imageUrl = image,
    durationSeconds = length,
    playsCount = playsCount,
)

private fun PlaylistDetailResponse.toDomain(): Playlist = Playlist(
    id = id,
    name = name,
    imageUrl = image,
    description = description,
    songsCount = songsCount,
    playsCount = playsCount,
    likesCount = likesCount,
    curatorName = user?.profile?.nickname ?: user?.username,
    createdAt = createdAt,
)

private fun PlaylistResult.toDomain(): Playlist = Playlist(
    id = id,
    name = name,
    imageUrl = image,
    songsCount = songsCount,
    curatorName = user?.profile?.nickname ?: user?.username,
)
