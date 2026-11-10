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
}
