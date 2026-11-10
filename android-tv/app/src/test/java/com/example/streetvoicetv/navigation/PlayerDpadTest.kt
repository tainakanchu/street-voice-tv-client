package com.example.streetvoicetv.navigation

import android.app.Application
import androidx.compose.foundation.ScrollState
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.screenshot.FakeData
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.screenshot.installFakeImageLoader
import com.example.streetvoicetv.ui.common.UserMessages
import com.example.streetvoicetv.ui.player.LYRICS_PANEL_TAG
import com.example.streetvoicetv.ui.player.PlayerContent
import com.example.streetvoicetv.ui.player.PlayerLoadingState
import com.example.streetvoicetv.ui.player.SEEK_BAR_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class PlayerDpadTest {

    @get:Rule
    val compose = createTvComposeRule()

    @Before
    fun setUp() {
        installFakeImageLoader()
        emulateTvDevice()
    }

    private val calls = mutableListOf<String>()
    private val seeks = mutableListOf<Long>()
    private val lyricsScroll = ScrollState(0)

    private fun setPlayer(
        playback: PlaybackState = FakeData.playbackPlaying,
        loading: PlayerLoadingState = PlayerLoadingState(isLoaded = true),
        isLoggedIn: Boolean = true,
    ) {
        compose.setContent {
            TvChrome {
                PlayerContent(
                    loadingState = loading,
                    playbackState = playback,
                    isLiked = false,
                    isLoggedIn = isLoggedIn,
                    onRetry = { calls += "retry" },
                    onBack = { calls += "back" },
                    onArtistSelected = { calls += "artist:$it" },
                    onToggleShuffle = { calls += "shuffle" },
                    onSkipPrevious = { calls += "previous" },
                    onTogglePlayPause = { calls += "playPause" },
                    onSkipNext = { calls += "next" },
                    onToggleRepeatMode = { calls += "repeat" },
                    onToggleLike = { calls += "like" },
                    onSeek = { seeks += it },
                    lyricsScrollState = lyricsScroll,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun controls_leftRightAcrossAllControls_withoutEscaping() {
        setPlayer()
        compose.assertFocusedDescription("暫停")

        compose.dpad(Dpad.Left)
        compose.assertFocusedDescription("上一首")
        compose.dpad(Dpad.Left)
        compose.assertFocusedDescription("隨機播放")
        // Left edge: stays on the first control.
        compose.dpad(Dpad.Left)
        compose.assertFocusedDescription("隨機播放")

        for (desc in listOf("上一首", "暫停", "下一首", "重複播放", "喜歡")) {
            compose.dpad(Dpad.Right)
            compose.assertFocusedDescription(desc)
        }
        compose.dpad(Dpad.Center)
        assertEquals(listOf("like"), calls)
    }

    @Test
    fun seekBar_upFromControls_leftRightSeek_downReturnsToSameControl() {
        setPlayer()
        compose.dpad(Dpad.Right)
        compose.assertFocusedDescription("下一首")

        compose.dpad(Dpad.Up)
        compose.assertFocusedTag(SEEK_BAR_TAG)
        compose.captureDpad("player_seek_bar")

        val pos = FakeData.playbackPlaying.positionMs
        compose.dpad(Dpad.Left)
        compose.assertFocusedTag(SEEK_BAR_TAG)
        compose.dpad(Dpad.Right)
        compose.assertFocusedTag(SEEK_BAR_TAG)
        assertEquals(listOf(pos - 10_000, pos + 10_000), seeks)

        // Down returns to the control we came from, not the one under the bar's centre.
        compose.dpad(Dpad.Down)
        compose.assertFocusedDescription("下一首")

        // Up twice: seek bar, then the artist name (opens the artist page).
        compose.dpad(Dpad.Up, Dpad.Up)
        compose.assertFocusedText(FakeData.playerSong.artistName)
        compose.dpad(Dpad.Center)
        assertEquals("artist:${FakeData.playerSong.artistUsername}", calls.last())
        compose.dpad(Dpad.Down)
        compose.assertFocusedTag(SEEK_BAR_TAG)
    }

    @Test
    fun lyrics_rightFromControls_downScrolls_thenFocusCanLeave() {
        setPlayer(FakeData.playbackLong)
        compose.assertFocusedDescription("播放")

        compose.dpad(Dpad.Right, times = 3)
        compose.assertFocusedDescription("喜歡")
        compose.dpad(Dpad.Right)
        compose.assertFocusedTag(LYRICS_PANEL_TAG)

        // Down scrolls the lyrics while the panel keeps focus ...
        var presses = 0
        while (lyricsScroll.value < lyricsScroll.maxValue && presses < 100) {
            val before = lyricsScroll.value
            compose.dpad(Dpad.Down)
            compose.assertFocusedTag(LYRICS_PANEL_TAG)
            assertTrue("Down scrolls the lyrics", lyricsScroll.value > before)
            presses++
            if (presses == 3) compose.captureDpad("player_lyrics_scrolled")
        }
        assertEquals(lyricsScroll.maxValue, lyricsScroll.value)

        // ... at the end, Down is no longer consumed by the panel but there is nothing below:
        // focus stays (no trap, no loss), and Left leaves to the controls.
        compose.dpad(Dpad.Down)
        compose.assertFocusedTag(LYRICS_PANEL_TAG)
        compose.dpad(Dpad.Left)
        compose.assertFocusedDescription("喜歡")

        // Up scrolls back from the bottom.
        compose.dpad(Dpad.Right, Dpad.Up)
        compose.assertFocusedTag(LYRICS_PANEL_TAG)
        assertTrue(lyricsScroll.value < lyricsScroll.maxValue)
    }

    @Test
    fun noLyrics_rightEdgeStaysOnControls() {
        setPlayer(
            FakeData.playbackPlaying.copy(
                song = FakeData.playerSong.copy(lyrics = null, synopsis = null),
            ),
            isLoggedIn = false,
        )
        compose.dpad(Dpad.Right, times = 4)
        compose.assertFocusedDescription("重複播放")
    }

    @Test
    fun error_retryFocused_centerRetries_backButtonReachable() {
        setPlayer(PlaybackState(), PlayerLoadingState(error = UserMessages.PLAYBACK_FAILED))
        compose.assertFocusedText("重試")
        compose.dpad(Dpad.Center)
        assertEquals(listOf("retry"), calls)
        compose.dpad(Dpad.Right)
        compose.assertFocusedText("返回")
        compose.dpad(Dpad.Right, Dpad.Up, Dpad.Down)
        compose.assertFocusedText("返回")
        compose.dpad(Dpad.Center)
        assertEquals(listOf("retry", "back"), calls)
    }
}
