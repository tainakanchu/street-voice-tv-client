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
import com.example.streetvoicetv.data.update.UpdateState
import com.example.streetvoicetv.ui.home.HomeContent
import com.example.streetvoicetv.ui.home.HomeUiState
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class UpdateDialogScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    @Test
    fun update_available() = capture("update_available", UpdateState.Available("0.2.0", "https://example.invalid/app.apk"))

    @Test
    fun update_available_long_version() = capture(
        "update_available_long_version",
        UpdateState.Available("10.20.300-beta.4+build.20261109.abcdef0123456789", "https://example.invalid/app.apk"),
    )

    @Test
    fun update_downloading() = capture("update_downloading", UpdateState.Downloading(0.42f))

    @Test
    fun update_ready_to_install() = capture("update_ready_to_install", UpdateState.ReadyToInstall(File("app.apk")))

    @Test
    fun update_ready_to_install_needs_permission() = capture(
        "update_ready_to_install_needs_permission",
        UpdateState.ReadyToInstall(File("app.apk"), autoLaunch = false, needsPermission = true),
    )

    @Test
    fun update_install_error() = capture("update_install_error", UpdateState.Error("No Activity found", isInstallError = true))

    @Test
    fun update_error() = capture("update_error", UpdateState.Error("Download failed: HTTP 404"))

    @Test
    fun update_error_long() = capture(
        "update_error_long",
        UpdateState.Error(
            "java.io.IOException: Unable to download https://github.com/example/street-voice-tv-client/releases/download/v0.2.0/app-release.apk " +
                FakeData.LONG_ZH + " " + FakeData.LONG_JA,
        ),
    )

    @Test
    fun update_available_focused() = compose.captureTv(
        "update_available_focused",
        updateState = UpdateState.Available("0.2.0", "https://example.invalid/app.apk"),
        beforeCapture = { focusOnText("更新") },
    ) { Background() }

    private fun capture(name: String, state: UpdateState) =
        compose.captureTv(name, updateState = state) { Background() }

    /** The dialog is shown on top of the home screen in the real app. */
    @Composable
    private fun Background() {
        HomeContent(
            uiState = HomeUiState(chartSongs = FakeData.songs, playlists = FakeData.playlists),
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
