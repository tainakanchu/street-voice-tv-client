package com.example.streetvoicetv.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.streetvoicetv.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hosts the MediaSession so playback survives the activity (screensaver / home) as a
 * `mediaPlayback` foreground service. Media3 promotes it to the foreground and shows the media
 * notification while playing. [PlaybackManager] starts it when a song starts loading and stops
 * it from [PlaybackManager.stop]. The player itself belongs to the [PlaybackManager] singleton.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var playbackManager: PlaybackManager

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, playbackManager.sessionPlayer)
            .setSessionActivity(openApp)
            .build()
            .also { addSession(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Restarted by the system after the process died: nothing to play, don't linger.
        if (intent == null && !playbackManager.hasMedia) {
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** Stay in the foreground between songs and during automatic retries (the player is briefly idle). */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired || playbackManager.isPlaybackActive)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!playbackManager.isPlaybackActive) {
            playbackManager.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            try {
                context.startService(Intent(context, PlaybackService::class.java))
            } catch (e: IllegalStateException) {
                // Not allowed from the background; an already running service keeps the session.
                Log.w("PlaybackService", "Could not start playback service", e)
            }
        }
    }
}
