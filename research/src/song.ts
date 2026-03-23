import { saveDebugResponse, logFailedResponse } from "./save-debug.js";
import type { SongDetail } from "./types.js";

const SONG_DETAIL_ENDPOINT = "https://streetvoice.com/api/v5/song";

const DEFAULT_HEADERS: HeadersInit = {
  "User-Agent":
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
  Accept: "application/json",
  Referer: "https://streetvoice.com/",
};

interface SongDetailApiResponse {
  id: number;
  name: string;
  image: string;
  length: number;
  genre: number;
  synopsis: string;
  lyrics: string;
  plays_count: number;
  likes_count: number;
  comments_count: number;
  publish_at: string;
  created_at: string;
  user: {
    id: number;
    username: string;
    nickname: string;
    profile_image: string;
  };
  album: {
    id: number;
    name: string;
    image: string;
  } | null;
  [key: string]: unknown;
}

/** song id から曲詳細を取得する */
export async function getSongDetail(songId: string): Promise<SongDetail> {
  const url = `${SONG_DETAIL_ENDPOINT}/${songId}/`;
  console.log(`\n[song-detail] GET ${url}`);

  const res = await fetch(url, { headers: DEFAULT_HEADERS });

  if (!res.ok) {
    await logFailedResponse("song-detail", res);
    throw new Error(`Song detail failed: ${res.status} ${res.statusText}`);
  }

  const body = await res.text();
  await saveDebugResponse("song-detail", body);

  const data: SongDetailApiResponse = JSON.parse(body);
  console.log(`  title: ${data.name}`);
  console.log(`  artist: ${data.user.nickname || data.user.username}`);
  console.log(`  duration: ${data.length}s`);
  console.log(`  plays: ${data.plays_count}`);

  return {
    songId: String(data.id),
    title: data.name,
    artist: data.user.nickname || data.user.username,
    artistId: data.user.username,
    album: data.album?.name,
    duration: data.length,
    imageUrl: data.image || undefined,
    songUrl: `https://streetvoice.com/${data.user.username}/songs/${data.id}/`,
    createdAt: data.created_at,
    raw: data,
  };
}
