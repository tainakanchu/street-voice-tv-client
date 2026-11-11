package com.example.streetvoicetv.ui.player

import android.app.Application
import com.example.streetvoicetv.playback.testSong
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class SongShareTest {

    @Test
    fun `web url is the canonical song page`() {
        val song = testSong(885976).copy(artistUsername = "thezofficial")
        assertEquals("https://streetvoice.com/thezofficial/songs/885976/", SongLinks.webUrl(song))
    }

    @Test
    fun `username is one encoded path segment`() {
        assertEquals("https://streetvoice.com/a%2Fb%20c/songs/1/", SongLinks.webUrl("a/b c", 1))
        assertEquals("https://streetvoice.com/%E8%A1%97%E8%81%B2/songs/2/", SongLinks.webUrl("街聲", 2))
    }

    @Test
    fun `qr matrix is square, non-empty and decodes back to the url`() {
        val url = SongLinks.webUrl("thezofficial", 885976)
        val matrix = QrCodes.encode(url)
        assertEquals(matrix.width, matrix.height)
        assertTrue("at least a version 1 symbol + quiet zone", matrix.width >= 21 + 4)
        var dark = 0
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) if (matrix[x, y]) dark++
        assertTrue(dark > 0)

        // Scale up and decode with zxing's own reader.
        val scale = 4
        val size = matrix.width * scale
        val pixels = IntArray(size * size) { i ->
            if (matrix[(i % size) / scale, (i / size) / scale]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels)))
        assertEquals(url, QRCodeReader().decode(bitmap).text)
    }

    @Test
    fun `image bitmap has one pixel per module`() {
        val matrix = QrCodes.encode("https://streetvoice.com/a/songs/1/")
        val image = QrCodes.toImageBitmap(matrix)
        assertEquals(matrix.width, image.width)
        assertEquals(matrix.height, image.height)
    }
}
