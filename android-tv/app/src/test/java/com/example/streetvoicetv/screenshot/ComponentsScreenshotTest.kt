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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.streetvoicetv.playback.PlaybackState
import com.example.streetvoicetv.ui.components.AlbumListItem
import com.example.streetvoicetv.ui.components.ArtistListItem
import com.example.streetvoicetv.ui.components.NowPlayingBar
import com.example.streetvoicetv.ui.components.PlaylistListItem
import com.example.streetvoicetv.ui.components.SongListItem

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [SCREENSHOT_SDK], qualifiers = TV_1080P, application = Application::class)
class ComponentsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    @Before
    fun setUp() = installFakeImageLoader()

    @Test
    fun components_list_items() = compose.captureTv("components_list_items") {
        ListItems(long = false)
    }

    @Test
    fun components_list_items_long() = compose.captureTv("components_list_items_long") {
        ListItems(long = true)
    }

    @Test
    fun components_list_items_long_focused() = compose.captureTv(
        "components_list_items_long_focused",
        beforeCapture = { focusOnText(FakeData.longSongs[0].name) },
    ) {
        ListItems(long = true)
    }

    @Test
    fun components_list_items_focused_artist() = compose.captureTv(
        "components_list_items_focused_artist",
        beforeCapture = { focusOnText(FakeData.longArtists[0].displayName) },
    ) {
        ListItems(long = true)
    }

    @Test
    fun components_now_playing_bar() = compose.captureTv("components_now_playing_bar") {
        Column(
            Modifier.padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Bar(FakeData.playbackPlaying)
            Bar(FakeData.playbackPlaying.copy(isPlaying = false, song = FakeData.playerSong.copy(imageUrl = null)))
            Bar(FakeData.playbackLong)
            Box(Modifier.width(320.dp)) { Bar(FakeData.playbackLong) }
        }
    }

    @Test
    fun components_now_playing_bar_focused() = compose.captureTv(
        "components_now_playing_bar_focused",
        nowPlaying = FakeData.playbackLong,
        beforeCapture = { focusOnText(FakeData.playerSongLong.artistName) },
    ) {
        Box(Modifier.padding(48.dp), contentAlignment = Alignment.TopStart) {}
    }

    @Composable
    private fun Bar(state: PlaybackState) {
        NowPlayingBar(playbackState = state, onTap = {}, onTogglePlayPause = {})
    }

    @Composable
    private fun ListItems(long: Boolean) {
        val song = if (long) FakeData.longSongs[0] else FakeData.songs[0]
        val song2 = if (long) FakeData.longSongs[1] else FakeData.songs[1]
        val artist = if (long) FakeData.longArtists[0] else FakeData.artists[0]
        val album = if (long) FakeData.longAlbums[2] else FakeData.albums[0]
        val playlist = if (long) FakeData.longPlaylists[1] else FakeData.playlists[0]
        Column(
            Modifier.padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SongListItem(song = song, onClick = {})
            SongListItem(song = song2, onClick = {})
            ArtistListItem(artist = artist, onClick = {})
            AlbumListItem(album = album, onClick = {})
            PlaylistListItem(playlist = playlist, onClick = {})
        }
    }
}
