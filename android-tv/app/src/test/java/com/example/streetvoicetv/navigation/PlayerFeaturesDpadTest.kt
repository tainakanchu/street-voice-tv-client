package com.example.streetvoicetv.navigation

import android.app.Application
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.playback.QueueView
import com.example.streetvoicetv.playback.SleepTimerOption
import com.example.streetvoicetv.playback.SleepTimerState
import com.example.streetvoicetv.screenshot.FakeData
import com.example.streetvoicetv.screenshot.PlayerFeatureFakes
import com.example.streetvoicetv.screenshot.QueueFakes
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.screenshot.installFakeImageLoader
import com.example.streetvoicetv.ui.player.AMBIENT_TAG
import com.example.streetvoicetv.ui.player.AMBIENT_TIMEOUT_MS
import com.example.streetvoicetv.ui.player.LYRICS_FOLLOW_RESUME_MS
import com.example.streetvoicetv.ui.player.PlayerContent
import com.example.streetvoicetv.ui.player.PlayerLoadingState
import com.example.streetvoicetv.ui.player.QUEUE_BUTTON_DESCRIPTION
import com.example.streetvoicetv.ui.player.QUEUE_TOAST_TAG
import com.example.streetvoicetv.ui.player.SLEEP_TIMER_BUTTON_DESCRIPTION
import com.example.streetvoicetv.ui.player.SLEEP_TIMER_MENU_TAG
import com.example.streetvoicetv.ui.player.SLEEP_TIMER_STATUS_TAG
import com.example.streetvoicetv.ui.player.lyricLineTag
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

