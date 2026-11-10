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
import com.example.streetvoicetv.domain.model.Page
import com.example.streetvoicetv.domain.model.PlayableStream
import com.example.streetvoicetv.domain.model.Playlist
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreetVoiceRepositoryImpl @Inject constructor(
    private val api: StreetVoiceApi,
) : StreetVoiceRepository {

    // --- Search ---

    override suspend fun searchSongs(query: String, limit: Int, offset: Int): Result<Page<Song>> {
        return apiCatching {
            api.searchSongs(query, limit, offset).let { pageOf(it.count, it.offset, it.next, it.results) { r -> r.toDomain() } }
        }
    }

    override suspend fun searchArtists(query: String, limit: Int, offset: Int): Result<Page<Artist>> {
        return apiCatching {
            api.searchUsers(query, limit, offset).let { pageOf(it.count, it.offset, it.next, it.results) { r -> r.toDomain() } }
        }
    }

    override suspend fun searchPlaylists(query: String, limit: Int, offset: Int): Result<Page<Playlist>> {
        return apiCatching {
            api.searchPlaylists(query, limit, offset).let { pageOf(it.count, it.offset, it.next, it.results) { r -> r.toDomain() } }
        }
    }

    // --- Song ---

    override suspend fun getSongDetail(songId: Int): Result<Song> {
        return apiCatching {
            api.getSongDetail(songId).toDomain()
        }
    }

    override suspend fun getStreamUrl(songId: Int): Result<PlayableStream> {
        return apiCatching {
            PlayableStream(songId = songId, hlsUrl = api.getStreamUrl(songId).file)
        }
    }

    override suspend fun likeSong(songId: Int): Result<Unit> {
        return apiCatching { api.likeSong(songId) }
    }

    override suspend fun unlikeSong(songId: Int): Result<Unit> {
        return apiCatching { api.unlikeSong(songId) }
    }

    // --- Artist ---

    override suspend fun getArtistDetail(username: String): Result<Artist> {
        return apiCatching {
            api.getUserDetail(username).toDomain()
        }
    }

    override suspend fun getArtistSongs(username: String, limit: Int, offset: Int): Result<Page<Song>> {
        return apiCatching {
            api.getUserSongs(username, limit, offset).let { pageOf(it.count, it.offset, it.next, it.results) { r -> r.toDomain() } }
        }
    }

    override suspend fun getArtistAlbums(username: String, limit: Int, offset: Int): Result<Page<Album>> {
        return apiCatching {
            api.getUserAlbums(username, limit, offset).let { pageOf(it.count, it.offset, it.next, it.results) { r -> r.toDomain() } }
        }
    }

    override suspend fun getArtistPlaylists(username: String, limit: Int, offset: Int): Result<List<Playlist>> {
        return apiCatching {
            api.getUserPlaylists(username, limit, offset).results.map { it.toDomain() }
        }
    }

    override suspend fun getLikedSongs(username: String, limit: Int, offset: Int): Result<Page<Song>> {
        return apiCatching {
            api.getUserLikedSongs(username, limit, offset)
                .let { pageOf(it.count, it.offset, it.next, it.results) { r -> r.contentObject.toDomain() } }
        }
    }

    // --- Album ---

    override suspend fun getAlbumDetail(albumId: Int): Result<Album> {
        return apiCatching {
            api.getAlbumDetail(albumId).toDomain()
        }
    }

    override suspend fun getAlbumSongs(albumId: Int, limit: Int, offset: Int): Result<Page<Song>> {
        return apiCatching {
            api.getAlbumSongs(albumId, limit, offset).let { pageOf(it.count, it.offset, it.next, it.results) { r -> r.toDomain() } }
        }
    }

    // --- Home / Discover ---

    override suspend fun getRealtimeChart(limit: Int): Result<List<Song>> {
        return apiCatching {
            api.getRealtimeChart(limit = limit).results.map { it.song.toDomain() }
        }
    }

    override suspend fun getEditorChoice(limit: Int): Result<List<Song>> {
        return apiCatching {
            api.getEditorChoice(limit = limit).results.map { it.contentObject.toDomain() }
        }
    }

    override suspend fun getPlaylistDetail(playlistId: Int): Result<Playlist> {
        return apiCatching {
            api.getPlaylistDetail(playlistId).toDomain()
        }
    }

    override suspend fun getRecommendedPlaylists(limit: Int): Result<List<Playlist>> {
        return apiCatching {
            api.getSectionPlaylists(sectionId = 1, limit = limit).results.map { it.toDomain() }
        }
    }

    override suspend fun getPlaylistSongs(playlistId: Int, limit: Int, offset: Int): Result<Page<Song>> {
        return apiCatching {
            api.getPlaylistSongs(playlistId, limit, offset)
                .let { pageOf(it.count, it.offset, it.next, it.results) { r -> r.song.toDomain() } }
        }
    }
}

/**
 * Like [runCatching], but lets [CancellationException] propagate so a cancelled caller
 * (e.g. a superseded load) stops instead of seeing a failed [Result].
 */
private inline fun <T> apiCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

/**
 * Maps one page of a `{count, offset, limit, next, previous, results}` response.
 * The next offset is read from the `next` URL (falling back to offset + page size); a missing
 * `next` or an empty page ends the list, and a next offset that would not advance is ignored
 * so a misbehaving server cannot make callers loop on the same page.
 */
internal fun <R, T> pageOf(count: Int?, offset: Int, next: String?, results: List<R>, map: (R) -> T): Page<T> =
    Page(items = results.map(map), total = count, nextOffset = nextOffsetOf(offset, results.size, next))

internal fun nextOffsetOf(offset: Int, pageSize: Int, next: String?): Int? {
    if (next == null || pageSize == 0) return null
    val fromUrl = next.toHttpUrlOrNull()?.queryParameter("offset")?.toIntOrNull()
    return fromUrl?.takeIf { it > offset } ?: (offset + pageSize)
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
