package com.example.streetvoicetv.navigation

import android.app.Application
import androidx.compose.ui.test.hasTestTag
import com.example.streetvoicetv.screenshot.FakeData
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.screenshot.installFakeImageLoader
import com.example.streetvoicetv.ui.player.PlayerContent
import com.example.streetvoicetv.ui.player.PlayerLoadingState
import com.example.streetvoicetv.ui.player.SEEK_BAR_TAG
import com.example.streetvoicetv.ui.player.SHARE_BUTTON_LABEL
import com.example.streetvoicetv.ui.player.SHARE_DIALOG_TAG
import com.example.streetvoicetv.ui.player.SongLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The 分享 chip next to the song title and its QR code dialog. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class PlayerShareDpadTest {

    @get:Rule
    val compose = createTvComposeRule()

    @Before
    fun setUp() {
        installFakeImageLoader()
        emulateTvDevice()
    }

    private val calls = mutableListOf<String>()

    private fun setPlayer() {
        compose.setContent {
            TvChrome {
                PlayerContent(
                    loadingState = PlayerLoadingState(isLoaded = true),
                    playbackState = FakeData.playbackPlaying,
                    isLiked = false,
                    isLoggedIn = true,
                    onRetry = {},
                    onBack = { calls += "back" },
                    onArtistSelected = { calls += "artist:$it" },
                    onToggleShuffle = {},
                    onSkipPrevious = {},
                    onTogglePlayPause = { calls += "playPause" },
                    onSkipNext = {},
                    onToggleRepeatMode = {},
                    onToggleLike = {},
                )
            }
        }
        compose.waitForIdle()
    }

    private fun dialogShown() =
        compose.onAllNodes(hasTestTag(SHARE_DIALOG_TAG)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun share_isAboveTheArtist_opensDialog_backClosesAndRefocuses() {
        setPlayer()
        compose.assertFocusedDescription("暫停")
        // Up: seek bar, artist (as before), then the 分享 chip next to the title.
        compose.dpad(Dpad.Up)
        compose.assertFocusedTag(SEEK_BAR_TAG)
        compose.dpad(Dpad.Up)
        compose.assertFocusedText(FakeData.playerSong.artistName)
        compose.dpad(Dpad.Up)
        compose.assertFocusedText(SHARE_BUTTON_LABEL)

        compose.dpad(Dpad.Center)
        assertTrue(dialogShown())
        compose.assertFocusedText("關閉")
        compose.onNode(hasTestTag(SHARE_DIALOG_TAG)).assertExists()
        assertTrue(
            "the URL is shown",
            compose.onAllNodes(androidx.compose.ui.test.hasText(SongLinks.webUrl(FakeData.playerSong)))
                .fetchSemanticsNodes().isNotEmpty(),
        )

        // Focus is trapped in the dialog.
        compose.dpad(Dpad.Up, Dpad.Left, Dpad.Down, Dpad.Right)
        compose.assertFocusedText("關閉")
        assertEquals("nothing behind the dialog was clicked", emptyList<String>(), calls)

        // Back closes it (does not leave the player) and focus returns to 分享.
        compose.dpad(Dpad.Back)
        assertTrue(!dialogShown())
        assertTrue(calls.none { it == "back" })
        compose.assertFocusedText(SHARE_BUTTON_LABEL)
    }

    @Test
    fun share_closeButtonClosesDialog() {
        setPlayer()
        compose.dpad(Dpad.Up, Dpad.Up, Dpad.Up, Dpad.Center)
        assertTrue(dialogShown())
        compose.dpad(Dpad.Center)
        assertTrue(!dialogShown())
        compose.assertFocusedText(SHARE_BUTTON_LABEL)
        // Down goes back to the artist / controls as usual.
        compose.dpad(Dpad.Down)
        compose.assertFocusedText(FakeData.playerSong.artistName)
    }
}
