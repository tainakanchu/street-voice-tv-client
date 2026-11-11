package com.example.streetvoicetv.screenshot

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.example.streetvoicetv.ui.mypage.MyPageContent
import com.example.streetvoicetv.ui.mypage.MyPageUiState

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class MyPageScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    @Test
    fun mypage_default() = compose.captureTv("mypage_default") {
        MyPage(MyPageUiState(profile = FakeData.artists[0], likedSongs = FakeData.songs, playlists = FakeData.playlists))
    }

    @Test
    fun mypage_following() = compose.captureTv("mypage_following") {
        MyPage(
            MyPageUiState(
                profile = FakeData.artists[0],
                likedSongs = FakeData.songs,
                playlists = FakeData.playlists,
                following = FakeData.artists + FakeData.longArtists.mapIndexed { i, a -> a.copy(username = "long_$i") },
            ),
        )
    }

    @Test
    fun mypage_following_no_playlists() = compose.captureTv("mypage_following_no_playlists") {
        MyPage(MyPageUiState(profile = FakeData.artists[0], likedSongs = FakeData.songs, following = FakeData.artists))
    }

    @Test
    fun mypage_long_titles() = compose.captureTv("mypage_long_titles") {
        MyPage(
            MyPageUiState(
                profile = FakeData.longArtists[0],
                likedSongs = FakeData.longSongs,
                playlists = FakeData.longPlaylists,
            ),
        )
    }

    @Test
    fun mypage_empty() = compose.captureTv("mypage_empty") {
        MyPage(MyPageUiState(profile = FakeData.artists[1].copy(introduction = null)))
    }

    @Test
    fun mypage_no_profile() = compose.captureTv("mypage_no_profile") {
        MyPage(MyPageUiState(profile = null))
    }

    @Test
    fun mypage_partial_errors() = compose.captureTv("mypage_partial_errors") {
        MyPage(
            MyPageUiState(
                profile = FakeData.artists[0],
                playlists = FakeData.playlists,
                likedError = com.example.streetvoicetv.ui.common.UserMessages.TIMEOUT,
            ),
        )
    }

    @Test
    fun mypage_needs_login() = compose.captureTv("mypage_needs_login") {
        MyPage(MyPageUiState(needsLogin = true))
    }

    @Test
    fun mypage_loading() = compose.captureTv("mypage_loading") { MyPage(MyPageUiState(isLoading = true)) }

    @Test
    fun mypage_focused_logout() = compose.captureTv(
        "mypage_focused_logout",
        beforeCapture = { focusOnText("登出") },
    ) {
        MyPage(MyPageUiState(profile = FakeData.longArtists[0], likedSongs = FakeData.longSongs, playlists = FakeData.longPlaylists))
    }

    @Test
    fun mypage_with_now_playing() = compose.captureTv(
        "mypage_with_now_playing",
        nowPlaying = FakeData.playbackLong,
    ) {
        MyPage(MyPageUiState(profile = FakeData.artists[0], likedSongs = FakeData.songs, playlists = FakeData.playlists))
    }

    @Test
    fun mypage_logout_confirm() = compose.captureTv("mypage_logout_confirm") {
        MyPage(
            MyPageUiState(profile = FakeData.artists[0], likedSongs = FakeData.songs, playlists = FakeData.playlists),
            showLogoutConfirm = true,
        )
    }

    @Test
    fun mypage_focused_liked_song() = compose.captureTv(
        "mypage_focused_liked_song",
        beforeCapture = { focusOnText(FakeData.longSongs[0].name) },
    ) {
        MyPage(MyPageUiState(profile = FakeData.longArtists[0], likedSongs = FakeData.longSongs, playlists = emptyList()))
    }

    @Composable
    private fun MyPage(state: MyPageUiState, showLogoutConfirm: Boolean = false) {
        MyPageContent(
            uiState = state,
            onSongSelected = { _, _ -> },
            onPlaylistSelected = {},
            onLogout = {},
            initialShowLogoutConfirm = showLogoutConfirm,
        )
    }
}
