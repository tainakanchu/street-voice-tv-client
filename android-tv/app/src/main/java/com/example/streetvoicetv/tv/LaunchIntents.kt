package com.example.streetvoicetv.tv

import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import com.example.streetvoicetv.Routes

/** Something an incoming intent asks the app to do (besides just opening it). */
sealed interface LaunchAction {
    /** Watch Next deep link: resume the last session and open the player. */
    data object ResumeLastSession : LaunchAction

    /** Voice / Assistant search: open the search screen with [query]. */
    data class Search(val query: String) : LaunchAction
}

object LaunchIntents {
    /** Google Assistant / Google TV voice search action. */
    const val ACTION_GMS_SEARCH = "com.google.android.gms.actions.SEARCH_ACTION"

    const val SCHEME = "streetvoicetv"
    const val HOST_RESUME = "resume"

    /** Longest search query taken over from an intent. */
    const val MAX_QUERY_LENGTH = 100

    fun parse(intent: Intent?): LaunchAction? {
        intent ?: return null
        return parse(
            action = intent.action,
            data = intent.data,
            query = intent.getStringExtra(SearchManager.QUERY),
        )
    }

    fun parse(action: String?, data: Uri?, query: String?): LaunchAction? = when (action) {
        Intent.ACTION_SEARCH, ACTION_GMS_SEARCH ->
            query?.trim()?.take(MAX_QUERY_LENGTH)?.takeIf { it.isNotBlank() }?.let { LaunchAction.Search(it) }
        Intent.ACTION_VIEW ->
            if (data != null && data.scheme.equals(SCHEME, ignoreCase = true) &&
                data.host.equals(HOST_RESUME, ignoreCase = true)
            ) {
                LaunchAction.ResumeLastSession
            } else {
                null
            }
        else -> null
    }
}

/**
 * Route of the search screen pre-filled with [query] (Uri-encoded, so "a/b", "#x", "50%" stay
 * one argument). Navigating to it also works while the search destination has no `q` argument.
 */
internal fun Routes.search(query: String): String = "search?q=${Uri.encode(query)}"

/** Route of the settings screen. */
internal const val SETTINGS_ROUTE = "settings"
