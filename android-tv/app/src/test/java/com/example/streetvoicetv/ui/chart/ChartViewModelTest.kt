package com.example.streetvoicetv.ui.chart

import androidx.lifecycle.SavedStateHandle
import com.example.streetvoicetv.domain.model.ChartCategory
import com.example.streetvoicetv.domain.model.ChartPeriod
import com.example.streetvoicetv.domain.model.Page
import com.example.streetvoicetv.domain.model.Song
import com.example.streetvoicetv.testutil.FakeStreetVoiceRepository
import com.example.streetvoicetv.testutil.MainDispatcherRule
import com.example.streetvoicetv.testutil.pagesOf
import com.example.streetvoicetv.ui.common.PlayAllMode
import com.example.streetvoicetv.ui.common.UserMessages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChartViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeStreetVoiceRepository()

    private fun songs(prefix: Int, count: Int) =
        (1..count).map { Song(prefix * 1000 + it, "s$it", "a", "a", null, 100.0, 0) }

    /** Every chart has 50 songs whose ids encode the chart. */
    private val charts = mutableMapOf<Pair<String, ChartPeriod>, List<Song>>()

    private fun chart(slug: String, period: ChartPeriod): List<Song> =
        charts.getOrPut(slug to period) { songs(charts.size + 1, 50) }

    init {
        repository.onChartPage = { slug, period, offset, limit -> pagesOf(chart(slug, period))(offset, limit) }
    }

    private fun viewModel(genre: String? = null, period: String? = null) = ChartViewModel(
        repository,
        SavedStateHandle(listOfNotNull(genre?.let { "genre" to it }, period?.let { "period" to it }).toMap()),
    )

    @Test
    fun `defaults to the realtime chart of all, paged by 20`() {
        val vm = viewModel()
        val state = vm.uiState.value
        assertEquals(ChartPeriod.Realtime, state.period)
        assertEquals("all", state.category.slug)
        assertEquals(chart("all", ChartPeriod.Realtime).take(20), state.songs)
        assertFalse(state.loadMore.endReached)

        vm.loadMore()
        vm.loadMore()
        assertEquals(chart("all", ChartPeriod.Realtime), vm.uiState.value.songs)
        assertTrue(vm.uiState.value.loadMore.endReached)
        assertEquals(
            listOf("getChart(all, realtime)", "getChart(all, realtime, offset=20)", "getChart(all, realtime, offset=40)"),
            repository.calls,
        )
    }

    @Test
    fun `route arguments pick the chart`() {
        val state = viewModel(genre = "rock", period = "weekly").uiState.value
        assertEquals(ChartPeriod.Weekly, state.period)
        assertEquals("rock", state.category.slug)
        assertEquals(chart("rock", ChartPeriod.Weekly).take(20), state.songs)
        // AI 生成 has no weekly chart.
        assertFalse(state.categories.any { it.realtimeOnly })
    }

    @Test
    fun `switching the category cancels the running load`() {
        val held = CompletableDeferred<Result<Page<Song>>>()
        var cancelled = false
        repository.onChartPage = { slug, period, offset, limit ->
            if (slug == "all") {
                try {
                    held.await()
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                }
            } else {
                pagesOf(chart(slug, period))(offset, limit)
            }
        }
        val vm = viewModel()
        assertTrue(vm.uiState.value.isLoading)

        vm.selectCategory(ChartCategory.bySlug("folk"))
        assertTrue(cancelled)
        held.complete(Result.success(Page(songs(99, 3), null, null)))

        val state = vm.uiState.value
        assertEquals("folk", state.category.slug)
        assertEquals(chart("folk", ChartPeriod.Realtime).take(20), state.songs)
    }

    @Test
    fun `switching the period drops a load-more of the previous chart`() {
        val vm = viewModel()
        val held = CompletableDeferred<Result<Page<Song>>>()
        repository.onChartPage = { slug, period, offset, limit ->
            if (offset > 0 && period == ChartPeriod.Realtime) held.await() else pagesOf(chart(slug, period))(offset, limit)
        }
        vm.loadMore()
        assertTrue(vm.uiState.value.loadMore.isLoadingMore)

        vm.selectPeriod(ChartPeriod.Weekly)
        held.complete(Result.success(Page(songs(99, 20), 50, 40)))

        val state = vm.uiState.value
        assertEquals(ChartPeriod.Weekly, state.period)
        assertEquals(chart("all", ChartPeriod.Weekly).take(20), state.songs)
        assertFalse(state.loadMore.isLoadingMore)
    }

    @Test
    fun `weekly falls back to all from a realtime-only category`() {
        val vm = viewModel(genre = "ai_generated")
        assertEquals("ai_generated", vm.uiState.value.category.slug)
        vm.selectPeriod(ChartPeriod.Weekly)
        assertEquals("all", vm.uiState.value.category.slug)
        assertEquals("getChart(all, weekly)", repository.calls.last())
    }

    @Test
    fun `selecting the current tab or category does not reload`() {
        val vm = viewModel()
        vm.selectPeriod(ChartPeriod.Realtime)
        vm.selectCategory(ChartCategory.bySlug("all"))
        assertEquals(1, repository.calls.size)
    }

    @Test
    fun `failure shows an error and retry reloads`() {
        repository.onChartPage = { _, _, _, _ -> Result.failure(RuntimeException("500")) }
        val vm = viewModel()
        assertEquals(UserMessages.LOAD_FAILED, vm.uiState.value.error)

        repository.onChartPage = { slug, period, offset, limit -> pagesOf(chart(slug, period))(offset, limit) }
        vm.retry()
        assertNull(vm.uiState.value.error)
        assertEquals(20, vm.uiState.value.songs.size)
    }

    @Test
    fun `play all loads the whole chart`() {
        val vm = viewModel()
        var played: List<Song>? = null
        vm.playAll(PlayAllMode.Shuffle) { list, _ -> played = list }
        assertEquals(chart("all", ChartPeriod.Realtime), played)
        assertNull(vm.uiState.value.playAllPending)
    }
}
