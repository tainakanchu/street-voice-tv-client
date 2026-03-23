import { writeFile, mkdir } from "node:fs/promises";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import type { ResponseDebugInfo } from "./types.js";

const __dirname = dirname(fileURLToPath(import.meta.url));
const DEBUG_DIR = join(__dirname, "..", "debug");

async function ensureDir(dir: string): Promise<void> {
  await mkdir(dir, { recursive: true });
}

/** レスポンスボディをデバッグ用に保存する */
export async function saveDebugResponse(
  label: string,
  body: string,
  ext = "json",
): Promise<string> {
  await ensureDir(DEBUG_DIR);
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const filename = `${label}_${timestamp}.${ext}`;
  const filepath = join(DEBUG_DIR, filename);
  await writeFile(filepath, body, "utf-8");
  console.log(`  [debug] saved: ${filepath}`);
  return filepath;
}

/** HTTP レスポンスのサマリを出力する */
export function summarizeResponse(res: Response): ResponseDebugInfo {
  const headers: Record<string, string> = {};
  res.headers.forEach((value, key) => {
    headers[key] = value;
  });
  return {
    url: res.url,
    status: res.status,
    statusText: res.statusText,
    headers,
    bodyPreview: "",
  };
}

/** エラー時のレスポンス情報を出力する */
export async function logFailedResponse(
  label: string,
  res: Response,
): Promise<void> {
  const info = summarizeResponse(res);
  const body = await res.text();
  info.bodyPreview = body.slice(0, 500);

  console.error(`\n[FAIL] ${label}`);
  console.error(`  status: ${info.status} ${info.statusText}`);
  console.error(`  url: ${info.url}`);
  console.error(`  headers:`, JSON.stringify(info.headers, null, 2));
  console.error(`  body preview: ${info.bodyPreview}`);

  await saveDebugResponse(`${label}_error`, JSON.stringify(info, null, 2));
}
