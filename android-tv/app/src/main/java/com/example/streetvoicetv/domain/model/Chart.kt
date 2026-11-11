package com.example.streetvoicetv.domain.model

/** A song genre of `/api/v4/song/genre/` (id 0 = 未分類). */
data class Genre(
    val id: Int,
    val name: String,
)

/** Chart period: the path segment of `/api/v5/chart/{period}/{style}/`. */
enum class ChartPeriod(val pathSegment: String, val label: String) {
    Realtime("realtime", "即時"),
    Weekly("weekly", "週間"),
    ;

    companion object {
        fun fromPathSegment(value: String?): ChartPeriod =
            entries.firstOrNull { it.pathSegment == value } ?: Realtime
    }
}

/**
 * A chart category ("style"). The chart API does not take the 22 song genres: it serves a
 * fixed set of style charts (the same tabs as https://streetvoice.com/music/charts/), and a
 * `?genre=` query parameter is ignored.
 *
 * @property slug path segment of `/api/v5/chart/{period}/{slug}/`.
 * @property realtimeOnly the weekly chart of this style does not exist (404).
 */
data class ChartCategory(
    val slug: String,
    val label: String,
    val realtimeOnly: Boolean = false,
) {
    fun availableFor(period: ChartPeriod): Boolean = !realtimeOnly || period == ChartPeriod.Realtime

    companion object {
        const val ALL_SLUG = "all"

        /** In the web chart page's order (verified 2026-11; AI 生成 has no weekly chart). */
        val ALL: List<ChartCategory> = listOf(
            ChartCategory(ALL_SLUG, "全部"),
            ChartCategory("rock", "搖滾"),
            ChartCategory("folk", "民謠"),
            ChartCategory("hip_hop", "嘻哈"),
            ChartCategory("urban", "都會"),
            ChartCategory("electronic", "電子"),
            ChartCategory("explore", "探索"),
            ChartCategory("ai_generated", "AI 生成", realtimeOnly = true),
        )

        fun forPeriod(period: ChartPeriod): List<ChartCategory> = ALL.filter { it.availableFor(period) }

        fun bySlug(slug: String?): ChartCategory = ALL.firstOrNull { it.slug == slug } ?: ALL.first()

        /** The server stops a chart after this many entries. */
        const val MAX_ENTRIES = 50
    }
}
