package com.example.streetvoicetv.di

import android.content.Context
import com.example.streetvoicetv.BuildConfig
import com.example.streetvoicetv.data.api.ApiConfig
import com.example.streetvoicetv.data.api.StreetVoiceApi
import com.example.streetvoicetv.data.auth.SessionCookieInterceptor
import com.example.streetvoicetv.data.repository.StreetVoiceRepositoryImpl
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val HTTP_CACHE_SIZE = 20L * 1024 * 1024

    @Provides
    @Singleton
    fun provideApiConfig(): ApiConfig {
        return ApiConfig(baseUrl = BuildConfig.STREETVOICE_BASE_URL)
    }

    @Provides
    @Singleton
    fun provideJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    /**
     * The app-wide client. SessionCookieInterceptor only adds the session to StreetVoice
     * requests, so derived clients (UpdateChecker) can safely talk to other hosts.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        @ApplicationContext context: Context,
        sessionCookieInterceptor: SessionCookieInterceptor,
    ): OkHttpClient {
        // Request lines (URLs incl. search queries) are only logged in debug builds.
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            // Honours the servers' Cache-Control / ETag headers (API responses, GitHub releases).
            .cache(Cache(File(context.cacheDir, "http"), HTTP_CACHE_SIZE))
            .addInterceptor(sessionCookieInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideStreetVoiceRepository(
        api: StreetVoiceApi,
    ): StreetVoiceRepository {
        return StreetVoiceRepositoryImpl(api)
    }
}
