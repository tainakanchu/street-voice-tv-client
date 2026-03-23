package com.example.streetvoicetv.data.api

import com.example.streetvoicetv.data.model.AlbumDetailResponse
import com.example.streetvoicetv.data.model.AlbumListResponse
import com.example.streetvoicetv.data.model.AlbumSongsResponse
import com.example.streetvoicetv.data.model.ChartResponse
import com.example.streetvoicetv.data.model.EditorChoiceResponse
import com.example.streetvoicetv.data.model.PlaylistDetailResponse
import com.example.streetvoicetv.data.model.PlaylistSongsResponse
import com.example.streetvoicetv.data.model.SearchResponse
import com.example.streetvoicetv.data.model.SectionPlaylistsResponse
import com.example.streetvoicetv.data.model.SongDetailResponse
import com.example.streetvoicetv.data.model.StreamResponse
import com.example.streetvoicetv.data.model.UserDetailResponse
import com.example.streetvoicetv.data.model.UserSearchResponse
import com.example.streetvoicetv.data.model.UserSongsResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreetVoiceApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val config: ApiConfig,
) {
    // --- Search ---

    suspend fun searchSongs(query: String, limit: Int, offset: Int): SearchResponse {
        val url = "${config.baseUrl}/api/v4/search/?q=$query&type=song&limit=$limit&offset=$offset"
        return get(url) { json.decodeFromString<SearchResponse>(it) }
    }

    suspend fun searchUsers(query: String, limit: Int, offset: Int): UserSearchResponse {
        val url = "${config.baseUrl}/api/v4/search/?q=$query&type=user&limit=$limit&offset=$offset"
        return get(url) { json.decodeFromString<UserSearchResponse>(it) }
    }

    // --- Song ---

    suspend fun getSongDetail(songId: Int): SongDetailResponse {
        val url = "${config.baseUrl}/api/v5/song/$songId/"
        return get(url) { json.decodeFromString<SongDetailResponse>(it) }
    }

    suspend fun getStreamUrl(songId: Int): StreamResponse {
        val url = "${config.baseUrl}/api/v4/song/$songId/hls/master/"
        val emptyBody = "{}".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(emptyBody)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "StreetVoiceTV/1.0")
            .addHeader("Referer", "${config.baseUrl}/")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .build()

        return executeRequest(request) { json.decodeFromString<StreamResponse>(it) }
    }

    // --- User / Artist ---

    suspend fun getUserDetail(username: String): UserDetailResponse {
        val url = "${config.baseUrl}/api/v4/user/$username/"
        return get(url) { json.decodeFromString<UserDetailResponse>(it) }
    }

    suspend fun getUserSongs(username: String, limit: Int, offset: Int): UserSongsResponse {
        val url = "${config.baseUrl}/api/v4/user/$username/songs/?limit=$limit&offset=$offset"
        return get(url) { json.decodeFromString<UserSongsResponse>(it) }
    }

    suspend fun getUserAlbums(username: String, limit: Int, offset: Int): AlbumListResponse {
        val url = "${config.baseUrl}/api/v4/user/$username/albums/?limit=$limit&offset=$offset"
        return get(url) { json.decodeFromString<AlbumListResponse>(it) }
    }

    // --- Album ---

    suspend fun getAlbumDetail(albumId: Int): AlbumDetailResponse {
        val url = "${config.baseUrl}/api/v4/album/$albumId/"
        return get(url) { json.decodeFromString<AlbumDetailResponse>(it) }
    }

    suspend fun getAlbumSongs(albumId: Int, limit: Int, offset: Int): AlbumSongsResponse {
        val url = "${config.baseUrl}/api/v4/album/$albumId/songs/?limit=$limit&offset=$offset"
        return get(url) { json.decodeFromString<AlbumSongsResponse>(it) }
    }

    // --- Play Event ---

    suspend fun reportPlay(songId: Int) {
        val url = "${config.baseUrl}/api/v4/song/$songId/play/"
        val emptyBody = "{}".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(emptyBody)
            .addHeader("Accept", "application/json")
            .addHeader("User-Agent", "StreetVoiceTV/1.0")
            .addHeader("Referer", "${config.baseUrl}/")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .build()
        executeRequest(request) { it }
    }

    // --- Home / Discover ---

    suspend fun getRealtimeChart(style: String = "all", limit: Int = 20): ChartResponse {
        val url = "${config.baseUrl}/api/v5/chart/realtime/$style/?limit=$limit"
        return get(url) { json.decodeFromString<ChartResponse>(it) }
    }

    suspend fun getEditorChoice(limit: Int = 10): EditorChoiceResponse {
        val url = "${config.baseUrl}/api/v4/editor_choice/?limit=$limit"
        return get(url) { json.decodeFromString<EditorChoiceResponse>(it) }
    }

    suspend fun getSectionPlaylists(sectionId: Int, limit: Int = 10): SectionPlaylistsResponse {
        val url = "${config.baseUrl}/api/v4/playlist_section/$sectionId/playlists/?limit=$limit"
        return get(url) { json.decodeFromString<SectionPlaylistsResponse>(it) }
    }

    suspend fun getPlaylistDetail(playlistId: Int): PlaylistDetailResponse {
        val url = "${config.baseUrl}/api/v4/playlist/$playlistId/"
        return get(url) { json.decodeFromString<PlaylistDetailResponse>(it) }
    }

    suspend fun getPlaylistSongs(playlistId: Int, limit: Int = 50, offset: Int = 0): PlaylistSongsResponse {
        val url = "${config.baseUrl}/api/v4/playlist/$playlistId/songs/?limit=$limit&offset=$offset"
        return get(url) { json.decodeFromString<PlaylistSongsResponse>(it) }
    }

    // --- Internal ---

    private suspend fun <T> get(url: String, parser: (String) -> T): T {
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .build()
        return executeRequest(request, parser)
    }

    private suspend fun <T> executeRequest(request: Request, parser: (String) -> T): T {
        return withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw ApiException(
                    code = response.code,
                    message = "API request failed: ${response.code} ${response.message}",
                )
            }
            val body = response.body?.string()
                ?: throw ApiException(code = response.code, message = "Empty response body")
            parser(body)
        }
    }
}

data class ApiConfig(
    val baseUrl: String,
)

class ApiException(
    val code: Int,
    override val message: String,
) : Exception(message)
