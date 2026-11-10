package com.example.streetvoicetv.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasText
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.screenshot.FakeData
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.screenshot.TV_1080P
import com.example.streetvoicetv.screenshot.TvChrome
import com.example.streetvoicetv.screenshot.installFakeImageLoader
import com.example.streetvoicetv.ui.album.AlbumContent
import com.example.streetvoicetv.ui.album.AlbumUiState
import com.example.streetvoicetv.ui.artist.ArtistContent
import com.example.streetvoicetv.ui.artist.ArtistTab
import com.example.streetvoicetv.ui.artist.ArtistUiState
import com.example.streetvoicetv.ui.components.NOW_PLAYING_BAR_TAG
import com.example.streetvoicetv.ui.playlist.PlaylistContent
import com.example.streetvoicetv.ui.playlist.PlaylistUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** D-pad flows of the song-list detail screens: Album, Playlist and Artist. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class DetailDpadTest {

    @get:Rule
    val compose = createTvComposeRule()

    @Before
    fun setUp() {
        installFakeImageLoader()
        emulateTvDevice()
    }

    private fun setScreen(nowPlaying: PlaybackState = PlaybackState(), content: @Composable () -> Unit) {
        compose.setContent { TvChrome(nowPlaying = nowPlaying, content = content) }
        compose.waitForIdle()
    }

    /** Header of the screen, identified by its title text. */
    private fun headerFullyVisible(title: String): Boolean {
        val nodes = compose.onAllNodes(hasText(title)).fetchSemanticsNodes()
        if (nodes.isEmpty()) return false
        val b = nodes.first().boundsInRoot
        return b.top >= 0f && b.height > 0f
    }

    /**
     * Shared flow: initial focus on the first song; Down through all songs scrolls the header
     * away (focused row always visible, also next to the NowPlayingBar); Down past the end
     * reaches the NowPlayingBar; Up back to the first song reveals the header again.
     */
    private fun songListFlow(title: String, songs: List<Song>, capturePrefix: String) {
        compose.assertFocusedText(songs[0].name)
        assertTrue("header visible initially", headerFullyVisible(title))

        for (i in 1 until songs.size) {
            compose.dpad(Dpad.Down)
            compose.assertFocusedText(songs[i].name)
            compose.assertFocusedFullyVisible()
            if (i == 6) {
                assertTrue("header scrolled away", !headerFullyVisible(title))
                compose.captureDpad("${capturePrefix}_scrolled")
            }
        }

        compose.dpad(Dpad.Down)
        compose.assertFocusInside(NOW_PLAYING_BAR_TAG)
        compose.dpad(Dpad.Up)
        compose.assertFocusedText(songs.last().name)

        compose.dpad(Dpad.Up, times = songs.size - 1)
        compose.assertFocusedText(songs[0].name)
        compose.assertFocusedFullyVisible()
        assertTrue("header revealed again at the top", headerFullyVisible(title))
        compose.captureDpad("${capturePrefix}_back_to_top")
    }

    @Test
    fun album_songListFlow() {
        val album = FakeData.album(0)
        val songs = FakeData.longSongs.take(15)
        setScreen(FakeData.playbackPlaying) {
            AlbumContent(AlbumUiState(album = album, songs = songs), { _, _ -> }, {}, {})
        }
        songListFlow(album.name, songs, "album")
    }

    @Test
    fun playlist_songListFlow() {
        val playlist = FakeData.playlist(0)
        val songs = FakeData.songs
        setScreen(FakeData.playbackPlaying) {
            PlaylistContent(PlaylistUiState(playlist = playlist, songs = songs), { _, _ -> }, {}, {})
        }
        songListFlow(playlist.name, songs, "playlist")
    }

    // ---------------------------------------------------------------- Artist

    private var artistState by mutableStateOf(ArtistUiState())
    private val selectedTabs = mutableListOf<ArtistTab>()

    private fun setArtist(state: ArtistUiState) {
        artistState = state
        setScreen {
            ArtistContent(
                uiState = artistState,
                onSongSelected = { _, _ -> },
                onAlbumSelected = {},
                onRetry = {},
                onBack = {},
                onSelectTab = {
                    selectedTabs += it
                    artistState = artistState.copy(selectedTab = it)
                },
            )
        }
    }

    private val artist = FakeData.artist(0)
    private val songsTab get() = hasText("歌曲 (", substring = true)
    private val albumsTab get() = hasText("專輯 (", substring = true)

    @Test
    fun artist_tabsReachable_switchingKeepsFocus() {
        setArtist(ArtistUiState(artist = artist, songs = FakeData.songs, albums = FakeData.albums))
        compose.assertFocusedText(FakeData.songs[0].name)

        // Up from the first song reaches the (selected) Songs tab.
        compose.dpad(Dpad.Up)
        compose.assertFocused(songsTab)

        // Right focuses and selects the Albums tab; focus stays on the tab.
        compose.dpad(Dpad.Right)
        compose.assertFocused(albumsTab)
        assertEquals(ArtistTab.Albums, artistState.selectedTab)
        compose.captureDpad("artist_albums_tab")

        compose.dpad(Dpad.Down)
        compose.assertFocusedText(FakeData.albums[0].name)
        compose.dpad(Dpad.Down)
        compose.assertFocusedText(FakeData.albums[1].name)

        // Up from the album list returns to the Albums tab (and must not switch back to Songs).
        compose.dpad(Dpad.Up, Dpad.Up)
        compose.assertFocused(albumsTab)
        assertEquals(ArtistTab.Albums, artistState.selectedTab)

        compose.dpad(Dpad.Left)
        compose.assertFocused(songsTab)
        assertEquals(ArtistTab.Songs, artistState.selectedTab)
        compose.dpad(Dpad.Down)
        compose.assertFocusedText(FakeData.songs[0].name)
    }

    @Test
    fun artist_emptySongsTab_focusStillLandsSomewhere() {
        setArtist(ArtistUiState(artist = artist, songs = emptyList(), albums = FakeData.albums))
        // No song to focus: the selected tab gets the initial focus instead of nothing.
        compose.assertFocused(songsTab)

        // Down on an empty tab does not lose focus.
        compose.dpad(Dpad.Down)
        compose.assertFocused(songsTab)

        compose.dpad(Dpad.Right, Dpad.Down)
        compose.assertFocusedText(FakeData.albums[0].name)
        compose.dpad(Dpad.Up, Dpad.Left)
        compose.assertFocused(songsTab)
        compose.captureDpad("artist_empty_songs_tab")
    }

    @Test
    fun artist_bothTabsEmpty_tabsKeepFocus() {
        setArtist(ArtistUiState(artist = artist))
        compose.assertFocused(songsTab)
        compose.dpad(Dpad.Right, Dpad.Down, Dpad.Up, Dpad.Left, Dpad.Up)
        compose.assertFocused(songsTab)
    }
}
