package com.example.streetvoicetv.data.repository

import com.example.streetvoicetv.data.model.SongDetailResponse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryMappingTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test
    fun `v5 flat song user maps nickname and profile image`() {
        val song = json.decodeFromString<SongDetailResponse>(
            """{"id":1,"name":"n","user":{"id":7,"username":"u","nickname":"暱稱","profile_image":"https://img/p.jpg"}}""",
        ).toDomain()
        assertEquals("暱稱", song.artistName)
        assertEquals("https://img/p.jpg", song.artistProfileImageUrl)
    }

    @Test
    fun `nested profile shape is still supported and flat fields win`() {
        val nested = json.decodeFromString<SongDetailResponse>(
            """{"id":1,"name":"n","user":{"username":"u","profile":{"nickname":"舊","image":"old.jpg"}}}""",
        ).toDomain()
        assertEquals("舊", nested.artistName)
        assertEquals("old.jpg", nested.artistProfileImageUrl)

        val both = json.decodeFromString<SongDetailResponse>(
            """{"id":1,"name":"n","user":{"username":"u","nickname":"","profile_image":"new.jpg","profile":{"nickname":"舊"}}}""",
        ).toDomain()
        assertEquals("舊", both.artistName) // blank flat nickname falls back
        assertEquals("new.jpg", both.artistProfileImageUrl)

        val bare = json.decodeFromString<SongDetailResponse>("""{"id":1,"name":"n","user":{"username":"u"}}""").toDomain()
        assertEquals("u", bare.artistName)
    }

    @Test
    fun `runCatchingCancellable rethrows cancellation but wraps other errors`() {
        assertTrue(runCatchingCancellable { error("boom") }.isFailure)
        assertEquals(3, runCatchingCancellable { 3 }.getOrNull())
        try {
            runCatchingCancellable { throw CancellationException("cancelled") }
            throw AssertionError("expected CancellationException")
        } catch (_: CancellationException) {
        }
    }
}
