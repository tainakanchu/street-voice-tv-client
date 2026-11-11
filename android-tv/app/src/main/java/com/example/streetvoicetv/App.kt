package com.example.streetvoicetv

import android.app.Activity
import android.app.Application
import android.os.Bundle
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.example.streetvoicetv.playback.PlaybackManager
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Nothing playback related is created here: the player, MediaSession and preferences are
 * created lazily on first playback (PlaybackManager / PlaybackService), keeping startup light.
 * When an activity stops (app to the background) the playback session is saved (on the IO
 * dispatcher) so it can be restored next time.
 */
@HiltAndroidApp
class App : Application(), ImageLoaderFactory {

    @Inject
    lateinit var httpClient: dagger.Lazy<OkHttpClient>

    @Inject
    lateinit var playbackManager: dagger.Lazy<PlaybackManager>

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStopped(activity: Activity) = playbackManager.get().saveSessionNow()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                // Share the connection pool / dispatcher, but images must not carry the API
                // session cookie or the request logging (Coil's own client sent neither).
                httpClient.get().newBuilder().apply { interceptors().clear() }.build()
            }
            .allowRgb565(true)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
            .build()
}
