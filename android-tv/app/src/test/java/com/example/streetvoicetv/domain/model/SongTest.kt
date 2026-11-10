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

    @Test
    fun `formattedDuration shows hours from one hour on`() {
        assertEquals("59:59", song(durationSeconds = 3599.0).formattedDuration)
        assertEquals("1:00:00", song(durationSeconds = 3600.0).formattedDuration)
        assertEquals("1:15:00", song(durationSeconds = 4500.0).formattedDuration)
        assertEquals("10:00:05", song(durationSeconds = 36005.0).formattedDuration)
    }

    @Test
    fun `formattedDuration clamps negative and NaN to zero`() {
        assertEquals("0:00", song(durationSeconds = -5.0).formattedDuration)
        assertEquals("0:00", song(durationSeconds = Double.NaN).formattedDuration)
    }

    @Test
    fun `formattedPlaysCount rolls over to the next unit when rounding reaches 1000`() {
        assertEquals("999.9K", song(playsCount = 999_949).formattedPlaysCount)
        assertEquals("1.0M", song(playsCount = 999_950).formattedPlaysCount)
        assertEquals("1.0M", song(playsCount = 999_999).formattedPlaysCount)
        assertEquals("1.0B", song(playsCount = 999_999_999).formattedPlaysCount)
        assertEquals("2.1B", song(playsCount = Int.MAX_VALUE).formattedPlaysCount)
        assertEquals("0", song(playsCount = -3).formattedPlaysCount)
    }

    @Test
    fun `formatting ignores the default locale`() {
        Locale.setDefault(Locale.GERMANY) // would use "," as decimal separator
        assertEquals("12.3K", song(playsCount = 12_345).formattedPlaysCount)
    }
}
