package com.example.streetvoicetv

import android.app.Application
import com.example.streetvoicetv.playback.PlaybackManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application() {

    @Inject
    lateinit var playbackManager: PlaybackManager

    override fun onCreate() {
        super.onCreate()
        // MediaSession を早期に初期化して TV リモコンキーを受け取れるようにする
        playbackManager.mediaSession
    }

    override fun onTerminate() {
        super.onTerminate()
        playbackManager.release()
    }
}
