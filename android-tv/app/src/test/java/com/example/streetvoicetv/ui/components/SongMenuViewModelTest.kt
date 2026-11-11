package com.example.streetvoicetv.ui.components

import android.app.Application
import android.content.Context
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.testutil.FakePlaybackActions
import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
import com.example.streetvoicetv.testutil.MainDispatcherRule
import com.example.streetvoicetv.ui.common.UserMessages
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SongMenuViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeStreetVoiceRepository()
    private val playback = FakePlaybackActions()
    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("song_menu_test", Context.MODE_PRIVATE)
        .also { it.edit().clear().commit() }
    private val session = SessionManager(prefs) {}
    private val vm = SongMenuViewModel(playback, repository, session)
    private val messages = mutableListOf<String>()

    init {
        TestScope(UnconfinedTestDispatcher()).launch { vm.messages.toList(messages) }
    }

    private val song = Song(1, "夜曲", "人", "artist", null, 200.0, 0)
    private val albumSong = song.copy(id = 2, albumId = 30)

    @Test
    fun `logged out - queue and navigation entries only, album only when known`() {
        vm.open(song)
        assertEquals(
            listOf(SongMenuAction.PlayNext, SongMenuAction.AddToQueue, SongMenuAction.GoToArtist),
            vm.menu.value!!.actions,
        )
        vm.open(albumSong)
        assertEquals(
            listOf(SongMenuAction.PlayNext, SongMenuAction.AddToQueue, SongMenuAction.GoToArtist, SongMenuAction.GoToAlbum),
            vm.menu.value!!.actions,
        )
    }

    @Test
    fun `play next and add to queue go to playback, close the menu and confirm`() {
        vm.open(song)
        vm.onAction(SongMenuAction.PlayNext)
        assertNull(vm.menu.value)
        vm.open(albumSong)
        vm.onAction(SongMenuAction.AddToQueue)
        assertNull(vm.menu.value)

        assertEquals(listOf("playNext(1)", "addToQueue(2)"), playback.calls)
        assertEquals(listOf("下一首播放：夜曲", "已加入佇列：夜曲"), messages)
    }

    @Test
    fun `navigation entries only close the menu`() {
        vm.open(albumSong)
        vm.onAction(SongMenuAction.GoToAlbum)
        assertNull(vm.menu.value)
        assertTrue(playback.calls.isEmpty())
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `the song detail fills in the album and the like state`() {
        session.saveSession("sid", "csrf", "me")
        repository.onSongDetail = { Result.success(song.copy(albumId = 40, albumName = "專輯", isLiked = true)) }
        vm.open(song)
        val menu = vm.menu.value!!
        assertEquals(40, menu.song.albumId)
        assertTrue(menu.isLiked)
        assertEquals(SongMenuAction.Unlike, menu.actions.last())
        assertTrue(SongMenuAction.GoToAlbum in menu.actions)
    }

    @Test
    fun `like is optimistic and remembered for the next menu`() {
        session.saveSession("sid", "csrf", "me")
        repository.onSongDetail = { Result.failure(RuntimeException("offline")) }
        vm.open(song)
        assertEquals(SongMenuAction.Like, vm.menu.value!!.actions.last())

        vm.onAction(SongMenuAction.Like)
        assertEquals(listOf("likeSong(1)"), repository.calls.filter { it.startsWith("like") })
        assertEquals(listOf("已加入喜歡：夜曲"), messages)
        assertTrue(vm.isLiked(song))

        vm.open(song)
        assertEquals(SongMenuAction.Unlike, vm.menu.value!!.actions.last())
        vm.onAction(SongMenuAction.Unlike)
        assertTrue("unlikeSong(1)" in repository.calls)
        assertFalse(vm.isLiked(song))
        assertEquals("已取消喜歡：夜曲", messages.last())
    }

    @Test
    fun `a failed like is reverted with an error message`() {
        session.saveSession("sid", "csrf", "me")
        val result = CompletableDeferred<Result<Unit>>()
        repository.onLike = { _, _ -> result.await() }
        vm.open(song.copy(albumId = 3))
        vm.onAction(SongMenuAction.Like)
        assertTrue(vm.isLiked(song))

        result.complete(Result.failure(RuntimeException("500")))
        assertFalse(vm.isLiked(song))
        assertEquals(UserMessages.LIKE_FAILED, messages.last())
    }

    @Test
    fun `dismiss closes without side effects`() {
        vm.open(song)
        vm.dismiss()
        assertNull(vm.menu.value)
        assertTrue(playback.calls.isEmpty())
    }
}
