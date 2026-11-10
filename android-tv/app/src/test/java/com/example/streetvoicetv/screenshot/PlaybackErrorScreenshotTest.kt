package com.example.streetvoicetv.screenshot

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.streetvoicetv.ui.common.UserMessages
import com.example.streetvoicetv.ui.components.NowPlayingBar
import com.example.streetvoicetv.ui.player.PlayerContent
import com.example.streetvoicetv.ui.player.PlayerLoadingState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Playback error / loading indications (player banner + compact NowPlayingBar state). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class PlaybackErrorScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    private val failed = FakeData.playbackPlaying.copy(isPlaying = false, error = UserMessages.NETWORK)

    @Test
    fun player_playback_error() = compose.captureTv("player_playback_error") {
        Player(failed)
    }

    @Test
    fun player_playback_loading() = compose.captureTv("player_playback_loading") {
        Player(FakeData.playbackPlaying.copy(isLoading = true, positionMs = 0, durationMs = 0))
    }

    @Test
    fun components_now_playing_bar_error() = compose.captureTv("components_now_playing_bar_error") {
        Column(Modifier.padding(48.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NowPlayingBar(playbackState = failed, onTap = {}, onTogglePlayPause = {})
            Box(Modifier.width(320.dp)) {
                NowPlayingBar(
                    playbackState = FakeData.playbackLong.copy(error = UserMessages.PLAYBACK_FAILED),
                    onTap = {},
                    onTogglePlayPause = {},
                )
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Player(state: com.example.streetvoicetv.playback.PlaybackState) {
        PlayerContent(
            loadingState = PlayerLoadingState(isLoaded = true),
            playbackState = state,
            isLiked = false,
            isLoggedIn = true,
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
