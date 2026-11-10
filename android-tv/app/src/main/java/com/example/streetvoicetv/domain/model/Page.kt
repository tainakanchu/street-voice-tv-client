package com.example.streetvoicetv.domain.model

/**
 * One page of an offset-paginated list.
 *
 * @property total total number of items the server reports (`count`), if known. The search
 *   endpoints cap it at 100 regardless of the real number of hits.
 * @property nextOffset offset to request the next page with, or null when this is the last page.
 */
data class Page<T>(
    val items: List<T>,
    val total: Int?,
    val nextOffset: Int?,
) {
    val hasMore: Boolean get() = nextOffset != null

    fun <R> map(transform: (T) -> R): Page<R> = Page(items.map(transform), total, nextOffset)
}

/** Appends [more] to this list, skipping items whose [key] is already present. */
fun <T, K> List<T>.appendDistinctBy(more: List<T>, key: (T) -> K): List<T> {
    if (more.isEmpty()) return this
    val seen = HashSet<K>(size + more.size)
    forEach { seen += key(it) }
    val added = more.filter { seen.add(key(it)) }
    return if (added.isEmpty()) this else this + added
}
