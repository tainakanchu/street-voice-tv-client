package com.example.streetvoicetv.data.api

import com.example.streetvoicetv.data.model.SearchResponse
import com.example.streetvoicetv.data.model.SongDetailResponse
import com.example.streetvoicetv.data.model.StreamResponse
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
    suspend fun searchSongs(query: String, limit: Int, offset: Int): SearchResponse {
        val url = "${config.baseUrl}/api/v4/search/?q=$query&type=song&limit=$limit&offset=$offset"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .build()

        return executeRequest(request) { body ->
            json.decodeFromString<SearchResponse>(body)
        }
    }

    suspend fun getSongDetail(songId: Int): SongDetailResponse {
        val url = "${config.baseUrl}/api/v5/song/$songId/"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("Accept", "application/json")
            .build()

        return executeRequest(request) { body ->
            json.decodeFromString<SongDetailResponse>(body)
        }
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

        return executeRequest(request) { body ->
            json.decodeFromString<StreamResponse>(body)
        }
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
