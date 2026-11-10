package com.example.streetvoicetv.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchHistoryManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    private val lock = Any()

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        update { current -> (listOf(trimmed) + current.filter { it != trimmed }).take(MAX_HISTORY) }
    }

    fun remove(query: String) {
        update { current -> current.filter { it != query } }
    }

    /** Read-modify-write under a lock so concurrent add/remove calls cannot lose entries. */
    private fun update(transform: (List<String>) -> List<String>) {
        synchronized(lock) {
            val updated = transform(_history.value)
            prefs.edit()
                .putString(KEY_ORDERED, updated.joinToString(SEPARATOR))
                .remove(LEGACY_KEY_SET) // unused unordered copy written by older versions
                .apply()
            _history.value = updated
        }
    }

    private fun loadHistory(): List<String> {
        val ordered = prefs.getString(KEY_ORDERED, null) ?: return emptyList()
        return ordered.split(SEPARATOR).filter { it.isNotBlank() }.take(MAX_HISTORY)
    }

    companion object {
        private const val LEGACY_KEY_SET = "history_set"
        private const val KEY_ORDERED = "history_ordered"
        private const val SEPARATOR = "\u001F"
        private const val MAX_HISTORY = 20
    }
}
