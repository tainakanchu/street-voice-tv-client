package com.example.streetvoicetv.screenshot

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.streetvoicetv.ui.components.AppMessageHost
import com.example.streetvoicetv.ui.components.AppMessageState
import com.example.streetvoicetv.ui.components.SongMenuLayer
import com.example.streetvoicetv.ui.components.SongMenuState
import com.example.streetvoicetv.ui.home.HomeContent
import com.example.streetvoicetv.ui.home.HomeUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The song context menu and the brief message bar. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class SongMenuScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    private val home = HomeUiState(
        chartSongs = FakeData.songs,
        editorPicks = FakeData.songs.reversed(),
        playlists = FakeData.playlists,
    )

    @Test
    fun song_menu_logged_out() = compose.captureTv("song_menu_logged_out") {
        WithMenu(SongMenuState(FakeData.songs[0], isLoggedIn = false, isLiked = false)) { Home() }
    }

    @Test
    fun song_menu_logged_in() = compose.captureTv("song_menu_logged_in") {
        WithMenu(SongMenuState(FakeData.songs[0].copy(albumId = 77), isLoggedIn = true, isLiked = false)) { Home() }
    }

    @Test
    fun song_menu_liked_long_title() = compose.captureTv(
        "song_menu_liked_long_title",
        nowPlaying = FakeData.playbackLong,
    ) {
        WithMenu(SongMenuState(FakeData.playerSongLong.copy(albumId = 77), isLoggedIn = true, isLiked = true)) { Home() }
    }

    @Test
    fun app_message() = compose.captureTv("app_message") {
        WithMessage("已加入佇列：${FakeData.songs[1].name}") { Home() }
    }

    @Test
    fun app_message_long_with_now_playing() = compose.captureTv(
        "app_message_long_with_now_playing",
        nowPlaying = FakeData.playbackLong,
    ) {
        WithMessage("已加入佇列：${FakeData.playerSongLong.name}") { Home() }
    }

    @Composable
    private fun WithMenu(menu: SongMenuState, content: @Composable () -> Unit) {
        SongMenuLayer(menu = menu, onOpen = {}, onAction = { _, _ -> }, onDismiss = {}, content = content)
    }

    @Composable
    private fun WithMessage(text: String, content: @Composable () -> Unit) {
        val state = remember { AppMessageState(durationMs = null).apply { show(text) } }
        AppMessageHost(state, content)
    }

    @Composable
    private fun Home() {
        HomeContent(
            uiState = home,
            isLoggedIn = false,
            profileImageUrl = null,
            onSongSelected = { _, _ -> },
            onPlaylistSelected = {},
            onSearchTap = {},
            onLoginTap = {},
            onMyPageTap = {},
        )
    }
}