/** D-pad: sleep timer menu, synced lyrics, ambient mode, removing queue rows. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class PlayerFeaturesDpadTest {

    @get:Rule
    val compose = createTvComposeRule()

    @Before
    fun setUp() {
        installFakeImageLoader()
        emulateTvDevice()
    }

    private val calls = mutableListOf<String>()
    private val sleep = mutableStateOf<SleepTimerState>(SleepTimerState.Off)
    private val position = mutableLongStateOf(0L)

    private fun setPlayer(
        playback: PlaybackState = FakeData.playbackPlaying,
        queue: QueueView = QueueFakes.normal,
        queueOpen: Boolean = false,
    ) {
        position.longValue = playback.positionMs
        compose.setContent {
            TvChrome {
                BackHandler { calls += "navigateBack" }
                // Stands in for PlayerScreen's media key handling.
                Box(
                    Modifier.onKeyEvent {
                        if (it.type == KeyEventType.KeyDown && it.key.nativeKeyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
                            calls += "media:next"
                            true
                        } else {
                            false
                        }
                    },
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
                        onTogglePlayPause = { calls += "playPause" },
                        onSkipNext = { calls += "next" },
                        onToggleRepeatMode = {},
                        onToggleLike = {},
                        positionMs = { position.longValue },
                        upNext = queue,
                        initialQueueOpen = queueOpen,
                        onPlayQueueItem = { calls += "queue:$it" },
                        onRemoveQueueItem = { calls += "remove:$it" },
                        sleepTimer = { sleep.value },
                        onSetSleepTimer = {
                            calls += "sleep:$it"
                            sleep.value = when (it) {
                                SleepTimerOption.OFF -> SleepTimerState.Off
                                SleepTimerOption.END_OF_SONG -> SleepTimerState.EndOfSong
                                else -> SleepTimerState.Countdown(it.minutes!! * 60_000L)
                            }
                        },
                        onSeekTo = { calls += "seek:$it" },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun shown(tag: String) = compose.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    // --- sleep timer ------------------------------------------------------------------------

    private fun toSleepButton() {
        compose.assertFocusedDescription("暫停")
        compose.dpad(Dpad.Right, times = 4)
        compose.assertFocusedDescription(SLEEP_TIMER_BUTTON_DESCRIPTION)
    }

    @Test
    fun sleepTimer_menuOpens_selectStartsTimer_focusReturnsToButton() {
        setPlayer()
        toSleepButton()
        compose.dpad(Dpad.Center)
        assertTrue(shown(SLEEP_TIMER_MENU_TAG))
        compose.assertFocusedText("30 分")
        compose.captureDpad("player_sleep_timer_menu")

        // Focus is trapped in the menu.
        compose.dpad(Dpad.Left, Dpad.Right)
        compose.assertFocusInside(SLEEP_TIMER_MENU_TAG)
        compose.dpad(Dpad.Down)
        compose.assertFocusedText("60 分")
        compose.dpad(Dpad.Center)
        assertEquals(listOf("sleep:MIN_60"), calls)
        assertFalse(shown(SLEEP_TIMER_MENU_TAG))
        compose.assertFocusedDescription(SLEEP_TIMER_BUTTON_DESCRIPTION)
        compose.onAllNodes(hasText("睡眠計時 1:00:00 後暫停")).fetchSemanticsNodes().let { assertEquals(1, it.size) }

        // The remaining time follows the state (ticks).
        sleep.value = SleepTimerState.Countdown(59 * 60_000L + 1_000)
        compose.waitForIdle()
        compose.onAllNodes(hasText("睡眠計時 59:01 後暫停")).fetchSemanticsNodes().let { assertEquals(1, it.size) }

        // Reopen: 關閉 cancels it.
        compose.dpad(Dpad.Center)
        compose.dpad(Dpad.Down, times = 6)
        compose.assertFocusedText("關閉")
        compose.dpad(Dpad.Center)
        assertEquals(SleepTimerState.Off, sleep.value)
        assertFalse(shown(SLEEP_TIMER_STATUS_TAG))
    }

    @Test
    fun sleepTimer_backClosesMenuOnly() {
        setPlayer()
        toSleepButton()
        compose.dpad(Dpad.Center)
        compose.dpad(Dpad.Back)
        assertFalse(shown(SLEEP_TIMER_MENU_TAG))
        compose.assertFocusedDescription(SLEEP_TIMER_BUTTON_DESCRIPTION)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun sleepTimer_endOfSong_showsStatus() {
        setPlayer()
        toSleepButton()
        compose.dpad(Dpad.Center)
        compose.dpad(Dpad.Down, times = 3)
        compose.assertFocusedText("本曲結束")
        compose.dpad(Dpad.Center)
        assertEquals(listOf("sleep:END_OF_SONG"), calls)
        compose.onAllNodes(hasText("睡眠計時：本曲結束後暫停")).fetchSemanticsNodes().let { assertEquals(1, it.size) }
    }

    // --- synced lyrics ----------------------------------------------------------------------

    private fun isCurrentLine(index: Int): Boolean =
        compose.onAllNodes(hasTestTag(lyricLineTag(index)) and SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .fetchSemanticsNodes().isNotEmpty()

    @Test
    fun syncedLyrics_enterLandsOnCurrentLine_okSeeksToFocusedLine() {
        setPlayer(PlayerFeatureFakes.playbackLrc)
        assertTrue(isCurrentLine(7))
        compose.dpad(Dpad.Right, times = 5)
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
        compose.dpad(Dpad.Right)
        compose.assertFocusedTag(lyricLineTag(7))

        compose.dpad(Dpad.Down, Dpad.Down)
        compose.assertFocusedTag(lyricLineTag(9))
        compose.assertFocusedFullyVisible()
        compose.dpad(Dpad.Center)
        assertEquals(listOf("seek:${PlayerFeatureFakes.lineTimeMs(9)}"), calls)

        compose.dpad(Dpad.Up, times = 5)
        compose.assertFocusedTag(lyricLineTag(4))
        compose.dpad(Dpad.Left)
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
    }

    @Test
    fun syncedLyrics_highlightFollowsPosition_focusFollowsAfterIdle() {
        setPlayer(PlayerFeatureFakes.playbackLrc)
        compose.dpad(Dpad.Right, times = 6)
        compose.assertFocusedTag(lyricLineTag(7))
        compose.dpad(Dpad.Up)
        compose.assertFocusedTag(lyricLineTag(6))

        // Song moves on while the user is browsing: highlight moves, focus stays (manual).
        position.longValue = PlayerFeatureFakes.lineTimeMs(8) + 100
        compose.waitForIdle()
        assertTrue(isCurrentLine(8))
        compose.assertFocusedTag(lyricLineTag(6))

        // 5 s after the last key, following resumes and focus goes to the line being sung.
        compose.mainClock.advanceTimeBy(LYRICS_FOLLOW_RESUME_MS + 500)
        compose.waitForIdle()
        compose.assertFocusedTag(lyricLineTag(8))
        position.longValue = PlayerFeatureFakes.lineTimeMs(10) + 100
        compose.waitForIdle()
        compose.assertFocusedTag(lyricLineTag(10))
        compose.assertFocusedFullyVisible()
    }

    // --- ambient ----------------------------------------------------------------------------

    private fun enterAmbient() {
        compose.mainClock.advanceTimeBy(AMBIENT_TIMEOUT_MS + 2_000)
        compose.waitForIdle()
        assertTrue("ambient shown", shown(AMBIENT_TAG))
    }

    @Test
    fun ambient_entersAfterIdle_firstKeyOnlyWakes_focusRestored() {
        setPlayer()
        compose.dpad(Dpad.Right)
        compose.assertFocusedDescription("下一首")
        assertFalse(shown(AMBIENT_TAG))
        enterAmbient()

        compose.dpad(Dpad.Center)
        assertFalse("woken", shown(AMBIENT_TAG))
        assertTrue("the waking key did nothing else", calls.isEmpty())
        compose.assertFocusedDescription("下一首")

        compose.dpad(Dpad.Right)
        compose.assertFocusedDescription("重複播放")
        compose.dpad(Dpad.Center)
        assertTrue(calls.isEmpty())

        // Back also only wakes.
        enterAmbient()
        compose.dpad(Dpad.Back)
        assertFalse(shown(AMBIENT_TAG))
        assertTrue(calls.isEmpty())
        compose.assertFocusedDescription("重複播放")
    }

    @Test
    fun ambient_keysResetTheTimer() {
        setPlayer()
        compose.mainClock.advanceTimeBy(AMBIENT_TIMEOUT_MS - 5_000)
        compose.dpad(Dpad.Right)
        compose.mainClock.advanceTimeBy(10_000)
        compose.waitForIdle()
        assertFalse(shown(AMBIENT_TAG))
    }

    @Test
    fun ambient_mediaKeysActDirectly() {
        setPlayer()
        enterAmbient()
        compose.dpad(Dpad.MediaNext)
        assertEquals(listOf("media:next"), calls)
        assertFalse(shown(AMBIENT_TAG))
        compose.assertFocusedDescription("暫停")
    }

    @Test
    fun ambient_notWhilePaused() {
        setPlayer(FakeData.playbackPlaying.copy(isPlaying = false))
        compose.mainClock.advanceTimeBy(AMBIENT_TIMEOUT_MS * 2)
        compose.waitForIdle()
        assertFalse(shown(AMBIENT_TAG))
    }

    // --- queue row removal ------------------------------------------------------------------

    @Test
    fun queueRow_menuKeyRemoves_focusMovesToNeighbour_messageShown() {
        setPlayer(queueOpen = false)
        compose.dpad(Dpad.Right, times = 5)
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
        compose.dpad(Dpad.Center)
        compose.assertFocusedTag(queueRowTag(3))

        compose.dpad(Dpad.Menu)
        assertEquals(listOf("remove:3"), calls)
        compose.assertFocusedTag(queueRowTag(4))
        assertTrue(shown(QUEUE_TOAST_TAG))
        compose.onAllNodes(hasText("已從佇列移除：${QueueFakes.normal.items[3].song.name}"))
            .fetchSemanticsNodes().let { assertEquals(1, it.size) }

        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        assertFalse(shown(QUEUE_TOAST_TAG))
    }

    @Test
    fun queueRow_menuOnLastRow_movesFocusToTheToggleBelow() {
        setPlayer(QueueFakes.playbackAtEnd, queue = QueueFakes.atEnd)
        compose.dpad(Dpad.Right, times = 5)
        compose.assertFocusedDescription(QUEUE_BUTTON_DESCRIPTION)
        compose.dpad(Dpad.Center)
        compose.assertFocusedTag(queueRowTag(9))
        compose.dpad(Dpad.Menu)
        assertEquals(listOf("remove:9"), calls)
    }
}
