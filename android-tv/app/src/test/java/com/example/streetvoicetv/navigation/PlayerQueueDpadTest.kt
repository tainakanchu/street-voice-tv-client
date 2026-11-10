package com.example.streetvoicetv.navigation

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.playback.QueueView
import com.example.streetvoicetv.screenshot.FakeData
import com.example.streetvoicetv.screenshot.QueueFakes
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.screenshot.installFakeImageLoader
import com.example.streetvoicetv.ui.player.LYRICS_PANEL_TAG
import com.example.streetvoicetv.ui.player.PlayerContent
import com.example.streetvoicetv.ui.player.PlayerLoadingState
import com.example.streetvoicetv.ui.player.QUEUE_BUTTON_DESCRIPTION
import com.example.streetvoicetv.ui.player.QUEUE_LIST_TAG
import com.example.streetvoicetv.ui.player.QUEUE_PANEL_TAG
import com.example.streetvoicetv.ui.player.queueRowTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** D-pad behaviour of the player's 佇列 (play queue) panel. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class PlayerQueueDpadTest {

    @get:Rule
    val compose = createTvComposeRule()

    @Before
    fun setUp() {
        installFakeImageLoader()
        emulateTvDevice()
    }

    private val calls = mutableListOf<String>()

    private fun setPlayer(
        queue: QueueView = QueueFakes.normal,
        playback: PlaybackState = FakeData.playbackPlaying,
        isLoggedIn: Boolean = true,
    ) {
        compose.setContent {
            TvChrome {
                // Stands in for the navigation back stack: registered first, so the player's
                // own handler (queue open) takes precedence.
                BackHandler { calls += "navigateBack" }
                PlayerContent(
                    loadingState = PlayerLoadingState(isLoaded = true),
                    playbackState = playback,
                    isLiked = false,
                    isLoggedIn = isLoggedIn,
                    onRetry = {},
                    onBack = { calls += "back" },
                    onArtistSelected = {},
                    onToggleShuffle = { calls += "shuffle" },
                    onSkipPrevious = { calls += "previous" },
                    onTogglePlayPause = { calls += "playPause" },
                    onSkipNext = { calls += "next" },
                    onToggleRepeatMode = { calls += "repeat" },
                    onToggleLike = { calls += "like" },
                    upNext = queue,
                    onPlayQueueItem = { calls += "queue:$it" },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun queueShown() = compose.onAllNodes(hasTestTag(QUEUE_PANEL_TAG)).fetchSemanticsNodes().isNotEmpty()
    private fun lyricsShown() = compose.onAllNodes(hasTestTag(LYRICS_PANEL_TAG)).fetchSemanticsNodes().isNotEmpty()

    /** From the initial focus (play / pause) to the 佇列 button and press it. */
    private fun openQueue() {
        compose.assertFocusedDescription("暫停")
        compose.dpad(Dpad.Right, times = 4)
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
        compose.dpad(Dpad.Center)
        assertTrue("queue panel shown", queueShown())
        assertFalse("lyrics replaced by the queue", lyricsShown())
    }

    @Test
    fun open_focusesNextSong_leftReturnsToQueueButton_rightRestoresRow() {
        setPlayer()
        openQueue()
        // Focus lands on the song after the current one (queue index 3).
        compose.assertFocusedTag(queueRowTag(3))
        compose.assertFocusedFullyVisible()
        compose.captureDpad("player_queue_opened")

        compose.dpad(Dpad.Left)
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
        compose.dpad(Dpad.Right)
        compose.assertFocusedTag(queueRowTag(3))

        compose.dpad(Dpad.Down, Dpad.Down)
        compose.assertFocusedTag(queueRowTag(5))
        compose.dpad(Dpad.Left)
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
        // Back into the list: the row focus left from.
        compose.dpad(Dpad.Right)
        compose.assertFocusedTag(queueRowTag(5))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun left_returnsToTheControlFocusCameFrom() {
        setPlayer()
        openQueue()
        compose.dpad(Dpad.Left, Dpad.Left, Dpad.Left)
        compose.assertFocusedDescription("重複播放")
        // Right through the controls reaches the queue again (via the 佇列 button).
        compose.dpad(Dpad.Right, Dpad.Right, Dpad.Right)
        compose.assertFocusInside(QUEUE_LIST_TAG)
        compose.dpad(Dpad.Left)
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
    }

    @Test
    fun selectingRows_jumpsToThatSong_includingPlayedOnes() {
        setPlayer()
        openQueue()
        compose.dpad(Dpad.Center)
        compose.dpad(Dpad.Up, Dpad.Up, Dpad.Up)
        compose.assertFocusedTag(queueRowTag(0))
        compose.dpad(Dpad.Center)
        assertEquals(listOf("queue:3", "queue:0"), calls)
        // Up at the top of the list: focus stays (no escape to nowhere, no loss).
        compose.dpad(Dpad.Up)
        compose.assertFocusedTag(queueRowTag(0))
        compose.assertFocusedFullyVisible()
    }

    @Test
    fun back_inQueue_returnsToLyricsAndQueueButton_doesNotLeavePlayer() {
        setPlayer()
        openQueue()
        compose.dpad(Dpad.Down)
        compose.dpad(Dpad.Back)
        assertFalse(queueShown())
        assertTrue(lyricsShown())
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
        assertTrue("Back did not leave the player", calls.isEmpty())

        // With the queue closed, Back leaves the player as before.
        compose.dpad(Dpad.Back, allowFocusLoss = true)
        assertEquals(listOf("navigateBack"), calls)
    }

    @Test
    fun back_fromControlsWhileQueueOpen_closesQueue_focusStays() {
        setPlayer()
        openQueue()
        compose.dpad(Dpad.Left, Dpad.Left)
        compose.assertFocusedDescription("喜歡")
        compose.dpad(Dpad.Back)
        assertFalse(queueShown())
        compose.assertFocusedDescription("喜歡")
        assertTrue(calls.isEmpty())
    }

    @Test
    fun queueButton_togglesBackToLyrics() {
        setPlayer()
        openQueue()
        compose.dpad(Dpad.Left)
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
        compose.dpad(Dpad.Center)
        assertFalse(queueShown())
        assertTrue(lyricsShown())
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
        // Right now goes to the lyrics panel again.
        compose.dpad(Dpad.Right)
        compose.assertFocusedTag(LYRICS_PANEL_TAG)
    }

    @Test
    fun lastSong_focusLandsOnCurrentSong() {
        setPlayer(QueueFakes.atEnd, QueueFakes.playbackAtEnd)
        // 下一首 is disabled at the end (but still focusable, like all tv-material buttons).
        openQueue()
        compose.assertFocusedTag(queueRowTag(9))
        compose.dpad(Dpad.Down)
        compose.assertFocusedTag(queueRowTag(9))
    }

    @Test
    fun longQueue_currentSongNearTop_scrollsWithoutLosingFocus() {
        setPlayer(QueueFakes.long, FakeData.playbackLong)
        compose.assertFocusedDescription("播放")
        compose.dpad(Dpad.Right, times = 4)
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
        compose.dpad(Dpad.Center)

        val next = QueueFakes.long.next!!.queueIndex
        compose.assertFocusedTag(queueRowTag(next))
        compose.assertFocusedFullyVisible()
        // The current song is visible near the top of the list.
        val list = compose.boundsOf(hasTestTag(QUEUE_LIST_TAG))
        val current = compose.boundsOf(hasTestTag(queueRowTag(998)))
        assertTrue("current row $current near the top of $list", current.top >= list.top && current.top < list.top + list.height / 3)

        // Long scrolls keep focus inside the list and visible.
        repeat(25) {
            compose.dpad(Dpad.Down)
            compose.assertFocusInside(QUEUE_LIST_TAG)
        }
        compose.assertFocusedFullyVisible()
        compose.dpad(Dpad.Left)
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
        compose.dpad(Dpad.Right)
        compose.assertFocusInside(QUEUE_LIST_TAG)
        compose.assertFocusedFullyVisible()
    }

    @Test
    fun emptyQueue_showsMessage_focusStaysOnControls() {
        setPlayer(QueueView())
        openQueue()
        compose.onAllNodes(hasText("佇列是空的")).fetchSemanticsNodes().let { assertEquals(1, it.size) }
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
        compose.dpad(Dpad.Right)
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
        compose.dpad(Dpad.Back)
        assertTrue(lyricsShown())
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
    }
}
