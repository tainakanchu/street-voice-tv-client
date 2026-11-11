package com.example.streetvoicetv.playback

import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random

/**
 * Finds songs to keep playing after the queue ends (自動播放). The server has no related-songs
 * API, so implementations combine what the repository offers.
 *
 * Contract: return at most [count] songs, none of whose ids are in [exclude], without
 * duplicates; return an empty list (do not throw) when nothing can be found. Called on the
 * main thread from [PlaybackManager]; do network work in suspend repository calls.
 */
fun interface RadioSource {
    suspend fun nextSongs(seed: Song, exclude: Set<Int>, count: Int): List<Song>
}

/** Songs by the seed's artist (first page of their song list). */
class ArtistRadioSource(
    private val repository: StreetVoiceRepository,
    private val random: Random = Random.Default,
) : RadioSource {
    override suspend fun nextSongs(seed: Song, exclude: Set<Int>, count: Int): List<Song> {
        if (seed.artistUsername.isBlank()) return emptyList()
        val page = repository.getArtistSongs(seed.artistUsername, limit = ARTIST_POOL).getOrNull()
            ?: return emptyList()
        return page.items.filter { it.id !in exclude }.distinctBy { it.id }.shuffled(random).take(count)
    }

    private companion object {
        const val ARTIST_POOL = 50
    }
}

/** Songs from the realtime chart (即時排行). */
class ChartRadioSource(
    private val repository: StreetVoiceRepository,
    private val random: Random = Random.Default,
) : RadioSource {
    override suspend fun nextSongs(seed: Song, exclude: Set<Int>, count: Int): List<Song> {
        val chart = repository.getRealtimeChart(limit = CHART_POOL).getOrNull() ?: return emptyList()
        return chart.filter { it.id !in exclude }.distinctBy { it.id }.shuffled(random).take(count)
    }

    private companion object {
        const val CHART_POOL = 50
    }
}

/**
 * Mixes several sources: each part is asked (concurrently) for its share of [count]
 * (by [weights]), the results are interleaved, de-duplicated and shuffled, and parts that come
 * up short are topped up from the others' extras. A failing part is skipped.
 *
 * Plug in more sources (e.g. a genre chart) by adding a part: `MixedRadioSource(listOf(
 * ArtistRadioSource(repo) to 1, GenreRadioSource(repo) to 1, ChartRadioSource(repo) to 1))`.
 */
class MixedRadioSource(
    private val parts: List<Pair<RadioSource, Int>>,
    private val random: Random = Random.Default,
) : RadioSource {

    override suspend fun nextSongs(seed: Song, exclude: Set<Int>, count: Int): List<Song> {
        if (count <= 0 || parts.isEmpty()) return emptyList()
        val totalWeight = parts.sumOf { it.second.coerceAtLeast(1) }
        // Ask every part for the full count so shortfalls can be filled from the others.
        val results = coroutineScope {
            parts.map { (source, _) ->
                async {
                    try {
                        source.nextSongs(seed, exclude, count)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll()
        }
        val picked = LinkedHashMap<Int, Song>()
        // Each part's share first ...
        results.forEachIndexed { i, songs ->
            val share = (count * parts[i].second.coerceAtLeast(1) + totalWeight - 1) / totalWeight
            songs.asSequence().filter { it.id !in exclude && it.id !in picked }.take(share)
                .forEach { picked[it.id] = it }
        }
        // ... then top up from whatever is left.
        results.flatten().forEach { if (picked.size < count && it.id !in exclude) picked.putIfAbsent(it.id, it) }
        return picked.values.take(count).shuffled(random)
    }

    companion object {
        /** Same artist + realtime chart, half and half. */
        fun default(repository: StreetVoiceRepository): RadioSource = MixedRadioSource(
            listOf(ArtistRadioSource(repository) to 1, ChartRadioSource(repository) to 1),
        )
    }
}
