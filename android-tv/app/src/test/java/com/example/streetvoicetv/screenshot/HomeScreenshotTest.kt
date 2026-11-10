package com.example.streetvoicetv.screenshot

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.streetvoicetv.ui.home.HomeContent
import com.example.streetvoicetv.ui.home.HomeUiState
import com.example.streetvoicetv.ui.common.UserMessages
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class HomeScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    private val defaultState = HomeUiState(
        chartSongs = FakeData.songs,
        editorPicks = FakeData.songs.reversed(),
        playlists = FakeData.playlists,
    )

    @Test
    fun home_default() = compose.captureTv("home_default") {
        Home(defaultState)
    }

    @Test
    fun home_logged_in() = compose.captureTv("home_logged_in") {
        Home(defaultState.copy(myPlaylists = FakeData.playlists.take(3)), isLoggedIn = true)
    }

    @Test
    fun home_loading() = compose.captureTv("home_loading") {
        Home(HomeUiState(isLoading = true))
    }

    @Test
    fun home_empty() = compose.captureTv("home_empty") {
        Home(HomeUiState())
    }

    @Test
    fun home_long_titles() = compose.captureTv("home_long_titles") {
        Home(
            HomeUiState(
                chartSongs = FakeData.longSongs,
                editorPicks = FakeData.longSongs.reversed(),
                playlists = FakeData.longPlaylists,
                myPlaylists = FakeData.longPlaylists.take(5),
            ),
            isLoggedIn = true,
        )
    }

    @Test
    fun home_focused_first_song() = compose.captureTv(
        "home_focused_first_song",
        beforeCapture = { focusOnText(FakeData.songs[0].name) },
    ) {
        Home(defaultState)
    }

    @Test
    fun home_focused_long_song() = compose.captureTv(
        "home_focused_long_song",
        beforeCapture = { focusOnText(FakeData.longSongs[0].name) },
    ) {
        Home(HomeUiState(chartSongs = FakeData.longSongs, playlists = FakeData.longPlaylists))
    }

    @Test
    fun home_with_now_playing() = compose.captureTv(
        "home_with_now_playing",
        nowPlaying = FakeData.playbackPlaying,
    ) {
        Home(defaultState)
    }

    @Test
    fun home_with_now_playing_long() = compose.captureTv(
        "home_with_now_playing_long",
        nowPlaying = FakeData.playbackLong,
    ) {
        Home(defaultState, isLoggedIn = true)
    }

    @Test
    fun home_error() = compose.captureTv("home_error") {
        Home(HomeUiState(error = UserMessages.TIMEOUT))
    }

    @Test
    fun home_long_with_now_playing_long() = compose.captureTv(
        "home_long_with_now_playing_long",
        nowPlaying = FakeData.playbackLong,
    ) {
        Home(
            HomeUiState(
                chartSongs = FakeData.longSongs,
                editorPicks = FakeData.longSongs.reversed(),
                playlists = FakeData.longPlaylists,
                myPlaylists = FakeData.longPlaylists.take(5),
            ),
            isLoggedIn = true,
        )
    }

    @androidx.compose.runtime.Composable
    private fun Home(state: HomeUiState, isLoggedIn: Boolean = false) {
        HomeContent(
            uiState = state,
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
