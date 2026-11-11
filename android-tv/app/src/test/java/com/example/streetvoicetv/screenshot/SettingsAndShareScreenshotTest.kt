package com.example.streetvoicetv.screenshot

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.ui.player.PlayerContent
import com.example.streetvoicetv.ui.player.PlayerLoadingState
import com.example.streetvoicetv.ui.player.SHARE_BUTTON_LABEL
import com.example.streetvoicetv.ui.settings.AmbientTimeout
import com.example.streetvoicetv.ui.settings.AppSettings
import com.example.streetvoicetv.ui.settings.ClearAction
import com.example.streetvoicetv.ui.settings.SettingsContent
import com.example.streetvoicetv.ui.settings.SettingsDialog
import com.example.streetvoicetv.ui.settings.SettingsLabels
import com.example.streetvoicetv.ui.settings.SettingsUiState
import com.example.streetvoicetv.ui.settings.StartupBehavior
import com.example.streetvoicetv.ui.settings.UpdateCheckStatus
import com.example.streetvoicetv.data.update.UpdateCheckResult
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Settings screen and the player's 分享 (QR code) entry / dialog. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class SettingsAndShareScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    private val defaultState = SettingsUiState(versionName = "1.4.0")

    @androidx.compose.runtime.Composable
    private fun Settings(state: SettingsUiState, dialog: SettingsDialog? = null) {
        SettingsContent(
            state = state,
            onCycleStartup = {},
            onToggleAutoplay = {},
            onCycleAmbient = {},
            onClear = {},
            onCheckUpdate = {},
            initialDialog = dialog,
        )
    }

    @Test
    fun settings_default() = compose.captureTv("settings_default") { Settings(defaultState) }

    /** 自動播放 off and focused; other rows show changed values and results. */
    @Test
    fun settings_focused_toggle() = compose.captureTv(
        "settings_focused_toggle",
        beforeCapture = { onNodeWithText(SettingsLabels.AUTOPLAY).requestFocus() },
    ) {
        Settings(
            defaultState.copy(
                settings = AppSettings(StartupBehavior.RESUME_LAST, AmbientTimeout.OFF),
                autoplayEnabled = false,
                cleared = setOf(ClearAction.SEARCH_HISTORY),
                updateCheck = UpdateCheckStatus.Done(UpdateCheckResult.UpToDate),
            ),
        )
    }

    @Test
    fun settings_confirm_dialog() = compose.captureTv("settings_confirm_dialog") {
        Settings(defaultState, SettingsDialog.Confirm(ClearAction.RECENTLY_PLAYED))
    }

    @Test
    fun settings_about() = compose.captureTv("settings_about") {
        Settings(defaultState, SettingsDialog.About)
    }

    @Test
    fun player_share_entry() = compose.captureTv(
        "player_share_entry",
        beforeCapture = { onNodeWithText(SHARE_BUTTON_LABEL).requestFocus() },
    ) {
        Player(FakeData.playbackPlaying)
    }

    @Test
    fun player_share_dialog() = compose.captureTv("player_share_dialog") {
        Player(FakeData.playbackPlaying, shareOpen = true)
    }

    @Test
    fun player_share_dialog_long_title() = compose.captureTv("player_share_dialog_long_title") {
        Player(FakeData.playbackLong, shareOpen = true)
    }

    @androidx.compose.runtime.Composable
    private fun Player(playback: PlaybackState, shareOpen: Boolean = false) {
        PlayerContent(
            loadingState = PlayerLoadingState(isLoaded = true),
            playbackState = playback,
            isLiked = false,
            isLoggedIn = true,
            onRetry = {},
            onBack = {},
            onArtistSelected = {},
            onToggleShuffle = {},
            onSkipPrevious = {},
            onTogglePlayPause = {},
            onSkipNext = {},
            onToggleRepeatMode = {},
            onToggleLike = {},
            initialShareOpen = shareOpen,
        )
    }
}
