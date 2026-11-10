package com.example.streetvoicetv.data.api

import com.example.streetvoicetv.data.auth.SessionCookies
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.data.auth.toCookieHeader
import com.example.streetvoicetv.data.model.AlbumDetailResponse
import com.example.streetvoicetv.data.model.AlbumListResponse
import com.example.streetvoicetv.data.model.AlbumSongsResponse
import com.example.streetvoicetv.data.model.ChartResponse
import com.example.streetvoicetv.data.model.CurrentUserResponse
import com.example.streetvoicetv.data.model.EditorChoiceResponse
import com.example.streetvoicetv.data.model.LikedSongsResponse
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
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.serializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * StreetVoice HTTP API. All URLs are built with [HttpUrl.Builder] so user input (search
 * queries, usernames) is percent-encoded instead of being spliced into the URL.
 *
 * Session invalidation: when a request was sent with a session cookie and the server answers
 * - 401 on any endpoint, or
 * - 403 on the session-check endpoint (`/api/v4/user/me/`, which is user-scoped, so a 403
 *   there means "not authenticated" — Django REST framework's SessionAuthentication answers
 *   403 rather than 401),
 * the session is cleared so the UI shows the logged-out state. Other 403s (CSRF failures on
 * like/unlike, rate limiting, private content) are NOT treated as a logout.
 */
