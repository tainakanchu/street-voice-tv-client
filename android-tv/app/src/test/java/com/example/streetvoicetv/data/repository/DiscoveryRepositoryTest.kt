package com.example.streetvoicetv.data.repository

import android.app.Application
import android.content.Context
import com.example.streetvoicetv.data.api.ApiConfig
import com.example.streetvoicetv.data.api.StreetVoiceApi
import com.example.streetvoicetv.data.auth.SessionCookieInterceptor
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.domain.model.ChartPeriod
import com.example.streetvoicetv.domain.model.Genre
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Genres, charts, following and follow / unfollow against a MockWebServer (shapes as of 2026-11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DiscoveryRepositoryTest {

    private val server = MockWebServer()
    private lateinit var session: SessionManager
    private lateinit var repository: StreetVoiceRepositoryImpl

    @Before
    fun setUp() {
        server.start()
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
        val prefs = RuntimeEnvironment.getApplication()
            .getSharedPreferences("discovery_test_session", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        session = SessionManager(prefs) {}
        val config = ApiConfig(server.url("").toString().trimEnd('/'))
        val client = OkHttpClient.Builder().addInterceptor(SessionCookieInterceptor(session, config)).build()
        repository = StreetVoiceRepositoryImpl(StreetVoiceApi(client, json, config, session))
    }

    @After
    fun tearDown() = server.shutdown()

    private fun enqueue(body: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code).setBody(body))

    private fun base() = server.url("").toString().trimEnd('/')

    @Test
    fun `genres are mapped from id + genre`() = runBlocking {
        enqueue("""{"results":[{"id":0,"genre":"分類：未分類"},{"id":1,"genre":"Rock"}]}""")
        val genres = repository.getGenres().getOrThrow()
        assertEquals(listOf(Genre(0, "分類：未分類"), Genre(1, "Rock")), genres)
        assertEquals("/api/v4/song/genre/", server.takeRequest().path)
    }

    @Test
    fun `weekly chart of a style - path, paging and song fields`() = runBlocking {
        enqueue(
            """{"offset":20,"limit":20,"next":"${base()}/api/v5/chart/weekly/rock/?limit=20&offset=40","previous":null,
               "results":[{"type":"chart_weekly","id":5,"song":{"id":887406,"name":"不會醒的夢","length":205,"genre":2,
               "publish_at":"2026-11-03T18:18:42+0800","user":{"username":"Langzi","profile":{"nickname":"質感流氓"}}}}]}""",
        )
        val page = repository.getChart("rock", ChartPeriod.Weekly, offset = 20, limit = 20).getOrThrow()

        assertEquals("/api/v5/chart/weekly/rock/?limit=20&offset=20", server.takeRequest().path)
        val song = page.items.single()
        assertEquals(887406, song.id)
        assertEquals("質感流氓", song.artistName)
        assertEquals(2, song.genre)
        assertEquals("2026-11-03T18:18:42+0800", song.publishAt)
        assertNull(page.total)
        assertEquals(40, page.nextOffset)
    }

    @Test
    fun `chart end - no next url`() = runBlocking {
        enqueue("""{"offset":40,"limit":20,"next":null,"results":[{"id":1,"song":{"id":1,"name":"x","user":{"username":"u"}}}]}""")
        val page = repository.getChart("all", ChartPeriod.Realtime, offset = 40).getOrThrow()
        assertEquals("/api/v5/chart/realtime/all/?limit=20&offset=40", server.takeRequest().path)
        assertFalse(page.hasMore)
    }

    @Test
    fun `following maps to_user with nested profile`() = runBlocking {
        enqueue(
            """{"count":7,"offset":0,"limit":2,"next":"${base()}/api/v4/user/a/following/?limit=2&offset=2","previous":null,
               "results":[{"to_user":{"type":"user","id":2164812,"username":"Lostyihateu",
               "profile":{"image":"https://img/p.jpg","nickname":"Losty✨","is_follow":false}},"created_at":"2026-06-12T06:19:08Z"}]}""",
        )
        val page = repository.getFollowing("a", limit = 2, offset = 0).getOrThrow()
        assertEquals("/api/v4/user/a/following/?limit=2&offset=0", server.takeRequest().path)
        val artist = page.items.single()
        assertEquals("Lostyihateu", artist.username)
        assertEquals("Losty✨", artist.displayName)
        assertEquals("https://img/p.jpg", artist.profileImageUrl)
        assertEquals(7, page.total)
        assertEquals(2, page.nextOffset)
    }

    @Test
    fun `user detail reads the nested profile, including is_follow`() = runBlocking {
        enqueue(
            """{"type":"user","id":3,"username":"Langzi","profile":{"image":"https://img/p.jpg","nickname":"質感流氓",
               "follower_count":4493,"following_count":7,"cover_image":"https://img/c.jpg","introduction":"天才一個",
               "is_follow":true,"songs_count":22}}""",
        )
        val artist = repository.getArtistDetail("Langzi").getOrThrow()
        assertEquals("質感流氓", artist.displayName)
        assertEquals("https://img/p.jpg", artist.profileImageUrl)
        assertEquals("https://img/c.jpg", artist.coverImageUrl)
        assertEquals("天才一個", artist.introduction)
        assertEquals(4493, artist.followersCount)
        assertEquals(7, artist.followingCount)
        assertEquals(22, artist.songsCount)
        assertEquals(true, artist.isFollowing)
    }

    @Test
    fun `user songs carry the nested nickname and publish_at`() = runBlocking {
        enqueue(
            """{"count":1,"offset":0,"limit":10,"next":null,"results":[{"id":9,"name":"s","length":10,
               "publish_at":"2026-06-11T17:27:06+0800","user":{"username":"u","profile":{"nickname":"暱稱"}}}]}""",
        )
        val song = repository.getArtistSongs("u", limit = 10).getOrThrow().items.single()
        assertEquals("暱稱", song.artistName)
        assertEquals("2026-06-11T17:27:06+0800", song.publishAt)
    }

    @Test
    fun `follow is a POST and unfollow a DELETE to user follow, with the CSRF header`() = runBlocking {
        session.saveSession("sid", "tok", "me")
        enqueue("{}", code = 201)
        enqueue("", code = 204)

        assertTrue(repository.followArtist("a b").isSuccess)
        assertTrue(repository.unfollowArtist("a b").isSuccess)

        val follow = server.takeRequest()
        assertEquals("POST", follow.method)
        assertEquals("/api/v4/user/a%20b/follow/", follow.path)
        assertEquals("tok", follow.getHeader("X-CSRFToken"))
        assertTrue(follow.getHeader("Cookie")!!.contains("sid"))

        val unfollow = server.takeRequest()
        assertEquals("DELETE", unfollow.method)
        assertEquals("/api/v4/user/a%20b/follow/", unfollow.path)
        assertEquals("tok", unfollow.getHeader("X-CSRFToken"))
    }

    @Test
    fun `follow failure is reported and does not log out`() = runBlocking {
        session.saveSession("sid", "tok", "me")
        enqueue("""{"detail":"CSRF Failed"}""", code = 403)
        assertTrue(repository.followArtist("a").isFailure)
        assertTrue(session.isLoggedIn.value)
    }
}
