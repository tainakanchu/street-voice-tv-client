package com.example.streetvoicetv.navigation

import android.app.Application
import android.net.Uri
import com.example.streetvoicetv.PLAYER_ROUTE
import com.example.streetvoicetv.Routes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoutesTest {

    @Test
    fun `only a player on top is replaced`() {
        assertTrue(Routes.shouldReplacePlayer(PLAYER_ROUTE))
        // home -> player -> artist: the player below must survive (and the artist screen too).
        assertFalse(Routes.shouldReplacePlayer("artist/{username}"))
        assertFalse(Routes.shouldReplacePlayer("home"))
        assertFalse(Routes.shouldReplacePlayer(null))
    }

    @Test
    fun `string arguments are encoded into a single segment`() {
        for (name in listOf("a/b", "tom & jerry", "#x", "50%", "周杰倫", "a?b=c")) {
            val route = Routes.artist(name)
            val arg = route.removePrefix("artist/")
            assertFalse(route, arg.contains('/') || arg.contains('?') || arg.contains('#'))
            assertEquals(name, Uri.decode(arg))
        }
        assertEquals("player/5", Routes.player(5))
    }
}
