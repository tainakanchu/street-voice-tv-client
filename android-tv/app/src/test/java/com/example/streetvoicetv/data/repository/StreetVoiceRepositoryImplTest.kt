package com.example.streetvoicetv.data.repository

import com.example.streetvoicetv.data.api.ApiConfig
import com.example.streetvoicetv.data.api.StreetVoiceApi
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Pagination mapping of list responses (`{count, offset, limit, next, previous, results}`). */
class StreetVoiceRepositoryImplTest {

    private val server = MockWebServer()
    private lateinit var repository: StreetVoiceRepositoryImpl

    @Before
    fun setUp() {
        server.start()
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
        val api = StreetVoiceApi(OkHttpClient(), json, ApiConfig(server.url("").toString().trimEnd('/')))
        repository = StreetVoiceRepositoryImpl(api)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun enqueue(body: String) = server.enqueue(MockResponse().setBody(body))

    private fun song(id: Int) =
        """{"id":$id,"name":"song $id","image":null,"length":120.0,"plays_count":1,"user":{"username":"u","nickname":"U"}}"""

    private fun nestedSong(id: Int) =
        """{"id":$id,"name":"song $id","length":120.0,"plays_count":1,"user":{"username":"u","profile":{"nickname":"U"}}}"""

    private fun base() = server.url("").toString().trimEnd('/')

    @Test
    fun `middle page - next offset read from the next url`() = runBlocking {
        enqueue(
            """{"count":166,"offset":20,"limit":20,
               "next":"${base()}/api/v4/user/a/songs/?limit=20&offset=40",
               "previous":"${base()}/api/v4/user/a/songs/?limit=20",
               "results":[${song(1)},${song(2)}]}""",
        )
        val page = repository.getArtistSongs("a", limit = 20, offset = 20).getOrThrow()

        assertEquals(listOf(1, 2), page.items.map { it.id })
        assertEquals(166, page.total)
        assertEquals(40, page.nextOffset)
        val request = server.takeRequest()
        assertEquals("/api/v4/user/a/songs/?limit=20&offset=20", request.path)
    }

    @Test
    fun `last page - no next means end`() = runBlocking {
        enqueue(
            """{"count":63,"offset":50,"limit":20,"next":null,
               "previous":"${base()}/api/v4/playlist/9/songs/?limit=20&offset=30",
               "results":[{"id":5,"song":${nestedSong(5)}}]}""",
        )
        val page = repository.getPlaylistSongs(9, limit = 20, offset = 50).getOrThrow()

        assertEquals(listOf(5), page.items.map { it.id })
        assertEquals(63, page.total)
        assertNull(page.nextOffset)
        assertEquals("/api/v4/playlist/9/songs/?limit=20&offset=50", server.takeRequest().path)
    }

    @Test
    fun `liked songs map content_object and paginate`() = runBlocking {
        enqueue(
            """{"count":6,"offset":0,"limit":3,
               "next":"${base()}/api/v4/user/me/likes/?limit=3&offset=3","previous":null,
               "results":[{"id":100,"content_object":${nestedSong(7)}}]}""",
        )
        val page = repository.getLikedSongs("me", limit = 3, offset = 0).getOrThrow()
        assertEquals(listOf(7), page.items.map { it.id })
        assertEquals(3, page.nextOffset)
    }

    @Test
    fun `next url without offset falls back to offset plus page size`() = runBlocking {
        enqueue(
            """{"count":9,"offset":0,"limit":2,"next":"${base()}/api/v4/album/1/songs/?limit=2",
               "previous":null,"results":[${song(1)},${song(2)}]}""",
        )
        val page = repository.getAlbumSongs(1, limit = 2, offset = 0).getOrThrow()
        assertEquals(2, page.nextOffset)
    }

    @Test
    fun `empty page ends the list even if next is set`() = runBlocking {
        enqueue(
            """{"count":100,"offset":100,"limit":10,"next":"${base()}/api/v4/search/?limit=10&offset=110",
               "previous":null,"results":[]}""",
        )
        val page = repository.searchSongs("x", limit = 10, offset = 100).getOrThrow()
        assertTrue(page.items.isEmpty())
        assertNull(page.nextOffset)
    }

    @Test
    fun `search query is url-encoded`() = runBlocking {
        enqueue("""{"count":0,"offset":0,"limit":20,"next":null,"previous":null,"results":[]}""")
        repository.searchSongs("周杰倫 & friends #1", limit = 20, offset = 40).getOrThrow()

        val url = server.takeRequest().requestUrl!!
        assertEquals("周杰倫 & friends #1", url.queryParameter("q"))
        assertEquals("song", url.queryParameter("type"))
        assertEquals("20", url.queryParameter("limit"))
        assertEquals("40", url.queryParameter("offset"))
    }

    @Test
    fun `http error is a failure`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        assertTrue(repository.getArtistAlbums("a", limit = 20, offset = 0).isFailure)
    }

    @Test
    fun `next offset helper ignores offsets that do not advance`() {
        assertEquals(30, nextOffsetOf(offset = 20, pageSize = 10, next = "https://x/?offset=20"))
        assertEquals(40, nextOffsetOf(offset = 20, pageSize = 10, next = "https://x/?offset=40"))
        assertNull(nextOffsetOf(offset = 20, pageSize = 10, next = null))
        assertNull(nextOffsetOf(offset = 20, pageSize = 0, next = "https://x/?offset=30"))
    }
}
