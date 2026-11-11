package com.example.streetvoicetv.screenshot

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.playback.QueueView
import com.example.streetvoicetv.ui.player.PlayerContent
import com.example.streetvoicetv.ui.player.PlayerLoadingState
import com.example.streetvoicetv.ui.player.queueRowTag
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The player's 佇列 (play queue) panel and the "下一首" hint. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class PlayerQueueScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    @Test
    fun player_queue_default() = compose.captureTv("player_queue_default") {
        Player(FakeData.playbackPlaying, QueueFakes.normal, queueOpen = true)
    }

    @Test
    fun player_queue_long_titles() = compose.captureTv("player_queue_long_titles") {
        Player(FakeData.playbackLong, QueueFakes.long, queueOpen = true)
    }

    @Test
    fun player_queue_shuffle() = compose.captureTv("player_queue_shuffle") {
        Player(QueueFakes.playbackShuffled, QueueFakes.shuffled, queueOpen = true)
    }

    @Test
    fun player_queue_focused_row() = compose.captureTv(
        "player_queue_focused_row",
        beforeCapture = { focusRowThenPress(Key.DirectionDown) },
    ) {
        Player(FakeData.playbackPlaying, QueueFakes.normal, queueOpen = true)
    }

    @Test
    fun player_queue_focused_current_row() = compose.captureTv(
        "player_queue_focused_current_row",
        beforeCapture = { focusRowThenPress(Key.DirectionUp) },
    ) {
        Player(FakeData.playbackPlaying, QueueFakes.normal, queueOpen = true)
    }

    @Test
    fun player_queue_empty() = compose.captureTv("player_queue_empty") {
        Player(FakeData.playbackPlaying.copy(queueSize = 1, queueIndex = 0), QueueView(), queueOpen = true)
    }

    /** Lyrics shown; the line under the controls reads "3 / 10 · 下一首：…". */
    @Test
    fun player_next_song_hint() = compose.captureTv("player_next_song_hint") {
        Player(FakeData.playbackPlaying, QueueFakes.normal, queueOpen = false)
    }

    /** Very long next title: ellipsized on one line. */
    @Test
    fun player_next_song_hint_long() = compose.captureTv("player_next_song_hint_long") {
        Player(FakeData.playbackLong, QueueFakes.long, queueOpen = false)
    }

    /**
     * Focuses the queue list (it lands on the next song, row 3 — the list's focusRestorer
     * redirects programmatic requests there) and moves with [key] like a remote would.
     */
    private fun ComposeContentTestRule.focusRowThenPress(key: Key) {
        onNodeWithTag(queueRowTag(3)).requestFocus()
        waitForIdle()
        onNodeWithTag(queueRowTag(3)).performKeyInput { pressKey(key) }
        mainClock.advanceTimeBy(1_000)
    }

    @Composable
    private fun Player(playbackState: PlaybackState, queue: QueueView, queueOpen: Boolean) {
        PlayerContent(
            loadingState = PlayerLoadingState(isLoaded = true),
            playbackState = playbackState,
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
            upNext = queue,
            initialQueueOpen = queueOpen,
        )
    }
}
