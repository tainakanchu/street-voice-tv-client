/** 検索結果の1曲分のサマリ */
export interface SongSummary {
  songId: string;
  title: string;
  artist: string;
  artistId: string;
  songUrl: string;
  imageUrl?: string;
}

/** 曲の詳細情報 */
export interface SongDetail {
  songId: string;
  title: string;
  artist: string;
  artistId: string;
  album?: string;
  duration?: number;
  imageUrl?: string;
  songUrl: string;
  createdAt?: string;
  /** API から返却された生データ */
  raw: unknown;
}

/** 再生可能なストリーム情報 */
export interface PlayableStream {
  songId: string;
  url: string;
  format: "hls" | "mp3" | "unknown";
  expiresAt?: string;
  /** API から返却された生データ */
  raw: unknown;
}

/** 調査用リポジトリインターフェース */
export interface StreetVoiceRepository {
  searchSongs(query: string): Promise<SongSummary[]>;
  getSongDetail(songId: string): Promise<SongDetail>;
  getPlayableStream(songId: string): Promise<PlayableStream>;
}

/** HTTP レスポンスのデバッグ用サマリ */
export interface ResponseDebugInfo {
  url: string;
  status: number;
  statusText: string;
  headers: Record<string, string>;
  bodyPreview: string;
}
