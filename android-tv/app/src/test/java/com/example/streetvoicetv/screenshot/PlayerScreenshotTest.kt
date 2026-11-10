package com.example.streetvoicetv.screenshot

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.ui.player.PlayerContent
import com.example.streetvoicetv.ui.player.PlayerLoadingState
import com.example.streetvoicetv.ui.player.LYRICS_PANEL_TAG
import com.example.streetvoicetv.ui.player.SEEK_BAR_TAG
import com.example.streetvoicetv.ui.common.UserMessages
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class PlayerScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    private val loaded = PlayerLoadingState(isLoaded = true)

    @Test
    fun player_default() = compose.captureTv("player_default") {
        Player(loaded, FakeData.playbackPlaying, isLoggedIn = true)
    }

    @Test
    fun player_logged_out() = compose.captureTv("player_logged_out") {
        Player(loaded, FakeData.playbackPlaying.copy(isPlaying = false), isLoggedIn = false)
    }

    @Test
    fun player_long_titles() = compose.captureTv("player_long_titles") {
        Player(loaded, FakeData.playbackLong, isLoggedIn = true, isLiked = true)
    }

    @Test
    fun player_no_image_no_lyrics() = compose.captureTv("player_no_image_no_lyrics") {
        Player(
            loaded,
            FakeData.playbackPlaying.copy(
                song = FakeData.playerSong.copy(imageUrl = null, lyrics = null, synopsis = null, albumName = null, likesCount = 0),
                queueSize = 1,
                queueIndex = 0,
            ),
            isLoggedIn = true,
        )
    }

    @Test
    fun player_loading() = compose.captureTv("player_loading") {
        Player(PlayerLoadingState(isLoading = true), PlaybackState(), isLoggedIn = true)
    }

    @Test
    fun player_error() = compose.captureTv("player_error") {
        Player(
            PlayerLoadingState(error = UserMessages.PLAYBACK_FAILED),
            PlaybackState(),
            isLoggedIn = true,
        )
    }

    @Test
    fun player_focused_play_button() = compose.captureTv(
        "player_focused_play_button",
        beforeCapture = { focusOnContentDescription("暫停") },
    ) {
        Player(loaded, FakeData.playbackPlaying, isLoggedIn = true)
    }

    @Test
    fun player_lyrics_focused() = compose.captureTv(
        "player_lyrics_focused",
        beforeCapture = {
            onNodeWithTag(LYRICS_PANEL_TAG).requestFocus()
            // D-pad DOWN scrolls the lyrics while the panel keeps focus.
            onNodeWithTag(LYRICS_PANEL_TAG).performKeyInput { pressKey(Key.DirectionDown) }
            mainClock.advanceTimeBy(1_000)
        },
    ) {
        Player(loaded, FakeData.playbackLong, isLoggedIn = true)
    }

    @Test
    fun player_seek_bar_focused() = compose.captureTv(
        "player_seek_bar_focused",
        beforeCapture = { onNodeWithTag(SEEK_BAR_TAG).requestFocus() },
    ) {
        Player(loaded, FakeData.playbackPlaying, isLoggedIn = true)
    }

    @Composable
    private fun Player(
        loadingState: PlayerLoadingState,
        playbackState: PlaybackState,
        isLoggedIn: Boolean,
        isLiked: Boolean = false,
    ) {
        PlayerContent(
            loadingState = loadingState,
            playbackState = playbackState,
            isLiked = isLiked,
            isLoggedIn = isLoggedIn,
            onRetry = {},
            onBack = {},
            onArtistSelected = {},
            onToggleShuffle = {},
            onSkipPrevious = {},
            onTogglePlayPause = {},
            onSkipNext = {},
            onToggleRepeatMode = {},
            onToggleLike = {},
        )
    }
}
