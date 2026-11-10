package com.example.streetvoicetv.data.api

import android.app.Application
import android.content.Context
import com.example.streetvoicetv.data.auth.SessionCookieInterceptor
import com.example.streetvoicetv.data.auth.SessionCookies
import com.example.streetvoicetv.data.auth.SessionManager
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StreetVoiceApiTest {

    private val server = MockWebServer()
    private lateinit var session: SessionManager
    private lateinit var api: StreetVoiceApi
    private lateinit var client: OkHttpClient
    private var webCookiesCleared = 0

    @Before
    fun setUp() {
        server.start()
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("api_test_session", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        session = SessionManager(prefs) { webCookiesCleared++ }
        val config = ApiConfig(baseUrl = server.url("/").toString().removeSuffix("/"))
        client = OkHttpClient.Builder()
            .addInterceptor(SessionCookieInterceptor(session, config))
            .build()
        api = StreetVoiceApi(client, Json { ignoreUnknownKeys = true }, config, session)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun enqueueJson(body: String, code: Int = 200) =
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    // ------------------------------------------------------------------ URL building

    @Test
    fun `search query is percent-encoded`() {
        for (query in listOf("Tom & Jerry", "#tag", "C++", "a=b?c", "周杰倫 稻香", "50%")) {
            val url = api.searchUrl(query, "song", 10, 0)
            // Round-trips exactly: no part of the query leaks into other parameters.
            assertEquals(query, url.queryParameter("q"))
            assertEquals("song", url.queryParameter("type"))
            assertEquals("10", url.queryParameter("limit"))
            assertEquals(listOf("q", "type", "limit", "offset"), url.queryParameterNames.toList())
            assertNull(url.fragment)
        }
        val raw = api.searchUrl("Tom & Jerry", "song", 10, 0).toString()
        assertTrue(raw, raw.endsWith("/api/v4/search/?q=Tom%20%26%20Jerry&type=song&limit=10&offset=0"))
        assertTrue(api.searchUrl("C++", "song", 1, 0).toString().contains("q=C%2B%2B"))
        assertTrue(api.searchUrl("#tag", "song", 1, 0).toString().contains("q=%23tag"))
    }

    @Test
    fun `usernames are single encoded path segments with trailing slash`() {
        val url = api.userUrl("a/b c?#", "songs") { addQueryParameter("limit", "5") }
        assertEquals(listOf("api", "v4", "user", "a/b c?#", "songs", ""), url.pathSegments)
        assertTrue(url.encodedPath, url.encodedPath.endsWith("/api/v4/user/a%2Fb%20c%3F%23/songs/"))
        assertEquals("5", url.queryParameter("limit"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `dot-dot username is rejected`() {
        api.userUrl("..")
    }

    @Test
    fun `requests hit the encoded url`() = runTest {
        enqueueJson("""{"count":0,"offset":0,"limit":10,"results":[]}""")
        api.searchSongs("Tom & Jerry", 10, 0)
        val recorded = server.takeRequest()
        assertEquals("Tom & Jerry", recorded.requestUrl!!.queryParameter("q"))
        assertEquals("song", recorded.requestUrl!!.queryParameter("type"))
    }

    // ------------------------------------------------------------------ errors & session

    @Test
    fun `non-2xx throws ApiException with the code`() = runTest {
        enqueueJson("nope", code = 500)
        try {
            api.getAlbumDetail(1)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(500, e.code)
        }
    }

    @Test
    fun `401 with a session clears the session`() = runTest {
        session.saveSession("sid", "csrf", "me")
        enqueueJson("{}", code = 401)
        runCatching { api.getUserLikedSongs("me") }
        assertFalse(session.session.value.isLoggedIn)
        assertNull(session.getSessionId())
        assertEquals(1, webCookiesCleared)
    }

    @Test
    fun `403 on an ordinary endpoint keeps the session`() = runTest {
        session.saveSession("sid", "csrf", "me")
        enqueueJson("{}", code = 403) // e.g. CSRF failure or rate limit
        runCatching { api.likeSong(1) }
        assertTrue(session.session.value.isLoggedIn)
    }

    @Test
    fun `403 on user me clears the session`() = runTest {
        session.saveSession("sid", "csrf", "me")
        enqueueJson("{}", code = 403)
        runCatching { api.getCurrentUser() }
        assertFalse(session.session.value.isLoggedIn)
    }

    @Test
    fun `401 without a session does nothing`() = runTest {
        enqueueJson("{}", code = 401)
        runCatching { api.getSongDetail(1) }
        assertEquals(0, webCookiesCleared)
    }

    @Test
    fun `a late 401 for an old session does not clear a newer session`() {
        session.saveSession("new", "csrf", "me")
        assertFalse(session.clearSessionIfCurrent("old"))
        assertTrue(session.session.value.isLoggedIn)
    }

    @Test
    fun `explicit cookies are sent as is and failures never clear the stored session`() = runTest {
        session.saveSession("stored", "csrf", "me")
        enqueueJson("{}", code = 403)
        runCatching { api.getCurrentUser(SessionCookies("sessionid", "fresh", null, null)) }
        assertEquals("sessionid=fresh", server.takeRequest().getHeader("Cookie"))
        assertTrue(session.session.value.isLoggedIn)

        enqueueJson("""{"username":"alice","id":3}""")
        assertEquals("alice", api.getCurrentUser(SessionCookies("session", "x", "csrf-token", "t")).username)
        assertEquals("session=x; csrf-token=t", server.takeRequest().getHeader("Cookie"))
    }

    // ------------------------------------------------------------------ interceptor

    private fun send(method: String, url: okhttp3.HttpUrl = server.url("/api/x/")): okhttp3.mockwebserver.RecordedRequest {
        server.enqueue(MockResponse())
        val body = if (method == "GET" || method == "HEAD") null else okhttp3.RequestBody.create(null, ByteArray(0))
        client.newCall(Request.Builder().url(url).method(method, body).build()).execute().close()
        return server.takeRequest()
    }

    @Test
    fun `interceptor sends the stored cookie name and csrf header only for unsafe methods`() {
        session.saveSession("sid", "tok", "me", sessionCookieName = "sessionid", csrfCookieName = "csrftoken")

        val get = send("GET")
        assertEquals("sessionid=sid; csrftoken=tok", get.getHeader("Cookie"))
        assertNull(get.getHeader("X-CSRFToken"))
        assertNull(send("HEAD").getHeader("X-CSRFToken"))

        for (method in listOf("POST", "DELETE", "PUT", "PATCH")) {
            assertEquals(method, "tok", send(method).getHeader("X-CSRFToken"))
        }
    }

    @Test
    fun `blank csrf is stored as absent and no empty header is sent`() {
        session.saveSession("sid", "", "me")
        assertNull(session.getCsrfToken())
        val post = send("POST")
        assertEquals("session=sid", post.getHeader("Cookie"))
        assertNull(post.getHeader("X-CSRFToken"))
    }

    @Test
    fun `no session means no cookie`() {
        val get = send("GET")
        assertNull(get.getHeader("Cookie"))
    }

    @Test
    fun `other hosts never get the session`() {
        session.saveSession("sid", "tok", "me")
        // Same server, but addressed by a different host name than the API base URL.
        val otherHost = server.url("/x").newBuilder().host("127.0.0.1").build()
        val apiHost = server.url("/").host
        if (apiHost == "127.0.0.1") return // cannot distinguish on this machine
        val recorded = send("POST", otherHost)
        assertNull(recorded.getHeader("Cookie"))
        assertNull(recorded.getHeader("X-CSRFToken"))
    }

    @Test
    fun `host matching accepts subdomains but not lookalike hosts`() {
        val interceptor = SessionCookieInterceptor(session, ApiConfig("https://streetvoice.com"))
        assertTrue(interceptor.isApiHost("streetvoice.com"))
        assertTrue(interceptor.isApiHost("www.streetvoice.com"))
        assertFalse(interceptor.isApiHost("evilstreetvoice.com"))
        assertFalse(interceptor.isApiHost("api.github.com"))
        assertFalse(interceptor.isApiHost("streetvoice.com.evil.io"))
    }
}
