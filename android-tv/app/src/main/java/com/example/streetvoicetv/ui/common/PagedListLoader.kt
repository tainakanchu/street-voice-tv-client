package com.example.streetvoicetv.ui.common

import com.example.streetvoicetv.domain.model.Page
import com.example.streetvoicetv.domain.model.appendDistinctBy
import com.example.streetvoicetv.domain.repository.StreetVoiceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** State of the "load more" footer of a paginated list, as shown by the UI. */
data class LoadMoreState(
    val isLoadingMore: Boolean = false,
    /** True when there is nothing more to load (the default: a list that is not paginated). */
    val endReached: Boolean = true,
    /** User-facing message of the last failed load-more; the footer then offers 重試. */
    val error: String? = null,
    /** Total number of items the server reports, if known (may exceed what is loaded). */
    val total: Int? = null,
) {
    val canLoadMore: Boolean get() = !endReached && !isLoadingMore && error == null
}

/**
 * Most songs 播放全部 / 隨機播放 load before starting (pages are fetched up to this many;
 * songs already loaded by scrolling are all kept).
 */
const val PLAY_ALL_CAP = 200

/** Which header button started playback ([PlayAllMode.Shuffle] = 隨機播放). */
enum class PlayAllMode { InOrder, Shuffle }

/**
 * Keeps the loaded pages of one offset-paginated list for a ViewModel.
 *
 * - The first page is fetched by the ViewModel's own load (usually alongside other requests)
 *   and handed over with [reset], which also cancels any load-more still in flight, so results
 *   of a superseded list (retry, new search) can never be appended.
 * - [loadMore] fetches the next page; at most one load runs at a time and calls while one is
 *   running, after the last page or after a failure (until [retryLoadMore]) are ignored.
 * - Items are de-duplicated by [key] (pages can shift when the list changes on the server).
 *
 * Every change is reported through [onChange]. Must be used from the main thread.
 */
class PagedListLoader<T>(
    private val scope: CoroutineScope,
    private val tag: String,
    private val pageSize: Int = StreetVoiceRepository.DEFAULT_PAGE_SIZE,
    private val key: (T) -> Any,
    private val fetch: suspend (offset: Int, limit: Int) -> Result<Page<T>>,
    private val onChange: (items: List<T>, loadMore: LoadMoreState) -> Unit,
) {
    var items: List<T> = emptyList()
        private set
    private var nextOffset: Int? = null
    private var total: Int? = null
    private var isLoadingMore = false
    private var error: String? = null
    private var job: Job? = null

    val state: LoadMoreState
        get() = LoadMoreState(isLoadingMore = isLoadingMore, endReached = nextOffset == null, error = error, total = total)

    /** Fetches the first page (does not change the state; pass the result to [reset]). */
    suspend fun fetchFirst(): Result<Page<T>> = fetch(0, pageSize)

    /** Replaces the list with [first] (null: empty list without further pages). */
    fun reset(first: Page<T>?) {
        job?.cancel()
        job = null
        items = if (first == null) emptyList() else emptyList<T>().appendDistinctBy(first.items, key)
        nextOffset = first?.nextOffset
        total = first?.total
        isLoadingMore = false
        error = null
        publish()
    }

    /** Loads the next page, unless a load is running, the end was reached or the last one failed. */
    fun loadMore() {
        if (error != null) return
        startLoadMore()
    }

    /** Retries after a failed [loadMore]. */
    fun retryLoadMore() {
        error = null
        startLoadMore()
    }

    private fun startLoadMore() {
        if (job?.isActive == true) return
        val offset = nextOffset ?: return
        isLoadingMore = true
        error = null
        publish()
        job = scope.launch {
            val result = fetch(offset, pageSize)
            ensureActive()
            isLoadingMore = false
            result
                .onSuccess { append(it) }
                .onFailure { error = userMessageFor(tag, it) }
            publish()
        }
    }

    /**
     * Loads further pages until at least [cap] items are loaded or the list ends, then calls
     * [onDone] with all loaded items (also when a page fails: playing what is there beats
     * playing nothing; the footer then offers to retry). Waits for a load-more in flight first.
     * [onDone] is not called when the list is [reset] (or the scope cancelled) meanwhile.
     */
    fun loadUpTo(cap: Int, onDone: (List<T>) -> Unit) {
        if (nextOffset == null || items.size >= cap) {
            onDone(items)
            return
        }
        val previous = job
        job = scope.launch {
            previous?.join()
            error = null
            while (items.size < cap) {
                val offset = nextOffset ?: break
                isLoadingMore = true
                publish()
                val limit = (cap - items.size).coerceIn(pageSize, StreetVoiceRepository.MAX_PAGE_SIZE)
                val result = fetch(offset, limit)
                ensureActive()
                isLoadingMore = false
                val page = result.getOrElse {
                    error = userMessageFor(tag, it)
                    null
                } ?: break
                append(page)
            }
            isLoadingMore = false
            publish()
            onDone(items)
        }
    }

    private fun append(page: Page<T>) {
        items = items.appendDistinctBy(page.items, key)
        total = page.total ?: total
        // A page without new items cannot advance the list: treat it as the end.
        nextOffset = page.nextOffset.takeIf { page.items.isNotEmpty() }
    }

    private fun publish() = onChange(items, state)
}
