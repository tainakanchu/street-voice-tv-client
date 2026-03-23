package com.example.streetvoicetv.domain.repository

import com.example.streetvoicetv.domain.model.PlayableStream
import com.example.streetvoicetv.domain.model.Song

interface StreetVoiceRepository {

    suspend fun searchSongs(query: String, limit: Int = 10, offset: Int = 0): Result<List<Song>>

    suspend fun getSongDetail(songId: Int): Result<Song>

    suspend fun getStreamUrl(songId: Int): Result<PlayableStream>
}
