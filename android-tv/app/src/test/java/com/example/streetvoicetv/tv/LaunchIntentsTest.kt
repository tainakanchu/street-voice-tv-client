package com.example.streetvoicetv.tv

import android.app.Application
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import com.example.streetvoicetv.Routes
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class LaunchIntentsTest {

    @Test
    fun `search intents carry the trimmed query`() {
        val search = Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "  告五人 ")
        assertEquals(LaunchAction.Search("告五人"), LaunchIntents.parse(search))

        val assistant = Intent(LaunchIntents.ACTION_GMS_SEARCH).putExtra(SearchManager.QUERY, "deca joins")
        assertEquals(LaunchAction.Search("deca joins"), LaunchIntents.parse(assistant))
    }

    @Test
    fun `blank or missing queries are ignored and long ones cut`() {
        assertNull(LaunchIntents.parse(Intent(Intent.ACTION_SEARCH)))
        assertNull(LaunchIntents.parse(Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, "   ")))
        val long = "a".repeat(500)
        val parsed = LaunchIntents.parse(Intent(Intent.ACTION_SEARCH).putExtra(SearchManager.QUERY, long))
        assertEquals(LaunchIntents.MAX_QUERY_LENGTH, (parsed as LaunchAction.Search).query.length)
    }

    @Test
    fun `resume deep link`() {
        assertEquals(
            LaunchAction.ResumeLastSession,
            LaunchIntents.parse(Intent(Intent.ACTION_VIEW, Uri.parse("streetvoicetv://resume"))),
        )
        assertEquals(
            LaunchAction.ResumeLastSession,
            LaunchIntents.parse(Intent(Intent.ACTION_VIEW, Uri.parse("StreetVoiceTV://Resume?from=watchnext"))),
        )
    }

    @Test
    fun `other intents are no launch action`() {
        assertNull(LaunchIntents.parse(null))
        assertNull(LaunchIntents.parse(Intent(Intent.ACTION_MAIN)))
        assertNull(LaunchIntents.parse(Intent(Intent.ACTION_VIEW, Uri.parse("streetvoicetv://other"))))
        assertNull(LaunchIntents.parse(Intent(Intent.ACTION_VIEW, Uri.parse("https://streetvoice.com/resume"))))
        assertNull(LaunchIntents.parse(Intent(Intent.ACTION_VIEW)))
    }

    @Test
    fun `search route encodes the query as one argument`() {
        assertEquals("search?q=a%2Fb%20%23x%2050%25", Routes.search("a/b #x 50%"))
        assertEquals("search?q=%E5%91%8A%E4%BA%94%E4%BA%BA", Routes.search("告五人"))
        assertEquals("告五人", Uri.parse("app://x/" + Routes.search("告五人")).getQueryParameter("q"))
    }
}
