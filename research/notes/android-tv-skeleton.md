# Android TV PoC Skeleton - Task Notes

## Task Overview
Create a minimal Android TV app skeleton for StreetVoice music player PoC.

## Tech Stack
- Kotlin 2.0.21 + Jetpack Compose for TV 1.0.0 + Media3 ExoPlayer 1.5.1
- Hilt 2.53.1 DI, kotlinx.serialization 1.7.3, OkHttp 4.12.0
- Repository pattern with DataSource layer
- Compose BOM 2024.12.01, AGP 8.5.2, Gradle 8.9

## Key Decisions
- Using OkHttp over Ktor for HTTP (simpler setup, well-tested on Android)
- kotlinx.serialization for JSON (Kotlin-native, no reflection)
- Compose for TV (androidx.tv.compose) for Leanback-compatible UI
- DPAD navigation support via Compose TV focus system + onKeyEvent
- KSP for Hilt annotation processing (not kapt)
- Base URL configurable via BuildConfig field
- HLS playback with DefaultHttpDataSource + custom headers (Referer, User-Agent)

## Progress
- [x] Create Gradle build files (project, app, version catalog, settings)
- [x] Create API models (SearchResponse, SongDetailResponse, StreamResponse)
- [x] Create domain models (Song, PlayableStream)
- [x] Create API client (StreetVoiceApi)
- [x] Create repository interface + implementation
- [x] Create DI module (Hilt)
- [x] Create UI screens (Search, Player)
- [x] Create ViewModels
- [x] Create theme, components
- [x] Create AndroidManifest, resources
- [x] Create Application class, MainActivity

## File Count: 30 files total
