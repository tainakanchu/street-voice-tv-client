import { saveDebugResponse, logFailedResponse } from "./save-debug.js";
import type { PlayableStream } from "./types.js";

const HLS_MASTER_ENDPOINT = "https://streetvoice.com/api/v4/song";

const DEFAULT_HEADERS: HeadersInit = {
  "User-Agent":
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
  Accept: "application/json",
  Referer: "https://streetvoice.com/",
  "X-Requested-With": "XMLHttpRequest",
};

interface HlsApiResponse {
  file: string;
}

/** song id から HLS master playlist URL を取得する */
export async function getPlayableStream(
  songId: string,
): Promise<PlayableStream> {
  const url = `${HLS_MASTER_ENDPOINT}/${songId}/hls/master/`;
  console.log(`\n[stream] POST ${url}`);

  const res = await fetch(url, {
    method: "POST",
    headers: {
      ...DEFAULT_HEADERS,
      "Content-Type": "application/json",
    },
    body: "{}",
  });

  if (!res.ok) {
    await logFailedResponse("stream", res);
    throw new Error(`Stream URL failed: ${res.status} ${res.statusText}`);
  }

  const body = await res.text();
  await saveDebugResponse("stream", body);

  const data: HlsApiResponse = JSON.parse(body);
  console.log(`  HLS URL: ${data.file}`);

  // URL の形式から format を判定
  const format = data.file.includes(".m3u8") ? "hls" : "unknown";

  return {
    songId,
    url: data.file,
    format,
    raw: data,
  };
}

/** HLS URL が実際にアクセス可能か検証する */
export async function verifyHlsUrl(hlsUrl: string): Promise<boolean> {
  console.log(`\n[verify-hls] GET ${hlsUrl}`);

  const res = await fetch(hlsUrl, {
    headers: {
      "User-Agent":
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36",
    },
  });

  const body = await res.text();
  await saveDebugResponse("hls-manifest", body, "m3u8");

  if (!res.ok) {
    console.error(`  HLS URL not accessible: ${res.status}`);
    return false;
  }

  const contentType = res.headers.get("content-type") || "";
  console.log(`  status: ${res.status}`);
  console.log(`  content-type: ${contentType}`);
  console.log(`  body preview: ${body.slice(0, 200)}`);

  return body.includes("#EXTM3U");
}