@Singleton
class StreetVoiceApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    config: ApiConfig,
    private val sessionManager: SessionManager,
) {
    private val baseUrl: HttpUrl = config.baseUrl.toHttpUrl()

    // --- Search ---

    suspend fun searchSongs(query: String, limit: Int, offset: Int): SearchResponse =
        get(searchUrl(query, "song", limit, offset))

    suspend fun searchUsers(query: String, limit: Int, offset: Int): UserSearchResponse =
        get(searchUrl(query, "user", limit, offset))

    suspend fun searchPlaylists(query: String, limit: Int, offset: Int): SectionPlaylistsResponse =
        get(searchUrl(query, "playlist", limit, offset))

    internal fun searchUrl(query: String, type: String, limit: Int, offset: Int): HttpUrl =
        url("api", "v4", "search") {
            addQueryParameter("q", query)
            addQueryParameter("type", type)
            page(limit, offset)
        }

    // --- Song ---

    suspend fun getSongDetail(songId: Int): SongDetailResponse =
        get(url("api", "v5", "song", songId.toString()))

    suspend fun getStreamUrl(songId: Int): StreamResponse =
        post(url("api", "v4", "song", songId.toString(), "hls", "master"))

    // --- User / Artist ---

    /** The logged-in user; also serves as the session check (see class doc). */
    suspend fun getCurrentUser(): CurrentUserResponse =
        get(url("api", "v4", "user", "me"), isSessionCheck = true)

    /**
     * `/user/me/` with explicitly given cookies (e.g. just captured from the login WebView,
     * not yet saved). A failure here never touches the stored session.
     */
    suspend fun getCurrentUser(cookies: SessionCookies): CurrentUserResponse =
        get(url("api", "v4", "user", "me"), explicitCookies = cookies)

    suspend fun getUserDetail(username: String): UserDetailResponse =
        get(userUrl(username))

    suspend fun getUserSongs(username: String, limit: Int, offset: Int): UserSongsResponse =
        get(userUrl(username, "songs") { page(limit, offset) })

    suspend fun getUserAlbums(username: String, limit: Int, offset: Int): AlbumListResponse =
        get(userUrl(username, "albums") { page(limit, offset) })

    suspend fun getUserPlaylists(username: String, limit: Int = 20, offset: Int = 0): SectionPlaylistsResponse =
        get(userUrl(username, "playlists") { page(limit, offset) })

    suspend fun getUserLikedSongs(username: String, limit: Int = 50, offset: Int = 0): LikedSongsResponse =
        get(userUrl(username, "likes") { page(limit, offset) })

    internal fun userUrl(
        username: String,
        sub: String? = null,
        query: HttpUrl.Builder.() -> Unit = {},
    ): HttpUrl {
        // HttpUrl treats "." / ".." segments as relative path steps; never let them through.
        require(username.isNotBlank() && username != "." && username != "..") { "invalid username" }
        val segments = listOfNotNull("api", "v4", "user", username, sub)
        return url(*segments.toTypedArray(), query = query)
    }

    // --- Album ---

    suspend fun getAlbumDetail(albumId: Int): AlbumDetailResponse =
        get(url("api", "v4", "album", albumId.toString()))

    suspend fun getAlbumSongs(albumId: Int, limit: Int, offset: Int): AlbumSongsResponse =
        get(url("api", "v4", "album", albumId.toString(), "songs") { page(limit, offset) })

    // --- Play Event ---

    suspend fun reportPlay(songId: Int) {
        execute(postRequest(url("api", "v4", "song", songId.toString(), "play"))) { }
    }

    // --- Like ---

    suspend fun likeSong(songId: Int) {
        execute(postRequest(likesUrl(songId))) { }
    }

    suspend fun unlikeSong(songId: Int) {
        execute(ajaxRequest(likesUrl(songId)).delete().build()) { }
    }

    private fun likesUrl(songId: Int) = url("api", "v4", "song", songId.toString(), "likes")

    // --- Home / Discover ---

    suspend fun getRealtimeChart(style: String = "all", limit: Int = 20): ChartResponse =
        get(url("api", "v5", "chart", "realtime", style) { addQueryParameter("limit", limit.toString()) })

    suspend fun getEditorChoice(limit: Int = 10): EditorChoiceResponse =
        get(url("api", "v4", "editor_choice") { addQueryParameter("limit", limit.toString()) })

    suspend fun getSectionPlaylists(sectionId: Int, limit: Int = 10): SectionPlaylistsResponse =
        get(
            url("api", "v4", "playlist_section", sectionId.toString(), "playlists") {
                addQueryParameter("limit", limit.toString())
            },
        )

    suspend fun getPlaylistDetail(playlistId: Int): PlaylistDetailResponse =
        get(url("api", "v4", "playlist", playlistId.toString()))

    suspend fun getPlaylistSongs(playlistId: Int, limit: Int = 50, offset: Int = 0): PlaylistSongsResponse =
        get(url("api", "v4", "playlist", playlistId.toString(), "songs") { page(limit, offset) })

    // --- Internal ---

    /**
     * `baseUrl` + percent-encoded [segments] + trailing slash (the API is Django-style and
     * expects it).
     */
    internal fun url(vararg segments: String, query: HttpUrl.Builder.() -> Unit = {}): HttpUrl =
        baseUrl.newBuilder()
            .apply {
                segments.forEach { addPathSegment(it) }
                addPathSegment("") // trailing slash
                query()
            }
            .build()

    private fun HttpUrl.Builder.page(limit: Int, offset: Int) {
        addQueryParameter("limit", limit.toString())
        addQueryParameter("offset", offset.toString())
    }

    private suspend inline fun <reified T> get(
        url: HttpUrl,
        isSessionCheck: Boolean = false,
        explicitCookies: SessionCookies? = null,
    ): T {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .apply {
                // SessionCookieInterceptor leaves an explicit Cookie header alone.
                if (explicitCookies != null) header("Cookie", explicitCookies.toCookieHeader())
            }
            .build()
        val deserializer = serializer<T>()
        return execute(request, isSessionCheck, usesStoredSession = explicitCookies == null) {
            decode(it, deserializer)
        }
    }

    private suspend inline fun <reified T> post(url: HttpUrl): T {
        val deserializer = serializer<T>()
        return execute(postRequest(url)) { decode(it, deserializer) }
    }

    private fun ajaxRequest(url: HttpUrl): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "StreetVoiceTV/1.0")
            .header("Referer", baseUrl.toString())
            .header("X-Requested-With", "XMLHttpRequest")

    private fun postRequest(url: HttpUrl): Request =
        ajaxRequest(url).post(EMPTY_JSON.toRequestBody(JSON_MEDIA_TYPE)).build()

    @OptIn(ExperimentalSerializationApi::class)
    private fun <T> decode(response: Response, deserializer: DeserializationStrategy<T>): T {
        val body = response.body
            ?: throw ApiException(code = response.code, message = "Empty response body")
        // Stream straight into the decoder instead of materialising the body as a String.
        return body.byteStream().use { json.decodeFromStream(deserializer, it) }
    }

    /**
     * Runs [request] on the IO dispatcher. The response is always closed (also on non-2xx).
     * See the class doc for when a 401/403 clears the session.
     */
    private suspend fun <T> execute(
        request: Request,
        isSessionCheck: Boolean = false,
        usesStoredSession: Boolean = true,
        handle: (Response) -> T,
    ): T = withContext(Dispatchers.IO) {
        // SessionCookieInterceptor attaches exactly this session to API-host requests.
        val sentSessionId = if (usesStoredSession) sessionManager.getSessionId() else null
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (sentSessionId != null && isSessionRejected(response.code, isSessionCheck)) {
                    sessionManager.clearSessionIfCurrent(sentSessionId)
                }
                throw ApiException(
                    code = response.code,
                    message = "API request failed: ${response.code} ${response.message}",
                )
            }
            handle(response)
        }
    }

    companion object {
        private const val EMPTY_JSON = "{}"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        /** Whether an error [code] means the sent session is no longer valid (see class doc). */
        internal fun isSessionRejected(code: Int, isSessionCheck: Boolean): Boolean =
            code == 401 || (code == 403 && isSessionCheck)
    }
}

data class ApiConfig(
    val baseUrl: String,
)

class ApiException(
    val code: Int,
    override val message: String,
) : Exception(message)
