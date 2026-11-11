package com.example.streetvoicetv.tv

import com.example.streetvoicetv.playback.QueueSnapshot
import com.example.streetvoicetv.playback.RestorableSession
import com.example.streetvoicetv.playback.testSongs
import com.example.streetvoicetv.ui.settings.StartupBehavior
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 啟動時：自動繼續上次播放 and the Watch Next resume deep link. */
@OptIn(ExperimentalCoroutinesApi::class)
class StartupAutoResumeTest {

    private val session = RestorableSession(QueueSnapshot(testSongs(3), currentIndex = 2, shuffleEnabled = false, shuffleOrder = emptyList(), shufflePosition = -1, autoplayFlags = emptyList()), positionMs = 1_000, savedAtMs = 0)

    @Test
    fun `resumes once the saved session has loaded`() = runTest {
        val restorable = MutableStateFlow<RestorableSession?>(null)
        var resumed = 0
        val result = async {
            autoResumeOnStartup(StartupBehavior.RESUME_LAST, restorable, hasMedia = { false }, resume = { resumed++; true })
        }
        runCurrent()
        assertEquals(0, resumed)
        restorable.value = session
        runCurrent()
        assertTrue(result.await())
        assertEquals(1, resumed)
    }

    @Test
    fun `does nothing when the setting shows home`() = runTest {
        var resumed = 0
        val result = autoResumeOnStartup(
            StartupBehavior.SHOW_HOME,
            MutableStateFlow(session),
            hasMedia = { false },
            resume = { resumed++; true },
        )
        assertFalse(result)
        assertEquals(0, resumed)
    }

    @Test
    fun `does not replace something the user started`() = runTest {
        var hasMedia = false
        val restorable = MutableStateFlow<RestorableSession?>(null)
        var resumed = 0
        val result = async {
            autoResumeOnStartup(StartupBehavior.RESUME_LAST, restorable, hasMedia = { hasMedia }, resume = { resumed++; true })
        }
        runCurrent()
        hasMedia = true
        restorable.value = session
        runCurrent()
        assertFalse(result.await())
        assertEquals(0, resumed)

        assertFalse(autoResumeOnStartup(StartupBehavior.RESUME_LAST, MutableStateFlow(session), { true }, { resumed++; true }))
        assertEquals(0, resumed)
    }

    @Test
    fun `gives up when no session turns up`() = runTest {
        var resumed = 0
        val result = async {
            autoResumeOnStartup(StartupBehavior.RESUME_LAST, MutableStateFlow(null), { false }, { resumed++; true }, timeoutMs = 5_000)
        }
        advanceTimeBy(5_001)
        assertFalse(result.await())
        assertEquals(0, resumed)
    }

    // --- resume deep link ---------------------------------------------------------------------

    @Test
    fun `deep link plays the loaded song and opens it`() = runTest {
        var played = 0
        val id = resumeTargetSongId({ 5 }, MutableStateFlow(null), playCurrent = { played++ }, resumeLastSession = { error("no") })
        assertEquals(5, id)
        assertEquals(1, played)
    }

    @Test
    fun `deep link waits for the saved session and resumes it`() = runTest {
        val restorable = MutableStateFlow<RestorableSession?>(null)
        var resumed = 0
        val id = async { resumeTargetSongId({ null }, restorable, {}, { resumed++; true }) }
        runCurrent()
        restorable.value = session
        runCurrent()
        assertEquals(3, id.await())
        assertEquals(1, resumed)
    }

    @Test
    fun `deep link with nothing to resume opens nothing`() = runTest {
        val id = async { resumeTargetSongId({ null }, MutableStateFlow(null), {}, { true }, timeoutMs = 1_000) }
        advanceTimeBy(1_001)
        assertNull(id.await())
        assertNull(resumeTargetSongId({ null }, MutableStateFlow(session), {}, { false }))
    }
}
