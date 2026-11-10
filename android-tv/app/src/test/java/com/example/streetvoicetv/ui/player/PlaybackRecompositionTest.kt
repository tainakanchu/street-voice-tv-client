package com.example.streetvoicetv.ui.player

import android.app.Application
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.streetvoicetv.playback.ManagerHarness
import com.example.streetvoicetv.playback.testSongs
import com.example.streetvoicetv.screenshot.FakeData
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.screenshot.installFakeImageLoader
import com.example.streetvoicetv.ui.components.NowPlayingBar
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Position updates must only recompose the progress UI, not the song info or the NowPlayingBar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class PlaybackRecompositionTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    @Test
    fun positionChanges_doNotRecomposeSongInfo() {
        val position = mutableLongStateOf(0L)
        var songInfoCompositions = 0
        compose.setContent {
            TvChrome {
                PlayerContent(
                    loadingState = PlayerLoadingState(isLoaded = true),
                    playbackState = FakeData.playbackPlaying,
                    positionMs = { position.longValue },
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
                    songInfoProbe = { songInfoCompositions++ },
                )
            }
        }
        compose.waitForIdle()
        assertEquals(1, songInfoCompositions)

        for (second in 1..10) {
            compose.runOnIdle { position.longValue = second * 1_000L }
            compose.waitForIdle()
        }
        compose.onNodeWithText("0:10").assertExists()
        assertEquals(1, songInfoCompositions)
    }

    @Test
    fun positionChanges_doNotRecomposeNowPlayingBar() {
        val h = ManagerHarness(Dispatchers.Main.immediate)
        var barCompositions = 0
        compose.runOnIdle {
            h.manager.playQueue(testSongs(2), 0)
            h.engine.becomeReady()
        }
        compose.setContent {
            TvChrome {
                val nowPlaying by h.manager.nowPlaying.collectAsState()
                val song = nowPlaying.song ?: return@TvChrome
                SideEffect { barCompositions++ }
                NowPlayingBar(
                    song = song,
                    isPlaying = nowPlaying.isPlaying,
                    error = nowPlaying.error,
                    onTap = {},
                    onTogglePlayPause = {},
                )
            }
        }
        compose.waitForIdle()
        assertEquals(1, barCompositions)

        for (step in 1..10) {
            compose.runOnIdle { h.manager.seekBy(1_000) }
            compose.waitForIdle()
        }
        assertEquals(10_000L, h.manager.positionMs.value)
        assertEquals(1, barCompositions)
    }
}
