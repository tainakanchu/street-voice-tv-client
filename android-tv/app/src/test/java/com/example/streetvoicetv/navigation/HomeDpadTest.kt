package com.example.streetvoicetv.navigation

import android.app.Application
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.screenshot.FakeData
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.screenshot.installFakeImageLoader
import com.example.streetvoicetv.ui.components.NOW_PLAYING_BAR_TAG
import com.example.streetvoicetv.ui.home.HomeContent
import com.example.streetvoicetv.ui.home.HomeUiState
import com.example.streetvoicetv.ui.home.FollowedNewSongs
import com.example.streetvoicetv.ui.home.RecentSong
import com.example.streetvoicetv.domain.model.ChartCategory
import com.example.streetvoicetv.domain.model.ChartPeriod
import com.example.streetvoicetv.domain.model.Song
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.streetvoicetv.ui.home.homeSectionTag
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

internal fun inHomeSection(key: String): SemanticsMatcher = hasAnyAncestor(hasTestTag(homeSectionTag(key)))

internal fun homeCard(name: String, section: String): SemanticsMatcher =
    hasText(name, substring = true) and inHomeSection(section)

internal val HomeSearchBar = hasText("搜尋歌曲與音樂人...")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class HomeDpadTest {

    @get:Rule
    val compose = createTvComposeRule()

    @Before
    fun setUp() {
        installFakeImageLoader()
        emulateTvDevice()
    }

    private val songs = FakeData.songs
    private val editor = FakeData.songs.reversed()
    private val state = HomeUiState(
        chartSongs = songs,
        editorPicks = editor,
        playlists = FakeData.playlists,
    )

    private var togglePlayPause = 0
    private var nowPlayingTaps = 0

    private fun setHome(
        isLoggedIn: Boolean = false,
        nowPlaying: PlaybackState = PlaybackState(),
        uiState: HomeUiState = state,
    ) {
        compose.setContent {
            TvChrome(
                nowPlaying = nowPlaying,
                onNowPlayingTap = { nowPlayingTaps++ },
                onTogglePlayPause = { togglePlayPause++ },
            ) {
                HomeContent(
                    uiState = uiState,
                    isLoggedIn = isLoggedIn,
                    profileImageUrl = if (isLoggedIn) "https://example.invalid/me.jpg" else null,
                    onSongSelected = { _, _ -> },
                    onPlaylistSelected = {},
                    onSearchTap = {},
                    onLoginTap = {},
                    onMyPageTap = {},
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun initialFocus_rightMovesAlongRow_keepingFocusedCardVisible() {
        setHome()
        compose.assertFocused(homeCard(songs[0].name, "chart"))

        for (i in 1 until songs.size) {
            compose.dpad(Dpad.Right)
            compose.assertFocused(homeCard(songs[i].name, "chart"))
            compose.assertFocusedFullyVisible()
        }
        // The end of the row: Right does not escape anywhere.
        compose.dpad(Dpad.Right)
        compose.assertFocused(homeCard(songs.last().name, "chart"))
        compose.captureDpad("home_row_end")
    }

    @Test
    fun downMovesToNextRow_upRestoresPreviouslyFocusedCard() {
        setHome()
        compose.assertFocused(homeCard(songs[0].name, "chart"))

        compose.dpad(Dpad.Down)
        compose.assertFocused(homeCard(editor[0].name, "editor"))
        compose.assertFocusedFullyVisible()

        // Move along the lower row (it scrolls), then Up returns to the card we came from
        // (focusRestorer), not to the card that is geometrically above.
        compose.dpad(Dpad.Right, times = 5)
        compose.assertFocused(homeCard(editor[5].name, "editor"))
        compose.dpad(Dpad.Up)
        compose.assertFocused(homeCard(songs[0].name, "chart"))
        compose.assertFocusedFullyVisible()
        compose.captureDpad("home_up_restores_card")

        // ... and Down again returns to where we left the lower row.
        compose.dpad(Dpad.Down)
        compose.assertFocused(homeCard(editor[5].name, "editor"))
        compose.assertFocusedFullyVisible()
    }

    @Test
    fun upFromFirstRow_reachesTopBar_downRestoresRow() {
        setHome()
        compose.dpad(Dpad.Right, times = 5)
        compose.assertFocused(homeCard(songs[5].name, "chart"))

        compose.dpad(Dpad.Up)
        compose.assertFocused(HomeSearchBar or hasText("登入"))
        compose.captureDpad("home_top_bar")

        // Top bar: search <-> login, Up stays in place.
        compose.dpad(Dpad.Left)
        compose.assertFocused(HomeSearchBar)
        compose.dpad(Dpad.Up)
        compose.assertFocused(HomeSearchBar)
        compose.dpad(Dpad.Right)
        compose.assertFocused(hasText("登入"))

        // Back down into the first row: the previously focused card is restored.
        compose.dpad(Dpad.Down)
        compose.assertFocused(homeCard(songs[5].name, "chart"))
    }

    @Test
    fun loggedIn_avatarReachableFromFirstRow() {
        setHome(isLoggedIn = true)
        compose.dpad(Dpad.Up)
        compose.assertFocused(HomeSearchBar)
        compose.dpad(Dpad.Right)
        compose.assertFocused(hasContentDescription("我的頁面"))
        compose.dpad(Dpad.Down)
        compose.assertFocused(homeCard(songs[0].name, "chart"))
    }

    @Test
    fun nowPlayingBar_reachableByDownFromLastRow_andBackUp() {
        setHome(nowPlaying = FakeData.playbackPlaying)

        compose.dpad(Dpad.Down)
        compose.assertFocused(inHomeSection("editor"))
        compose.assertFocusedFullyVisible()
        compose.dpad(Dpad.Down)
        compose.assertFocused(inHomeSection("playlists"))
        compose.assertFocusedFullyVisible()
        val lastRowCard = compose.describeFocus()

        // Down from the last row reaches the NowPlayingBar.
        compose.dpad(Dpad.Down)
        compose.assertFocusInside(NOW_PLAYING_BAR_TAG)
        compose.captureDpad("home_now_playing_bar")

        // Within the bar: tap target <-> play/pause.
        compose.dpad(Dpad.Right)
        compose.assertFocusedDescription("暫停")
        compose.dpad(Dpad.Center)
        assertEquals(1, togglePlayPause)
        compose.dpad(Dpad.Left)
        compose.assertFocusedText(FakeData.playbackPlaying.song!!.name)

        // Up leaves the bar back to the last row card we came from.
        compose.dpad(Dpad.Up)
        compose.assertFocused(inHomeSection("playlists"))
        assertEquals(lastRowCard, compose.describeFocus())
        compose.assertFocusedFullyVisible()

        // Opening the player from the bar first hands focus back to the screen (the bar is
        // hidden on the player screen; see AppScaffold), then navigates.
        compose.dpad(Dpad.Down, Dpad.Center)
        assertEquals(1, nowPlayingTaps)
        assertEquals(lastRowCard, compose.describeFocus())
    }

    @Test
    fun nowPlayingBar_neverCoversFocusedCard() {
        setHome(nowPlaying = FakeData.playbackLong)
        // Walk every card of every row; the focused card is never hidden behind the bar.
        for (row in 0 until 3) {
            repeat(9) {
                compose.dpad(Dpad.Right)
                compose.assertFocusedFullyVisible()
            }
            if (row < 2) {
                compose.dpad(Dpad.Down)
                compose.assertFocusedFullyVisible()
            }
        }
    }

    // ------------------------------------------------ 追蹤音樂人的新歌 / 曲風 / 本週排行

    private val followedSongs = FakeData.songs.take(6).map { it.copy(id = it.id + 500) }
    private val weekly = FakeData.songs.map { it.copy(id = it.id + 900) }
    private val followedRow = FollowedNewSongs(
        owner = "me",
        songs = followedSongs.mapIndexed { i, song -> RecentSong(song, "${i + 1} 天前") },
    )
    private val discoveryState = state.copy(
        genres = ChartCategory.forPeriod(ChartPeriod.Realtime),
        weeklySongs = weekly,
        followedNew = followedRow,
    )

    private var homeState by mutableStateOf(HomeUiState())
    private val played = mutableListOf<List<Song>>()
    private val chartTaps = mutableListOf<String>()

    private fun setDiscoveryHome(initial: HomeUiState) {
        homeState = initial
        compose.setContent {
            TvChrome {
                HomeContent(
                    uiState = homeState,
                    isLoggedIn = true,
                    profileImageUrl = null,
                    onSongSelected = { _, _ -> },
                    onPlaylistSelected = {},
                    onSearchTap = {},
                    onLoginTap = {},
                    onMyPageTap = {},
                    onPlayAll = { songs, _ -> played += songs },
                    onChartTap = { genre, period -> chartTaps += "${period.pathSegment}/$genre" },
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun discoveryRows_reachableTopToBottom() {
        setDiscoveryHome(discoveryState)
        // The followed artists' new songs are the first row and get the initial focus.
        compose.assertFocused(homeCard(followedSongs[0].name, "followed_new"))
        compose.assertFocusedFullyVisible()

        // Up: the row's 播放全部 (queue = the row, newest first), then the top bar.
        compose.dpad(Dpad.Up)
        compose.assertFocused(hasText("播放全部") and inHomeSection("followed_new"))
        compose.dpad(Dpad.Center)
        assertEquals(listOf(followedSongs), played)
        compose.dpad(Dpad.Up)
        compose.assertFocused(HomeSearchBar)

        // Down: back to the row (its cards, not the button), then the chart.
        compose.dpad(Dpad.Down)
        compose.assertFocused(inHomeSection("followed_new"))
        compose.dpad(Dpad.Down)
        compose.assertFocused(homeCard(songs[0].name, "chart"))
        compose.assertFocusedFullyVisible()

        // 曲風 chips: Center opens the realtime chart of that category.
        compose.dpad(Dpad.Down)
        compose.assertFocused(inHomeSection("genres"))
        compose.dpad(Dpad.Left, Dpad.Left)
        compose.assertFocused(hasText("全部") and inHomeSection("genres"))
        compose.dpad(Dpad.Right, Dpad.Center)
        assertEquals(listOf("realtime/rock"), chartTaps)
        compose.assertFocusedFullyVisible()

        // 本週排行: Down lands on the cards; Up from them reaches 查看全部.
        compose.dpad(Dpad.Down)
        compose.assertFocused(homeCard(weekly[0].name, "weekly"))
        compose.assertFocusedFullyVisible()
        compose.captureDpad("home_weekly_row")
        compose.dpad(Dpad.Up)
        compose.assertFocused(hasText("查看全部"))
        compose.dpad(Dpad.Center)
        assertEquals(listOf("realtime/rock", "weekly/all"), chartTaps)
        compose.dpad(Dpad.Down)
        compose.assertFocused(homeCard(weekly[0].name, "weekly"))

        // ... and the existing rows follow.
        compose.dpad(Dpad.Down)
        compose.assertFocused(homeCard(editor[0].name, "editor"))
        compose.dpad(Dpad.Down)
        compose.assertFocused(inHomeSection("playlists"))
        compose.assertFocusedFullyVisible()
    }

    @Test
    fun followedRow_arrivingLater_takesTheInitialFocus() {
        setDiscoveryHome(discoveryState.copy(followedNew = null))
        compose.assertFocused(homeCard(songs[0].name, "chart"))

        homeState = discoveryState
        compose.waitForIdle()
        compose.assertFocused(homeCard(followedSongs[0].name, "followed_new"))
        compose.assertFocusedFullyVisible()
    }

    @Test
    fun followedRow_arrivingAfterTheUserMoved_doesNotStealFocus() {
        setDiscoveryHome(discoveryState.copy(followedNew = null))
        compose.dpad(Dpad.Right)
        compose.assertFocused(homeCard(songs[1].name, "chart"))

        homeState = discoveryState
        compose.waitForIdle()
        compose.assertFocused(homeCard(songs[1].name, "chart"))
        compose.dpad(Dpad.Up)
        compose.assertFocused(inHomeSection("followed_new"))
    }

    @Test
    fun followedRowError_retryReachable() {
        setDiscoveryHome(discoveryState.copy(followedNew = FollowedNewSongs("me", emptyList(), error = "無法載入內容，請稍後再試")))
        compose.assertFocused(homeCard(songs[0].name, "chart"))
        compose.dpad(Dpad.Up)
        compose.assertFocused(hasText("重試") and inHomeSection("followed_new"))
    }
}
