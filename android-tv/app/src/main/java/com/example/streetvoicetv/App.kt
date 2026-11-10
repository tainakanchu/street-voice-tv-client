package com.example.streetvoicetv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

/**
 * Nothing playback related is created here: the player, MediaSession and preferences are
 * created lazily on first playback (PlaybackManager / PlaybackService), keeping startup light.
 */
@HiltAndroidApp
class App : Application(), ImageLoaderFactory {

    @Inject
    lateinit var httpClient: dagger.Lazy<OkHttpClient>

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
