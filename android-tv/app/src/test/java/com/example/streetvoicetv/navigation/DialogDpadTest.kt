package com.example.streetvoicetv.navigation

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import com.example.streetvoicetv.data.update.UpdateState
import com.example.streetvoicetv.screenshot.FakeData
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.screenshot.installFakeImageLoader
import com.example.streetvoicetv.ui.common.UserMessages
import com.example.streetvoicetv.ui.home.HomeContent
import com.example.streetvoicetv.ui.home.HomeUiState
import com.example.streetvoicetv.ui.mypage.MyPageContent
import com.example.streetvoicetv.ui.mypage.MyPageUiState
import com.example.streetvoicetv.ui.update.UPDATE_DIALOG_BODY_TAG
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Modal dialogs (UpdateDialog, logout ConfirmDialog) and the MessageState error screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class DialogDpadTest {

    @get:Rule
    val compose = createTvComposeRule()

    @Before
    fun setUp() {
        installFakeImageLoader()
        emulateTvDevice()
    }

    private val all = Dpad.entries.filter { it != Dpad.Center && it != Dpad.Back }

    // ------------------------------------------------------------ UpdateDialog

    private var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
    private var updates = 0
    private var dismisses = 0
    private var homeRetries = 0

    private fun setHomeWithUpdateDialog(home: HomeUiState = HomeUiState(chartSongs = FakeData.songs, playlists = FakeData.playlists)) {
        compose.setContent {
            TvChrome(
                nowPlaying = FakeData.playbackPlaying,
                updateState = updateState,
                onUpdate = { updates++ },
                onDismissUpdate = {
                    dismisses++
                    updateState = UpdateState.Idle
                },
            ) {
                HomeContent(
                    uiState = home,
                    isLoggedIn = false,
                    profileImageUrl = null,
                    onSongSelected = { _, _ -> },
                    onPlaylistSelected = {},
                    onSearchTap = {},
                    onLoginTap = {},
                    onMyPageTap = {},
                    onRetry = { homeRetries++ },
                )
            }
        }
        compose.waitForIdle()
    }

    private val updateButtons = hasText("更新") or hasText("稍後")

    @Test
    fun updateAvailable_focusTrappedInDialog_backDismisses_focusReturnsToScreen() {
        setHomeWithUpdateDialog()
        // The user is on the third card when the update check finishes.
        compose.dpad(Dpad.Right, Dpad.Right)
        compose.assertFocusedText(FakeData.songs[2].name)

        updateState = UpdateState.Available("1.2.3", "https://example.invalid/app.apk")
        compose.waitForIdle()
        compose.assertFocused(hasText("更新"))
        compose.captureDpad("update_dialog")

        compose.dpad(Dpad.Left)
        compose.assertFocused(hasText("稍後"))
        compose.dpad(Dpad.Right)
        compose.assertFocused(hasText("更新"))
        // No direction ever escapes to the screen below.
        for (key in all) {
            compose.dpad(key, times = 3)
            compose.assertFocused(updateButtons)
        }

        compose.dpad(Dpad.Back)
        assertEquals(1, dismisses)
        // Back on the screen where the user was.
        compose.assertFocusedText(FakeData.songs[2].name)
        compose.captureDpad("update_dialog_dismissed")
    }

    @Test
    fun updateAvailable_centerOnLaterDismisses() {
        setHomeWithUpdateDialog()
        updateState = UpdateState.Available("1.2.3", "https://example.invalid/app.apk")
        compose.waitForIdle()
        compose.dpad(Dpad.Center)
        assertEquals(1, updates)
        compose.dpad(Dpad.Left, Dpad.Center)
        assertEquals(1, dismisses)
        compose.assertFocusedText(FakeData.songs[0].name)
    }

    @Test
    fun updateDownloading_keysAndBackDoNothing() {
        setHomeWithUpdateDialog()
        updateState = UpdateState.Downloading(0.4f)
        compose.waitForIdle()
        compose.assertFocusedTag(UPDATE_DIALOG_BODY_TAG)
        for (key in all + Dpad.Back + Dpad.Center) {
            compose.dpad(key)
            compose.assertFocusedTag(UPDATE_DIALOG_BODY_TAG)
        }
        assertEquals(0, dismisses)
    }

    @Test
    fun updateError_closeFocused_backDismisses() {
        setHomeWithUpdateDialog()
        updateState = UpdateState.Error("boom")
        compose.waitForIdle()
        compose.assertFocused(hasText("關閉"))
        for (key in all) {
            compose.dpad(key)
            compose.assertFocused(hasText("關閉"))
        }
        compose.dpad(Dpad.Back)
        assertEquals(1, dismisses)
        compose.assertFocusedText(FakeData.songs[0].name)
    }

    // ------------------------------------------------------------ MessageState

    @Test
    fun homeError_retryFocused_centerRetries_topBarReachable() {
        setHomeWithUpdateDialog(HomeUiState(error = UserMessages.TIMEOUT))
        compose.assertFocused(hasText("重試"))
        compose.dpad(Dpad.Center)
        assertEquals(1, homeRetries)
        compose.dpad(Dpad.Left, Dpad.Right, Dpad.Down)
        compose.assertFocused(hasText("重試"))
        compose.dpad(Dpad.Up)
        compose.assertFocused(hasText("搜尋歌曲與音樂人...") or hasText("登入"))
        compose.dpad(Dpad.Down)
        compose.assertFocused(hasText("重試"))
    }

    // ------------------------------------------------------------ Logout ConfirmDialog

    private var logouts = 0

    private fun setMyPage(state: MyPageUiState) {
        compose.setContent {
            TvChrome(nowPlaying = FakeData.playbackPlaying) {
                MyPageContent(
                    uiState = state,
                    onSongSelected = { _, _ -> },
                    onPlaylistSelected = {},
                    onLogout = { logouts++ },
                )
            }
        }
        compose.waitForIdle()
    }

    private val confirmButtons = hasText("取消") or hasText("登出")

    @Test
    fun logoutConfirm_focusStartsOnCancel_trapped_backReturnsToLogoutButton() {
        setMyPage(
            MyPageUiState(
                profile = FakeData.artist(0),
                playlists = FakeData.playlists.take(4),
                likedSongs = FakeData.songs.take(3),
            ),
        )
        compose.assertFocusedText(FakeData.playlists[0].name)

        // Up from the first playlist reaches the 登出 button in the profile header.
        compose.dpad(Dpad.Up)
        compose.assertFocused(hasText("登出"))
        compose.dpad(Dpad.Center)
        compose.assertFocused(hasText("取消"))
        compose.captureDpad("logout_confirm")

        compose.dpad(Dpad.Right)
        compose.assertFocused(hasText("登出"))
        compose.dpad(Dpad.Left)
        compose.assertFocused(hasText("取消"))
        for (key in all) {
            compose.dpad(key, times = 3)
            compose.assertFocused(confirmButtons)
        }

        compose.dpad(Dpad.Back)
        assertEquals(0, logouts)
        compose.onNode(hasText("確定要登出嗎？")).assertDoesNotExist()
        // Focus returns to the button that opened the dialog.
        compose.assertFocused(hasText("登出"))

        // Cancel via the button behaves the same.
        compose.dpad(Dpad.Center, Dpad.Center)
        compose.assertFocused(hasText("登出"))

        // Confirming logs out.
        compose.dpad(Dpad.Center, Dpad.Right, Dpad.Center)
        assertEquals(1, logouts)
    }

    @Test
    fun logoutConfirm_fromProfileError_backReturnsToScreen() {
        setMyPage(MyPageUiState(profile = null))
        compose.assertFocused(hasText("重試"))
        compose.dpad(Dpad.Right)
        compose.assertFocused(hasText("登出"))
        compose.dpad(Dpad.Center)
        compose.assertFocused(hasText("取消"))
        compose.dpad(Dpad.Back)
        compose.assertFocused(hasText("重試") or hasText("登出"))
    }
}
