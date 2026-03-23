import { searchSongs } from "./search.js";
import { getSongDetail } from "./song.js";
import { getPlayableStream, verifyHlsUrl } from "./stream.js";

const command = process.argv[2] || "all";
const query = process.argv[3] || "五月天";

async function runSearch(q: string) {
  console.log("=== Search ===");
  const results = await searchSongs(q);
  for (const song of results) {
    console.log(`  [${song.songId}] ${song.artist} - ${song.title}`);
  }
  return results;
}

async function runSongDetail(songId: string) {
  console.log("\n=== Song Detail ===");
  const detail = await getSongDetail(songId);
  console.log(`  title: ${detail.title}`);
  console.log(`  artist: ${detail.artist}`);
  console.log(`  album: ${detail.album ?? "(none)"}`);
  console.log(`  duration: ${detail.duration}s`);
  return detail;
}

async function runStream(songId: string) {
  console.log("\n=== Stream URL ===");
  const stream = await getPlayableStream(songId);
  console.log(`  url: ${stream.url}`);
  console.log(`  format: ${stream.format}`);

  console.log("\n=== Verify HLS ===");
  const valid = await verifyHlsUrl(stream.url);
  console.log(`  HLS valid: ${valid}`);

  return stream;
}

async function runAll(q: string) {
  const results = await runSearch(q);
  if (results.length === 0) {
    console.log("No results found.");
    return;
  }

  const firstSong = results[0];
  console.log(`\nUsing first result: [${firstSong.songId}] ${firstSong.title}`);

  await runSongDetail(firstSong.songId);
  await runStream(firstSong.songId);

  console.log("\n=== Done ===");
  console.log("Full flow: search -> detail -> stream -> verify completed.");
}

try {
  switch (command) {
    case "search":
      await runSearch(query);
      break;
    case "song":
      await runSongDetail(query);
      break;
    case "stream":
      await runStream(query);
      break;
    case "all":
    default:
      await runAll(query);
      break;
  }
} catch (error) {
  console.error("\n[ERROR]", error);
  process.exit(1);
}
