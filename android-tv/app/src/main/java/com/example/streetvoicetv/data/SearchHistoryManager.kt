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

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    fun add(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val current = _history.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        val updated = current.take(MAX_HISTORY)
        prefs.edit().putStringSet(KEY, updated.toSet())
            .putString(KEY_ORDERED, updated.joinToString(SEPARATOR))
            .apply()
        _history.value = updated
    }

    fun remove(query: String) {
        val updated = _history.value.filter { it != query }
        prefs.edit().putString(KEY_ORDERED, updated.joinToString(SEPARATOR)).apply()
        _history.value = updated
    }

    private fun loadHistory(): List<String> {
        val ordered = prefs.getString(KEY_ORDERED, null) ?: return emptyList()
        return ordered.split(SEPARATOR).filter { it.isNotBlank() }.take(MAX_HISTORY)
    }

    companion object {
        private const val KEY = "history_set"
        private const val KEY_ORDERED = "history_ordered"
        private const val SEPARATOR = "\u001F"
        private const val MAX_HISTORY = 20
    }
}
