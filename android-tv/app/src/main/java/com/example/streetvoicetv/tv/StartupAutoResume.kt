package com.example.streetvoicetv.tv

import com.example.streetvoicetv.playback.PlaybackManager
import com.example.streetvoicetv.playback.RestorableSession
import com.example.streetvoicetv.ui.settings.SettingsRepository
import com.example.streetvoicetv.ui.settings.StartupBehavior
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 啟動時：自動繼續上次播放. Once [restorable] arrives (it loads asynchronously after start-up),
 * resumes it playing — unless the setting is off, something is already loaded ([hasMedia]), or
 * nothing turns up within [timeoutMs]. Never navigates (Home stays on screen).
 * Returns true when it resumed.
 */
suspend fun autoResumeOnStartup(
    behavior: StartupBehavior,
    restorable: StateFlow<RestorableSession?>,
    hasMedia: () -> Boolean,
    resume: () -> Boolean,
    timeoutMs: Long = StartupAutoResume.WAIT_FOR_SESSION_MS,
): Boolean {
    if (behavior != StartupBehavior.RESUME_LAST || hasMedia()) return false
    withTimeoutOrNull(timeoutMs) { restorable.first { it != null } } ?: return false
    if (hasMedia()) return false // the user started something meanwhile
    return resume()
}

/** Runs [autoResumeOnStartup] at most once per process (activity re-creations do not repeat it). */
@Singleton
class StartupAutoResume @Inject constructor(
    private val settings: SettingsRepository,
    private val playbackManager: PlaybackManager,
) {
    private val done = AtomicBoolean(false)

    /** [skip]: the app was opened for something else that decides about playback (deep link). */
    suspend fun runOnce(skip: Boolean = false): Boolean {
        if (!done.compareAndSet(false, true) || skip) return false
        return autoResumeOnStartup(
            behavior = settings.settings.value.startupBehavior,
            restorable = playbackManager.restorable,
            hasMedia = { playbackManager.hasMedia },
            resume = { playbackManager.resumeLastSession(play = true) },
        )
    }

    companion object {
        const val WAIT_FOR_SESSION_MS = 10_000L
    }
}
