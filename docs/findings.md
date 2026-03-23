# Findings

## Search
- endpoint: `GET https://streetvoice.com/api/v4/search/?q={query}&type=song&limit={n}&offset={n}`
- method: GET
- auth required: No
- headers required: User-Agent, Accept: application/json, Referer
- response type: JSON
- pagination: `{ count, offset, limit, next, previous, results: [...] }`
- notes:
  - `type` パラメータで `song`, `user`, `playlist` を切替可能
  - 各結果に `id`, `name`, `image`, `user.username`, `user.nickname`, `length`, `plays_count` 等を含む
  - デフォルト limit=10
  - 検索キーワード「五月天」で 100 件ヒット確認

## Song Detail
- endpoint: `GET https://streetvoice.com/api/v5/song/{songId}/`
- method: GET
- auth required: No
- headers required: User-Agent, Accept: application/json, Referer
- response shape summary:
  ```
  {
    id, name, image, length, genre,
    synopsis, lyrics, lyrics_is_lrc,
    plays_count, likes_count, comments_count, share_count,
    publish_at, created_at, last_modified,
    public, draft, is_ai, exclusive,
    user: { id, username, nickname, profile_image },
    album: { id, name, image } | null,
    profit_config: { ... },
    achievements: { is_song_of_the_day, ... }
  }
  ```
- notes:
  - v4 と v5 両方使えるが v5 の方が `achievements`, `is_ai`, `exclusive` 等の追加フィールドあり
  - `length` は秒単位
  - ユーザー情報は user オブジェクトにネスト

## Stream URL
- endpoint: `POST https://streetvoice.com/api/v4/song/{songId}/hls/master/`
- method: POST (body: `{}`)
- auth required: No
- headers required: User-Agent, Accept: application/json, Referer, X-Requested-With: XMLHttpRequest
- response: `{ "file": "https://akhls.streetvoice.com/music/.../xxx.192khls.mplist.m3u8" }`
- url expiry: 署名パラメータなし、失効の兆候なし（要長期観察）
- playable in Media3: HLS (MP3 192kbps) - Media3 ExoPlayer 対応フォーマット
- notes:
  - POST メソッド必須（GET は 405）
  - CDN は `akhls.streetvoice.com`
  - Master playlist は single variant (192kbps MP3)
  - M3U8 は標準的な HLS マニフェスト (#EXTM3U, #EXT-X-VERSION:3)
  - CODEC: mp4a.40.34 (MP3)
  - `/api/v4/song/{songId}/hls/file/` でも取得可能（MP3 variant 直接）
  - `/api/v4/song/{songId}/file/` は認証必要 (403)

## Additional Useful Endpoints
- `GET /api/v5/chart/realtime/{style}/` - リアルタイムランキング
- `GET /api/v5/chart/weekly/{style}/` - 週間ランキング
- `GET /api/v4/user/{username}/songs/` - ユーザーの曲一覧
- `GET /api/v4/playlist/{id}/songs/` - プレイリスト曲一覧
- `GET /api/v4/song/genre/` - ジャンル一覧 (22種)
- `POST /api/v4/song/{songId}/play/` - 再生イベント記録

## Authentication
- Cookie なしで検索・詳細・HLS取得すべて可能
- CSRF token 不要
- ブラウザセッション不要
- ログイン: `POST /api/v4/auth/signin/` (今回は不要)

## Risks
- 内部 API のため予告なく変更される可能性
- Rate limit の明示的ヘッダはないが、過度なリクエストには注意
- HLS URL の失効期間は未検証（長期観察が必要）
- API v4 の一部機能は v5 に移行中（chart/realtime は v4 で停止済み）
