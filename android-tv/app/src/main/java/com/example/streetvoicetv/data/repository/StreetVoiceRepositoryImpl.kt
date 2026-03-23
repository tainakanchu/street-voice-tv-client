package com.example.streetvoicetv.data.repository

import com.example.streetvoicetv.data.api.StreetVoiceApi
import com.example.streetvoicetv.data.model.SearchSongResult
import com.example.streetvoicetv.data.model.SongDetailResponse
import com.example.streetvoicetv.domain.model.PlayableStream
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreetVoiceRepositoryImpl @Inject constructor(
    private val api: StreetVoiceApi,
) : StreetVoiceRepository {

    override suspend fun searchSongs(query: String, limit: Int, offset: Int): Result<List<Song>> {
        return runCatching {
            val response = api.searchSongs(query, limit, offset)
            response.results.map { it.toDomainModel() }
        }
    }

    override suspend fun getSongDetail(songId: Int): Result<Song> {
        return runCatching {
            val response = api.getSongDetail(songId)
            response.toDomainModel()
        }
    }

    override suspend fun getStreamUrl(songId: Int): Result<PlayableStream> {
        return runCatching {
            val response = api.getStreamUrl(songId)
            PlayableStream(songId = songId, hlsUrl = response.file)
        }
    }
}

private fun SearchSongResult.toDomainModel(): Song {
    return Song(
        id = id,
        name = name,
        artistName = user.nickname ?: user.username,
        artistUsername = user.username,
        imageUrl = image,
        durationSeconds = length,
        playsCount = playsCount,
    )
}

private fun SongDetailResponse.toDomainModel(): Song {
    return Song(
        id = id,
        name = name,
        artistName = user.nickname ?: user.username,
        artistUsername = user.username,
        imageUrl = image,
        durationSeconds = length,
        playsCount = 0,
        genre = genre,
        synopsis = synopsis,
        lyrics = lyrics,
        albumName = album?.name,
        albumImageUrl = album?.image,
        artistProfileImageUrl = user.profileImage,
    )
}
