# StreetVoice TV Client

在 Google TV / Android TV 上收聽 [StreetVoice](https://streetvoice.com) 音樂的非官方客戶端。

An unofficial client for listening to [StreetVoice](https://streetvoice.com) music on Google TV / Android TV.

## 功能 Features

- 🔍 **搜尋歌曲與音樂人** — Search songs and artists
- 🎵 **HLS 串流播放** — HLS streaming playback via Media3 ExoPlayer
- 📋 **播放佇列與自動下一首** — Queue management with auto-play next
- 🏠 **首頁推薦** — Home screen with realtime chart, editor's choice, playlists
- 👤 **音樂人頁面** — Artist page with songs and albums
- 💿 **專輯與播放清單** — Album and playlist browsing
- 🔑 **WebView 登入** — Login via WebView (email/password)
- 🎨 **背景模糊封面** — Blurred album art background on player screen
- 📺 **遙控器支援** — DPAD remote control + MediaSession for play/pause keys
- 🔒 **螢幕常亮** — Screen stays on during playback
- 📊 **播放回報** — Play count reported back to StreetVoice

## 螢幕截圖 Screenshots

```
首頁 Home → 搜尋 Search → 音樂人 Artist → 專輯 Album → 播放器 Player
                                              ↓
                                        播放清單 Playlist
```

## 技術架構 Tech Stack

| 層 Layer | 技術 Technology |
|----------|----------------|
| UI | Kotlin, Jetpack Compose for TV, Material3 |
| 播放 Playback | Media3 ExoPlayer (HLS), MediaSession |
| 網路 Network | OkHttp, kotlinx.serialization |
| DI | Hilt |
| 導航 Navigation | Navigation Compose |
| 圖片 Image | Coil |

## 專案結構 Project Structure

```
├── android-tv/          # Android TV 應用程式 (Kotlin)
│   └── app/src/main/java/com/example/streetvoicetv/
│       ├── data/        # API 呼叫、資料模型、Repository 實作
│       ├── domain/      # 領域模型與 Repository 介面
│       ├── di/          # Hilt DI 模組
│       ├── playback/    # PlaybackManager (ExoPlayer + MediaSession)
│       └── ui/          # Compose 畫面 (home, search, artist, album, player, playlist, login)
├── research/            # API 調查用 TypeScript 腳本
├── docs/                # API 調查結果與風險評估
└── flake.nix            # Nix 開發環境定義
```

## 開始使用 Getting Started

### 前置需求 Prerequisites

- [Nix](https://nixos.org/download/) (推薦 recommended) 或 or:
  - JDK 17
  - Android SDK (Platform 35, Build Tools 35)
  - Node.js 22+ (僅調查腳本 for research scripts only)

### 使用 Nix

```bash
# 啟動開發環境
nix develop

# 或使用 direnv 自動載入
direnv allow
```

### 建置 Build

```bash
cd android-tv

# Debug 版本
./gradlew assembleDebug

# Release 版本 (minify + shrink, ~4MB)
./gradlew assembleRelease
```

### 安裝到裝置 Install

```bash
# Debug
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Release
adb install -r app/build/outputs/apk/release/app-release.apk
```

### 調查腳本 Research Scripts

```bash
cd research
npm install
npm start              # 執行完整流程 (搜尋 → 詳情 → 串流)
npm run search -- 五月天  # 搜尋
```

## API 端點 API Endpoints

所有端點皆不需要登入即可使用。All endpoints work without authentication.

| 端點 Endpoint | 說明 Description |
|---------------|------------------|
| `GET /api/v4/search/?q={q}&type=song` | 搜尋歌曲 Search songs |
| `GET /api/v5/song/{id}/` | 歌曲詳情 Song detail |
| `POST /api/v4/song/{id}/hls/master/` | 取得 HLS 串流網址 Get HLS stream URL |
| `POST /api/v4/song/{id}/play/` | 回報播放 Report play event |
| `GET /api/v4/search/?q={q}&type=user` | 搜尋音樂人 Search artists |
| `GET /api/v4/user/{username}/songs/` | 音樂人的歌曲 Artist's songs |
| `GET /api/v4/user/{username}/albums/` | 音樂人的專輯 Artist's albums |
| `GET /api/v4/album/{id}/songs/` | 專輯歌曲 Album songs |
| `GET /api/v5/chart/realtime/all/` | 即時排行榜 Realtime chart |
| `GET /api/v4/editor_choice/` | 編輯推薦 Editor's choice |
| `GET /api/v4/playlist/{id}/songs/` | 播放清單歌曲 Playlist songs |

## 注意事項 Notes

- 本專案僅供個人使用，不作公開發佈。This project is for personal use only, not for public distribution.
- StreetVoice 的 API 為非公開內部 API，可能隨時變更。StreetVoice APIs are internal and may change without notice.
- HLS 串流為 MP3 192kbps。HLS streams are MP3 at 192kbps.

## 授權 License

[MIT](LICENSE)
