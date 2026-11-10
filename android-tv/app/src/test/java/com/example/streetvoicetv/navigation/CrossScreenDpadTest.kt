package com.example.streetvoicetv.navigation

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.example.streetvoicetv.screenshot.FakeData
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.screenshot.installFakeImageLoader
import com.example.streetvoicetv.ui.album.AlbumContent
import com.example.streetvoicetv.ui.album.AlbumUiState
import com.example.streetvoicetv.ui.artist.ArtistContent
import com.example.streetvoicetv.ui.artist.ArtistUiState
import com.example.streetvoicetv.ui.components.NOW_PLAYING_BAR_TAG
import com.example.streetvoicetv.ui.home.HomeContent
import com.example.streetvoicetv.ui.home.HomeUiState
import com.example.streetvoicetv.ui.mypage.MyPageContent
import com.example.streetvoicetv.ui.mypage.MyPageUiState
import com.example.streetvoicetv.ui.player.PlayerContent
import com.example.streetvoicetv.ui.player.PlayerLoadingState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Flows across screens. MainActivity's NavHost needs Hilt view models, ExoPlayer and the network,
 * so these tests use a real navigation-compose NavHost wired the same way (routes, Back via the
 * activity's OnBackPressedDispatcher) but with the stateless XxxContent composables and fake state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class CrossScreenDpadTest {

    @get:Rule
    val compose = createTvComposeRule()

    @Before
    fun setUp() {
        installFakeImageLoader()
        emulateTvDevice()
    }

    private val home = HomeUiState(
        chartSongs = FakeData.songs,
        editorPicks = FakeData.songs.reversed(),
        playlists = FakeData.playlists,
    )

    private fun setApp() {
        compose.setContent {
            val nav = rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            val onPlayer = entry?.destination?.route == "player"
            TvChrome(
                nowPlaying = if (onPlayer) FakeData.playbackPlaying.copy(song = null) else FakeData.playbackPlaying,
                onNowPlayingTap = { nav.navigate("player") },
            ) {
                NavHost(nav, startDestination = "home", modifier = Modifier.fillMaxSize()) {
                    composable("home") {
                        HomeContent(
                            uiState = home,
                            isLoggedIn = true,
                            profileImageUrl = null,
                            onSongSelected = { _, _ -> nav.navigate("player") },
                            onPlaylistSelected = { nav.navigate("album") },
                            onSearchTap = {},
                            onLoginTap = {},
                            onMyPageTap = { nav.navigate("mypage") },
                        )
                    }
                    composable("player") {
                        PlayerContent(
                            loadingState = PlayerLoadingState(isLoaded = true),
                            playbackState = FakeData.playbackPlaying,
                            isLiked = false,
                            isLoggedIn = true,
                            onRetry = {},
                            onBack = { nav.popBackStack() },
                            onArtistSelected = { nav.navigate("artist") },
                            onToggleShuffle = {},
                            onSkipPrevious = {},
                            onTogglePlayPause = {},
                            onSkipNext = {},
                            onToggleRepeatMode = {},
                            onToggleLike = {},
                        )
                    }
                    composable("album") {
                        AlbumContent(
                            AlbumUiState(album = FakeData.album(0), songs = FakeData.songs),
                            onSongSelected = { _, _ -> nav.navigate("player") },
                            onRetry = {},
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("artist") {
                        ArtistContent(
                            ArtistUiState(artist = FakeData.artist(0), songs = FakeData.songs, albums = FakeData.albums),
                            onSongSelected = { _, _ -> nav.navigate("player") },
                            onAlbumSelected = { nav.navigate("album") },
                            onRetry = {},
                            onBack = { nav.popBackStack() },
                            onSelectTab = {},
                        )
                    }
                    composable("mypage") {
                        MyPageContent(
                            uiState = MyPageUiState(
                                profile = FakeData.artist(1),
                                playlists = FakeData.playlists.take(4),
                                likedSongs = FakeData.songs,
                            ),
                            onSongSelected = { _, _ -> nav.navigate("player") },
                            onPlaylistSelected = { nav.navigate("album") },
                            onLogout = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun homeToPlayerAndBack_restoresFocusedCard() {
        setApp()
        compose.dpad(Dpad.Down, Dpad.Right, Dpad.Right)
        compose.assertFocused(homeCard(FakeData.songs.reversed()[2].name, "editor"))

        compose.dpad(Dpad.Center)
        compose.assertFocusedDescription("暫停")
        compose.captureDpad("nav_player_opened")

        compose.dpad(Dpad.Back)
        compose.assertFocused(homeCard(FakeData.songs.reversed()[2].name, "editor"))
        compose.assertFocusedFullyVisible()
        compose.captureDpad("nav_back_to_home")
    }

    @Test
    fun homeToAlbumToPlayer_backTwice_restoresEachScreen() {
        setApp()
        compose.dpad(Dpad.Down, Dpad.Down, Dpad.Right)
        compose.assertFocused(homeCard(FakeData.playlists[1].name, "playlists"))
        compose.dpad(Dpad.Center)
        compose.assertFocusedText(FakeData.songs[0].name)

        compose.dpad(Dpad.Down, Dpad.Down, Dpad.Down)
        compose.assertFocusedText(FakeData.songs[3].name)
        compose.dpad(Dpad.Center)
        compose.assertFocusedDescription("暫停")

        compose.dpad(Dpad.Back)
        compose.assertFocusedText(FakeData.songs[3].name)
        compose.assertFocusedFullyVisible()
        compose.dpad(Dpad.Back)
        compose.assertFocused(homeCard(FakeData.playlists[1].name, "playlists"))
    }

    @Test
    fun nowPlayingBar_onArtistAndMyPage_reachableAndLeavable() {
        setApp()
        // Home -> avatar -> MyPage
        compose.dpad(Dpad.Up, Dpad.Right)
        compose.dpad(Dpad.Center)
        compose.assertFocusedText(FakeData.playlists[0].name)
        walkIntoNowPlayingBarAndBack()
        compose.dpad(Dpad.Back)
        compose.assertFocusedDescription("我的頁面")

        // Home -> player -> artist page
        compose.dpad(Dpad.Down, Dpad.Center)
        compose.assertFocusedDescription("暫停")
        compose.dpad(Dpad.Up, Dpad.Up, Dpad.Center)
        compose.assertFocusedText(FakeData.songs[0].name)
        walkIntoNowPlayingBarAndBack()
    }

    @Test
    fun nowPlayingBar_tapOpensPlayer_backReturnsToScreen() {
        setApp()
        compose.dpad(Dpad.Down, Dpad.Down, Dpad.Down)
        compose.assertFocusInside(NOW_PLAYING_BAR_TAG)
        compose.dpad(Dpad.Center)
        compose.assertFocusedDescription("暫停")
        // Back returns to the Home element focused before the bar (the bar lives outside the
        // screens, so the screen's own focus restoration picks the card); Down reaches the bar again.
        compose.dpad(Dpad.Back)
        compose.assertFocused(inHomeSection("playlists"))
        compose.dpad(Dpad.Down)
        compose.assertFocusInside(NOW_PLAYING_BAR_TAG)
    }

    /** Down until the NowPlayingBar is focused, check it is not a trap, and come back up. */
    private fun walkIntoNowPlayingBarAndBack() {
        var lastContent = compose.focusedText()
        var presses = 0
        while (!compose.isFocusInside(NOW_PLAYING_BAR_TAG) && presses < 30) {
            lastContent = compose.focusedText()
            compose.dpad(Dpad.Down)
            compose.assertFocusedFullyVisible()
            presses++
        }
        compose.assertFocusInside(NOW_PLAYING_BAR_TAG)
        // No trap and no loss: sideways / down keep focus in a sensible place.
        compose.dpad(Dpad.Right, Dpad.Down, Dpad.Left, Dpad.Down)
        compose.assertFocusInside(NOW_PLAYING_BAR_TAG)
        // Up returns to the element focus came from.
        compose.dpad(Dpad.Up)
        compose.assertFocusedText(lastContent)
    }
}
