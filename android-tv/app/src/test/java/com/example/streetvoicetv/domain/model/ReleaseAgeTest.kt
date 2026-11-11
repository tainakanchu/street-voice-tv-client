package com.example.streetvoicetv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseAgeTest {

    private val published = parsePublishAt("2026-11-03T18:18:42+0800")!!

    @Test
    fun `parses the API format and ISO offsets`() {
        assertEquals(1_793_701_122_000L, published)
        assertEquals(published, parsePublishAt("2026-11-03T18:18:42+08:00"))
        assertEquals(published, parsePublishAt("2026-11-03T10:18:42Z"))
        assertNull(parsePublishAt(null))
        assertNull(parsePublishAt("yesterday"))
    }

    @Test
    fun `relative age, then the date`() {
        val minute = 60_000L
        assertEquals("剛剛", formatReleaseAge(published, published + 30_000))
        assertEquals("剛剛", formatReleaseAge(published, published - 5 * minute))
        assertEquals("5 分鐘前", formatReleaseAge(published, published + 5 * minute))
        assertEquals("3 小時前", formatReleaseAge(published, published + 3 * 60 * minute))
        assertEquals("3 天前", formatReleaseAge(published, published + 3 * 24 * 60 * minute))
        assertEquals("2026/11/3", formatReleaseAge(published, published + 40L * 24 * 60 * minute))
    }
}
