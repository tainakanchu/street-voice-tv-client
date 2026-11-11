package com.example.streetvoicetv.navigation

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.screenshot.FakeData
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.screenshot.installFakeImageLoader
import com.example.streetvoicetv.testutil.FakePlaybackActions
import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
import com.example.streetvoicetv.ui.components.APP_MESSAGE_TAG
import com.example.streetvoicetv.ui.components.AppMessageHost
import com.example.streetvoicetv.ui.components.AppMessageState
import com.example.streetvoicetv.ui.components.SONG_MENU_TAG
import com.example.streetvoicetv.ui.components.SongMenuHost
import com.example.streetvoicetv.ui.components.SongMenuViewModel
import com.example.streetvoicetv.ui.home.HomeContent
import com.example.streetvoicetv.ui.home.HomeUiState
import com.example.streetvoicetv.ui.search.SearchContent
import com.example.streetvoicetv.ui.search.SearchUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The song context menu: long-press OK / MENU on song cards and rows. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class SongMenuDpadTest {

    @get:Rule
    val compose = createTvComposeRule()

    @Before
    fun setUp() {
        installFakeImageLoader()
        emulateTvDevice()
    }

    private val songs = FakeData.songs
    private val repository = FakeStreetVoiceRepository().apply {
        // Detail: the album is known (前往專輯), not liked.
        onSongDetail = { id -> Result.success(songs.first { it.id == id }.copy(albumId = 77)) }
    }
    private val playback = FakePlaybackActions()
    private val session = SessionManager(
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("song_menu_dpad", Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() },
    ) {}
    private val messages = AppMessageState(durationMs = null)
    private val selected = mutableListOf<Song>()
    private val artistTaps = mutableListOf<String>()
    private val albumTaps = mutableListOf<Int>()

    private fun setWithMenu(content: @Composable () -> Unit) {
        val vm = SongMenuViewModel(playback, repository, session)
        compose.setContent {
            TvChrome(
                contentHost = { screen ->
                    AppMessageHost(messages) {
                        SongMenuHost(
                            viewModel = vm,
                            onGoToArtist = { artistTaps += it },
                            onGoToAlbum = { albumTaps += it },
                            content = screen,
                        )
                    }
                },
                content = content,
            )
        }
        compose.waitForIdle()
    }

    private fun setHome() = setWithMenu {
        HomeContent(
            uiState = HomeUiState(chartSongs = songs, playlists = FakeData.playlists),
            isLoggedIn = session.isLoggedIn.value,
            profileImageUrl = null,
            onSongSelected = { song, _ -> selected += song },
            onPlaylistSelected = {},
            onSearchTap = {},
            onLoginTap = {},
            onMyPageTap = {},
        )
    }

    private fun setSearchResults() = setWithMenu {
        SearchContent(
            uiState = SearchUiState(query = "周", songs = songs, resultsGeneration = 1),
            searchHistory = emptyList(),
            onQueryChange = {},
            onSearch = {},
            onClearError = {},
            onSelectHistory = {},
            onSongSelected = { song, _ -> selected += song },
            onArtistSelected = {},
            onPlaylistSelected = {},
        )
    }

    private val inMenu = hasTestTag(SONG_MENU_TAG)
    private fun menuOpen() = compose.onAllNodes(inMenu).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun longPressOnCard_opensMenu_withoutPlaying_focusTrapped_backReturnsToCard() {
        setHome()
        compose.dpad(Dpad.Right)
        compose.assertFocused(homeCard(songs[1].name, "chart"))

        compose.longPress(Dpad.Center)
        assertTrue(menuOpen())
        assertTrue("the long press must not also click the card", selected.isEmpty())
        compose.assertFocusInside(SONG_MENU_TAG)
        compose.assertFocusedText("下一首播放")
        compose.captureDpad("song_menu_open")

        // Focus never leaves the menu.
        for (key in listOf(Dpad.Up, Dpad.Down, Dpad.Left, Dpad.Right)) {
            compose.dpad(key, times = 6)
            compose.assertFocusInside(SONG_MENU_TAG)
        }
        assertTrue(playback.calls.isEmpty())

        compose.dpad(Dpad.Back)
        assertTrue(!menuOpen())
        compose.assertFocused(homeCard(songs[1].name, "chart"))
        // A normal press afterwards still clicks the card (nothing is left swallowed).
        compose.dpad(Dpad.Center)
        assertEquals(listOf(songs[1]), selected)
    }

    @Test
    fun longPress_keyUpReachingTheMenu_doesNotTriggerAnEntry() {
        setHome()
        compose.longPressSlowRelease(Dpad.Center)
        assertTrue(menuOpen())
        compose.assertFocusedText("下一首播放")
        assertTrue(playback.calls.isEmpty())
        assertTrue(selected.isEmpty())
        // The next, real press does.
        compose.dpad(Dpad.Center)
        assertEquals(listOf("playNext(${songs[0].id})"), playback.calls)
        assertTrue(!menuOpen())
        compose.assertFocused(homeCard(songs[0].name, "chart"))
    }

    @Test
    fun menuKeyOnRow_addToQueue_showsMessage_focusBackOnRow() {
        setSearchResults()
        compose.dpad(Dpad.Down)
        compose.assertFocusedText(songs[1].name)

        compose.dpad(Dpad.Menu)
        assertTrue(menuOpen())
        compose.assertFocusedText("下一首播放")
        compose.dpad(Dpad.Down)
        compose.assertFocusedText("加入佇列")
        compose.dpad(Dpad.Center)

        assertEquals(listOf("addToQueue(${songs[1].id})"), playback.calls)
        assertTrue(selected.isEmpty())
        assertTrue(!menuOpen())
        compose.assertFocusedText(songs[1].name)
        compose.onNodeWithTag(APP_MESSAGE_TAG).assertExists()
        assertEquals("已加入佇列：${songs[1].name}", messages.current?.text)
        compose.captureDpad("song_menu_message")

        // MENU toggles the menu closed again.
        compose.dpad(Dpad.Menu)
        assertTrue(menuOpen())
        compose.dpad(Dpad.Menu)
        assertTrue(!menuOpen())
        compose.assertFocusedText(songs[1].name)
    }

    @Test
    fun loggedOut_noLikeEntry_albumFromDetail_goToArtistAndAlbum() {
        setHome()
        compose.longPress(Dpad.Center)
        // Entries: 下一首播放, 加入佇列, 前往音樂人, 前往專輯 (album known from the detail); no 喜歡.
        assertTrue(compose.onAllNodes(hasText("喜歡")).fetchSemanticsNodes().isEmpty())
        compose.dpad(Dpad.Down, Dpad.Down)
        compose.assertFocusedText("前往音樂人")
        compose.dpad(Dpad.Center)
        assertEquals(listOf(songs[0].artistUsername), artistTaps)
        assertTrue(!menuOpen())
        compose.assertFocused(homeCard(songs[0].name, "chart"))

        compose.longPress(Dpad.Center)
        compose.dpad(Dpad.Down, times = 5)
        compose.assertFocusedText("前往專輯")
        compose.dpad(Dpad.Center)
        assertEquals(listOf(77), albumTaps)
    }

    @Test
    fun loggedIn_likeEntry_likesOptimistically() {
        session.saveSession("sid", "csrf", "me")
        setHome()
        compose.longPress(Dpad.Center)
        compose.dpad(Dpad.Down, times = 5)
        compose.assertFocusedText("喜歡")
        compose.dpad(Dpad.Center)
        assertTrue("likeSong(${songs[0].id})" in repository.calls)
        assertEquals("已加入喜歡：${songs[0].name}", messages.current?.text)

        compose.longPress(Dpad.Center)
        compose.dpad(Dpad.Down, times = 5)
        compose.assertFocusedText("取消喜歡")
        compose.dpad(Dpad.Back)
        compose.assertFocused(homeCard(songs[0].name, "chart"))
    }

    @Test
    fun withoutMenuHost_triggerIsInert() {
        // Screens outside a SongMenuHost (previews): the trigger is inert.
        compose.setContent {
            TvChrome {
                HomeContent(
                    uiState = HomeUiState(chartSongs = songs),
                    isLoggedIn = false,
                    profileImageUrl = null,
                    onSongSelected = { song, _ -> selected += song },
                    onPlaylistSelected = {},
                    onSearchTap = {},
                    onLoginTap = {},
                    onMyPageTap = {},
                )
            }
        }
        compose.waitForIdle()
        compose.dpad(Dpad.Menu)
        assertTrue(!menuOpen())
        compose.assertFocused(homeCard(songs[0].name, "chart"))
        // tv-material's own handling: a long press without onLongClick still clicks once.
        compose.longPress(Dpad.Center)
        assertEquals(listOf(songs[0]), selected)
    }
}
