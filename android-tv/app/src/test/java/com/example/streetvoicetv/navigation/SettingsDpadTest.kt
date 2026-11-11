package com.example.streetvoicetv.navigation

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.ui.settings.AmbientTimeout
import com.example.streetvoicetv.ui.settings.AppSettings
import com.example.streetvoicetv.ui.settings.ClearAction
import com.example.streetvoicetv.ui.settings.SETTINGS_ABOUT_TAG
import com.example.streetvoicetv.ui.settings.SettingsContent
import com.example.streetvoicetv.ui.settings.SettingsLabels
import com.example.streetvoicetv.ui.settings.SettingsUiState
import com.example.streetvoicetv.ui.settings.StartupBehavior
import com.example.streetvoicetv.ui.settings.next
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class SettingsDpadTest {

    @get:Rule
    val compose = createTvComposeRule()

    @Before
    fun setUp() = emulateTvDevice()

    private val calls = mutableListOf<String>()
    private var state by mutableStateOf(SettingsUiState(versionName = "1.2.3"))

    private fun setSettings() {
        compose.setContent {
            TvChrome {
                SettingsContent(
                    state = state,
                    onCycleStartup = {
                        calls += "startup"
                        state = state.copy(settings = state.settings.copy(startupBehavior = state.settings.startupBehavior.next()))
                    },
                    onToggleAutoplay = {
                        calls += "autoplay"
                        state = state.copy(autoplayEnabled = !state.autoplayEnabled)
                    },
                    onCycleAmbient = {
                        calls += "ambient"
                        state = state.copy(settings = state.settings.copy(ambientTimeout = state.settings.ambientTimeout.next()))
                    },
                    onClear = {
                        calls += "clear:$it"
                        state = state.copy(cleared = state.cleared + it)
                    },
                    onCheckUpdate = { calls += "update" },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun shown(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun rows_downThroughAllItems_withoutEscaping() {
        setSettings()
        compose.assertFocusedText(SettingsLabels.STARTUP)
        val order = listOf(
            SettingsLabels.AUTOPLAY,
            SettingsLabels.AMBIENT,
            ClearAction.SEARCH_HISTORY.title,
            ClearAction.IMAGE_CACHE.title,
            ClearAction.RECENTLY_PLAYED.title,
            SettingsLabels.CHECK_UPDATE,
            SettingsLabels.ABOUT,
        )
        for (title in order) {
            compose.dpad(Dpad.Down)
            compose.assertFocusedText(title)
            compose.assertFocusedFullyVisible()
        }
        // Bottom edge: stays on the last row; Up walks back.
        compose.dpad(Dpad.Down)
        compose.assertFocusedText(SettingsLabels.ABOUT)
        compose.dpad(Dpad.Up, times = order.size)
        compose.assertFocusedText(SettingsLabels.STARTUP)
        compose.dpad(Dpad.Left, Dpad.Right)
        compose.assertFocusedText(SettingsLabels.STARTUP)
    }

    @Test
    fun choiceAndToggleRows_changeInPlace_focusStays() {
        setSettings()
        assertTrue(shown(StartupBehavior.SHOW_HOME.label))
        compose.dpad(Dpad.Center)
        assertEquals(StartupBehavior.RESUME_LAST, state.settings.startupBehavior)
        assertTrue(shown(StartupBehavior.RESUME_LAST.label))
        compose.assertFocusedText(SettingsLabels.STARTUP)

        compose.dpad(Dpad.Down, Dpad.Center)
        assertEquals(false, state.autoplayEnabled)
        compose.assertFocusedText(SettingsLabels.AUTOPLAY)
        compose.dpad(Dpad.Center)
        assertEquals(true, state.autoplayEnabled)

        compose.dpad(Dpad.Down)
        repeat(3) { compose.dpad(Dpad.Center) }
        assertEquals(AmbientTimeout.SEC_30, state.settings.ambientTimeout)
        compose.assertFocusedText(SettingsLabels.AMBIENT)
        assertEquals(listOf("startup", "autoplay", "autoplay", "ambient", "ambient", "ambient"), calls)
        assertEquals(AppSettings(StartupBehavior.RESUME_LAST, AmbientTimeout.SEC_30), state.settings)
    }

    @Test
    fun clearRow_confirmDialog_cancelKeepsData_confirmClears() {
        setSettings()
        compose.dpad(Dpad.Down, times = 3)
        compose.assertFocusedText(ClearAction.SEARCH_HISTORY.title)

        // Open: focus starts on 取消 and is trapped in the dialog.
        compose.dpad(Dpad.Center)
        compose.assertFocusedText("取消")
        compose.dpad(Dpad.Up, Dpad.Down, Dpad.Left)
        compose.assertFocusedText("取消")
        compose.dpad(Dpad.Center)
        assertTrue("cancel does not clear", calls.isEmpty())
        compose.assertFocusedText(ClearAction.SEARCH_HISTORY.title)

        // Back also cancels.
        compose.dpad(Dpad.Center, Dpad.Back)
        assertTrue(calls.isEmpty())
        compose.assertFocusedText(ClearAction.SEARCH_HISTORY.title)

        // Confirm.
        compose.dpad(Dpad.Center, Dpad.Right)
        compose.assertFocusedText("清除")
        compose.dpad(Dpad.Center)
        assertEquals(listOf("clear:${ClearAction.SEARCH_HISTORY}"), calls)
        compose.assertFocusedText(ClearAction.SEARCH_HISTORY.title)
        assertTrue(shown("已清除"))
    }

    @Test
    fun updateAndAbout() {
        setSettings()
        compose.dpad(Dpad.Down, times = 6)
        compose.assertFocusedText(SettingsLabels.CHECK_UPDATE)
        assertTrue(shown("版本 1.2.3"))
        compose.dpad(Dpad.Center)
        assertEquals(listOf("update"), calls)

        compose.dpad(Dpad.Down, Dpad.Center)
        assertTrue(compose.onAllNodes(hasTestTag(SETTINGS_ABOUT_TAG)).fetchSemanticsNodes().isNotEmpty())
        compose.assertFocusedText("關閉")
        compose.dpad(Dpad.Up, Dpad.Left)
        compose.assertFocusedText("關閉")
        compose.dpad(Dpad.Back)
        assertTrue(compose.onAllNodes(hasTestTag(SETTINGS_ABOUT_TAG)).fetchSemanticsNodes().isEmpty())
        compose.assertFocusedText(SettingsLabels.ABOUT)
    }

    @Test
    fun myPage_gearRightOfLogout_opensSettings() {
        var opened = 0
        compose.setContent {
            TvChrome {
                com.example.streetvoicetv.ui.mypage.MyPageContent(
                    uiState = com.example.streetvoicetv.ui.mypage.MyPageUiState(
                        profile = com.example.streetvoicetv.screenshot.FakeData.artist(0),
                        playlists = com.example.streetvoicetv.screenshot.FakeData.playlists.take(2),
                    ),
                    onSongSelected = { _, _ -> },
                    onPlaylistSelected = {},
                    onLogout = {},
                    onOpenSettings = { opened++ },
                )
            }
        }
        compose.waitForIdle()
        compose.dpad(Dpad.Up)
        compose.assertFocusedText("登出")
        compose.dpad(Dpad.Right)
        compose.assertFocusedDescription("設定")
        compose.dpad(Dpad.Center)
        assertEquals(1, opened)
    }
}
