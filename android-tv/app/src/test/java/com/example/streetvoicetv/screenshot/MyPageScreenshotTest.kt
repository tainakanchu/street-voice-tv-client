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

    @Composable
    private fun MyPage(state: MyPageUiState) {
        MyPageContent(uiState = state, onSongSelected = { _, _ -> }, onPlaylistSelected = {}, onLogout = {})
    }
}
