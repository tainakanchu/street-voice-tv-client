package com.example.streetvoicetv.di

import com.example.streetvoicetv.BuildConfig
import com.example.streetvoicetv.data.api.ApiConfig
import com.example.streetvoicetv.data.api.StreetVoiceApi
import com.example.streetvoicetv.data.repository.StreetVoiceRepositoryImpl
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

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

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideStreetVoiceApi(
        client: OkHttpClient,
        json: Json,
        config: ApiConfig,
    ): StreetVoiceApi {
        return StreetVoiceApi(client, json, config)
    }

    @Provides
    @Singleton
    fun provideStreetVoiceRepository(
        api: StreetVoiceApi,
    ): StreetVoiceRepository {
        return StreetVoiceRepositoryImpl(api)
    }
}
