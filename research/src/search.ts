import { saveDebugResponse, logFailedResponse } from "./save-debug.js";
import type { SongSummary } from "./types.js";

const SEARCH_ENDPOINT = "https://streetvoice.com/api/v4/search/";

const DEFAULT_HEADERS: HeadersInit = {
  "User-Agent":
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
  Accept: "application/json",
  Referer: "https://streetvoice.com/",
};

interface SearchApiResponse {
  count: number;
  offset: number;
  limit: number;
  next: string | null;
  previous: string | null;
  results: SearchResultItem[];
}

interface SearchResultItem {
  id: number;
  name: string;
  image: string;
  user: {
    id: number;
    username: string;
    nickname: string;
    profile_image: string;
  };
  length?: number;
  genre?: number;
  plays_count?: number;
  likes_count?: number;
  comments_count?: number;
  [key: string]: unknown;
}

/** StreetVoice の検索 API を叩いて曲を検索する */
export async function searchSongs(
  query: string,
  limit = 10,
  offset = 0,
): Promise<SongSummary[]> {
  const url = new URL(SEARCH_ENDPOINT);
  url.searchParams.set("q", query);
  url.searchParams.set("type", "song");
  url.searchParams.set("limit", String(limit));
  url.searchParams.set("offset", String(offset));

  console.log(`\n[search] GET ${url.toString()}`);

  const res = await fetch(url.toString(), { headers: DEFAULT_HEADERS });

  if (!res.ok) {
    await logFailedResponse("search", res);
    throw new Error(`Search failed: ${res.status} ${res.statusText}`);
  }

  const body = await res.text();
  await saveDebugResponse("search", body);

  const data: SearchApiResponse = JSON.parse(body);
  console.log(
    `  found ${data.count} total results (showing ${data.results.length})`,
  );

  return data.results.map((item) => ({
    songId: String(item.id),
    title: item.name,
    artist: item.user.nickname || item.user.username,
    artistId: item.user.username,
    songUrl: `https://streetvoice.com/${item.user.username}/songs/${item.id}/`,
    imageUrl: item.image || undefined,
  }));
}
