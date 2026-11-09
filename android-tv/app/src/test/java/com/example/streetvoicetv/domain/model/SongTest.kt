package com.example.streetvoicetv.domain.model

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class SongTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        // formattedPlaysCount uses String.format with the default locale
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() = Locale.setDefault(originalLocale)

    private fun song(durationSeconds: Double = 0.0, playsCount: Int = 0) = Song(
        id = 1,
        name = "n",
        artistName = "a",
        artistUsername = "a",
        imageUrl = null,
        durationSeconds = durationSeconds,
        playsCount = playsCount,
    )

    @Test
    fun `formattedDuration truncates fractional seconds`() {
        assertEquals("3:05", song(durationSeconds = 185.9).formattedDuration)
        assertEquals("0:00", song(durationSeconds = 0.0).formattedDuration)
    }

    @Test
    fun `formattedPlaysCount abbreviates thousands and millions`() {
        assertEquals("999", song(playsCount = 999).formattedPlaysCount)
        assertEquals("1.0K", song(playsCount = 1_000).formattedPlaysCount)
        assertEquals("12.3K", song(playsCount = 12_345).formattedPlaysCount)
        assertEquals("98.8M", song(playsCount = 98_765_432).formattedPlaysCount)
    }
}
