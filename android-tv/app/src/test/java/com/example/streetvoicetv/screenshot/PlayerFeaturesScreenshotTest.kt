package com.example.streetvoicetv.screenshot

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.playback.QueueView
import com.example.streetvoicetv.playback.SleepTimerState
import com.example.streetvoicetv.ui.player.PlayerContent
import com.example.streetvoicetv.ui.player.PlayerLoadingState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Synced lyrics, ambient mode, sleep timer and the autoplay part of the queue. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class PlayerFeaturesScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    /** Line 8 of the LRC highlighted, kept at ~1/3 of the panel height. */
    @Test
    fun player_synced_lyrics() = compose.captureTv(
        "player_synced_lyrics",
        beforeCapture = { mainClock.advanceTimeBy(1_000) },
    ) {
        Player(PlayerFeatureFakes.playbackLrc)
    }

    @Test
    fun player_ambient() = compose.captureTv("player_ambient", beforeCapture = { mainClock.advanceTimeBy(2_000) }) {
        Player(PlayerFeatureFakes.playbackLrc, upNext = QueueFakes.normal, ambient = true)
    }

    @Test
    fun player_ambient_long_titles() = compose.captureTv(
        "player_ambient_long_titles",
        beforeCapture = { mainClock.advanceTimeBy(2_000) },
    ) {
        Player(PlayerFeatureFakes.playbackLongLrc, upNext = QueueFakes.long, ambient = true)
    }

    @Test
    fun player_sleep_timer_menu() = compose.captureTv("player_sleep_timer_menu") {
        Player(FakeData.playbackPlaying, sleep = SleepTimerState.Countdown(29 * 60_000L + 41_000), sleepMenuOpen = true)
    }

    @Test
    fun player_sleep_timer_remaining() = compose.captureTv("player_sleep_timer_remaining") {
        Player(FakeData.playbackPlaying, upNext = QueueFakes.normal, sleep = SleepTimerState.Countdown(29 * 60_000L + 41_000))
    }

    @Test
    fun player_queue_autoplay() = compose.captureTv("player_queue_autoplay") {
        Player(FakeData.playbackPlaying, upNext = PlayerFeatureFakes.autoplayQueue, queueOpen = true)
    }

    @Composable
    private fun Player(
        playback: PlaybackState,
        upNext: QueueView = QueueView(),
        queueOpen: Boolean = false,
        ambient: Boolean = false,
        sleep: SleepTimerState = SleepTimerState.Off,
        sleepMenuOpen: Boolean = false,
    ) {
        PlayerContent(
            loadingState = PlayerLoadingState(isLoaded = true),
            playbackState = playback,
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
            upNext = upNext,
            initialQueueOpen = queueOpen,
            initialAmbient = ambient,
            sleepTimer = { sleep },
            initialSleepMenuOpen = sleepMenuOpen,
        )
    }
}
