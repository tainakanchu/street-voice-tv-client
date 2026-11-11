package com.example.streetvoicetv.navigation

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.screenshot.FakeData
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.screenshot.installFakeImageLoader
import com.example.streetvoicetv.ui.home.CONTINUE_CARD_TAG
import com.example.streetvoicetv.ui.home.ContinueListening
import com.example.streetvoicetv.ui.home.FollowedNewSongs
import com.example.streetvoicetv.ui.home.HomeContent
import com.example.streetvoicetv.ui.home.HomeUiState
import com.example.streetvoicetv.ui.home.RecentSong
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Home's 「繼續播放」 card and 「最近播放」 row. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class HomeContinueDpadTest {

    @get:Rule
    val compose = createTvComposeRule()

    @Before
    fun setUp() {
        installFakeImageLoader()
        emulateTvDevice()
    }

    private val songs = FakeData.songs
    private val recent = FakeData.songs.take(5).map { it.copy(id = it.id + 700) }
    private val followed = FakeData.songs.take(4).map { it.copy(id = it.id + 500) }
    private val continueListening = ContinueListening(song = songs[3], positionMs = 60_000, queueSize = 12)
    private val full = HomeUiState(
        chartSongs = songs,
        playlists = FakeData.playlists,
        continueListening = continueListening,
        recentlyPlayed = recent.mapIndexed { i, song -> RecentSong(song, "${(i + 1) * 5} 分鐘前") },
        followedNew = FollowedNewSongs("me", followed.map { RecentSong(it, "1 天前") }),
    )

    private var homeState by mutableStateOf(HomeUiState())
    private var resumes = 0
    private val played = mutableListOf<Pair<Song, List<Song>>>()

    private fun setHome(initial: HomeUiState) {
        homeState = initial
        compose.setContent {
            TvChrome {
                HomeContent(
                    uiState = homeState,
                    isLoggedIn = true,
                    profileImageUrl = null,
                    onSongSelected = { song, queue -> played += song to queue },
                    onPlaylistSelected = {},
                    onSearchTap = {},
                    onLoginTap = {},
                    onMyPageTap = {},
                    onContinueListening = { resumes++ },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun continueCard_takesInitialFocus_okResumes_rowsFollowInOrder() {
        setHome(full)
        compose.assertFocusedTag(CONTINUE_CARD_TAG)
        compose.assertFocusedFullyVisible()
        compose.captureDpad("home_continue_focused")
        compose.dpad(Dpad.Center)
        assertEquals(1, resumes)

        // Up: the top bar; Down: 追蹤音樂人的新歌, then 最近播放, then the chart.
        compose.dpad(Dpad.Up)
        compose.assertFocused(HomeSearchBar)
        compose.dpad(Dpad.Down)
        compose.assertFocusedTag(CONTINUE_CARD_TAG)
        compose.dpad(Dpad.Down)
        compose.assertFocused(homeCard(followed[0].name, "followed_new"))
        compose.dpad(Dpad.Down)
        compose.assertFocused(homeCard(recent[0].name, "recent"))
        compose.assertFocusedFullyVisible()

        // A recently played song plays with the row as its queue, starting at it.
        compose.dpad(Dpad.Right, Dpad.Right, Dpad.Center)
        assertEquals(listOf(recent[2] to recent), played)

        // (Down enters the chart below the focused card.)
        compose.dpad(Dpad.Down)
        compose.assertFocused(homeCard(songs[2].name, "chart"))
        // Up: 最近播放, 追蹤 (cards, then its 播放全部), the card.
        compose.dpad(Dpad.Up, times = 4)
        compose.assertFocusedTag(CONTINUE_CARD_TAG)
    }

    @Test
    fun continueCard_arrivingAfterTheRows_takesTheFocus_andTheFollowedRowDoesNotTakeItBack() {
        setHome(full.copy(continueListening = null, followedNew = null))
        compose.assertFocused(homeCard(recent[0].name, "recent"))

        homeState = homeState.copy(continueListening = continueListening)
        compose.waitForIdle()
        compose.assertFocusedTag(CONTINUE_CARD_TAG)
        compose.assertFocusedFullyVisible()

        homeState = homeState.copy(followedNew = full.followedNew)
        compose.waitForIdle()
        compose.assertFocusedTag(CONTINUE_CARD_TAG)
    }

    @Test
    fun continueCard_arrivingAfterTheUserMoved_doesNotStealFocus() {
        setHome(full.copy(continueListening = null, followedNew = null))
        compose.dpad(Dpad.Right)
        compose.assertFocused(homeCard(recent[1].name, "recent"))

        homeState = homeState.copy(continueListening = continueListening)
        compose.waitForIdle()
        compose.assertFocused(homeCard(recent[1].name, "recent"))
        compose.dpad(Dpad.Up)
        compose.assertFocusedTag(CONTINUE_CARD_TAG)
    }

    @Test
    fun continueCardOnly_noOtherRows_isFocusedAndReachable() {
        setHome(HomeUiState(continueListening = continueListening))
        compose.assertFocusedTag(CONTINUE_CARD_TAG)
        compose.dpad(Dpad.Down, Dpad.Left)
        compose.assertFocusedTag(CONTINUE_CARD_TAG)
        compose.dpad(Dpad.Up)
        compose.assertFocused(HomeSearchBar)
    }
}
