package com.example.streetvoicetv.ui.player

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.PlaybackException
import org.robolectric.RuntimeEnvironment
import com.example.streetvoicetv.data.auth.SessionManager
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import com.example.streetvoicetv.playback.ManagerHarness
import com.example.streetvoicetv.playback.testSong
import com.example.streetvoicetv.playback.testSongs
import com.example.streetvoicetv.screenshot.SCREENSHOT_SDK
import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
import com.example.streetvoicetv.testutil.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Song detail / like endpoints, controllable per call; everything else from the shared fake. */
private class PlayerRepository(
    base: StreetVoiceRepository = FakeStreetVoiceRepository(),
) : StreetVoiceRepository by base {
    val likedIds = mutableSetOf<Int>()
    var gateDetails = false
    val detailCalls = mutableListOf<Int>()
    private val pendingDetails = mutableMapOf<Int, CompletableDeferred<Unit>>()
    var likeGate: CompletableDeferred<Result<Unit>>? = null
    val likeCalls = mutableListOf<String>()

    override suspend fun getSongDetail(songId: Int): Result<Song> {
        detailCalls += songId
        if (gateDetails) CompletableDeferred<Unit>().also { pendingDetails[songId] = it }.await()
        return Result.success(testSong(songId).copy(isLiked = songId in likedIds, lyrics = "lyrics $songId"))
    }

    fun completeDetail(songId: Int) = pendingDetails.remove(songId)?.complete(Unit)

    override suspend fun likeSong(songId: Int): Result<Unit> {
        likeCalls += "like($songId)"
        return likeGate?.await() ?: Result.success(Unit)
    }

    override suspend fun unlikeSong(songId: Int): Result<Unit> {
        likeCalls += "unlike($songId)"
        return likeGate?.await() ?: Result.success(Unit)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [SCREENSHOT_SDK], application = Application::class)
class PlayerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainRule = MainDispatcherRule(dispatcher)

    private val h = ManagerHarness(dispatcher)
    private val repo = PlayerRepository()
    private val session = SessionManager(RuntimeEnvironment.getApplication())

    private fun runCurrent() = dispatcher.scheduler.runCurrent()

    private fun viewModel(songId: Int) =
        PlayerViewModel(repo, h.manager, session, SavedStateHandle(mapOf("songId" to songId)))

    private fun queueAndOpen(songs: List<Song>, index: Int): PlayerViewModel {
        h.manager.setQueue(songs, index) // as MainActivity.playSongInList does
        val vm = viewModel(songs[index].id)
        runCurrent()
        h.engine.becomeReady()
        runCurrent()
        return vm
    }

    @Test
    fun `like status follows the current song, A then B then A`() {
        repo.likedIds += 1
        val vm = queueAndOpen(testSongs(2), 0)
        assertTrue(vm.isLiked.value)

        vm.skipNext()
        runCurrent()
        assertFalse(vm.isLiked.value)

        vm.skipPrevious()
        runCurrent()
        assertTrue("A's status is fetched again, not B's kept", vm.isLiked.value)
        assertEquals(listOf(1, 2, 1), repo.detailCalls)
    }

    @Test
    fun `a stale detail response is not applied to the next song`() {
        repo.likedIds += 1
        repo.gateDetails = true
        val vm = queueAndOpen(testSongs(2), 0)
        vm.skipNext()
        runCurrent()
        repo.completeDetail(2)
        runCurrent()
        repo.completeDetail(1) // cancelled request finishing late
        runCurrent()
        assertFalse(vm.isLiked.value)
        assertEquals("lyrics 2", h.manager.state.value.song?.lyrics)
    }

    @Test
    fun `queued song detail enriches the playing song without restarting it`() {
        queueAndOpen(testSongs(3), 1)
        assertEquals("lyrics 2", h.manager.state.value.song?.lyrics)
        assertEquals(1, h.engine.loads.size)
    }

    @Test
    fun `toggleLike is guarded while in flight and reverts on failure`() {
        val vm = queueAndOpen(testSongs(2), 0)
        val gate = CompletableDeferred<Result<Unit>>()
        repo.likeGate = gate

        vm.toggleLike()
        vm.toggleLike() // ignored: request in flight
        runCurrent()
        assertTrue(vm.isLiked.value) // optimistic
        assertEquals(listOf("like(1)"), repo.likeCalls)

        gate.complete(Result.failure(IllegalStateException("offline")))
        runCurrent()
        assertFalse(vm.isLiked.value)
    }

    @Test
    fun `a like response for the previous song does not mark the new song`() {
        val vm = queueAndOpen(testSongs(2), 0)
        val gate = CompletableDeferred<Result<Unit>>()
        repo.likeGate = gate
        vm.toggleLike()
        runCurrent()
        vm.skipNext()
        runCurrent()
        gate.complete(Result.success(Unit))
        runCurrent()
        assertFalse(vm.isLiked.value)
    }

    @Test
    fun `reopening the current song in an error state reloads it`() {
        queueAndOpen(testSongs(2), 0)
        h.engine.fail(PlaybackException.ERROR_CODE_DECODING_FAILED)
        runCurrent()
        assertEquals(1, h.engine.loads.size)

        viewModel(1)
        runCurrent()
        assertNull(h.manager.state.value.error)
        assertEquals(2, h.engine.loads.size)
    }

    @Test
    fun `upNext exposes the manager queue and playQueueItem jumps to a queued song`() {
        val vm = queueAndOpen(testSongs(4), 1)
        assertEquals(listOf(1, 2, 3, 4), vm.upNext.value.items.map { it.song.id })
        assertEquals(1, vm.upNext.value.currentPosition)
        assertEquals(3, vm.upNext.value.next?.song?.id)

        vm.playQueueItem(3)
        runCurrent()
        assertEquals(4, h.manager.state.value.song?.id)
        assertEquals(3, vm.upNext.value.currentPosition)
        assertEquals("details fetched for the new song", 4, repo.detailCalls.last())
        assertEquals("lyrics 4", vm.upNext.value.current?.song?.lyrics)
        assertEquals(2, h.engine.loads.size)
    }

    @Test
    fun `opening a song that is not queued loads it as a single-song queue`() {
        val vm = viewModel(7)
        runCurrent()
        assertTrue(vm.loadingState.value.isLoaded)
        assertEquals(7, h.manager.state.value.song?.id)
        assertEquals(1, h.manager.state.value.queueSize)
        assertEquals("detail fetched once", listOf(7), repo.detailCalls)
    }
}
