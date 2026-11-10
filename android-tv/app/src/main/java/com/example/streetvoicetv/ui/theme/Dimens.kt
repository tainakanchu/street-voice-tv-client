package com.example.streetvoicetv.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layout constants for a 10-foot UI. The safe area follows the Android TV
 * guideline of 5% overscan (48dp horizontally / 27dp vertically at 960x540dp).
 */
object Dimens {
    /** Horizontal TV safe-area padding. */
    val SafeHorizontal: Dp = 48.dp

    /** Vertical TV safe-area padding. */
    val SafeVertical: Dp = 27.dp

    /** Extra bottom space reserved for scrolling lists while the NowPlayingBar is shown. */
    val NowPlayingBarInset: Dp = 96.dp

    /** Maximum width of the floating NowPlayingBar. */
    val NowPlayingBarMaxWidth: Dp = 400.dp

    /** Gap between top-level sections of a screen. */
    val SectionSpacing: Dp = 24.dp

    /** Focus scale for list rows (kept small so focused rows stay inside the safe area). */
    const val ListItemFocusedScale: Float = 1.02f
}

/**
 * Extra bottom inset that scrolling content should add to its contentPadding,
 * e.g. so the last list items are not hidden behind the floating NowPlayingBar.
 * Provided by AppScaffold.
 */
val LocalBottomOverlayInset = compositionLocalOf { 0.dp }

/** Standard contentPadding for full-screen vertical lists: safe area + overlay inset. */
fun screenListPadding(
    bottomOverlayInset: Dp,
    top: Dp = Dimens.SafeVertical,
    horizontal: Dp = Dimens.SafeHorizontal,
): PaddingValues = PaddingValues(
    start = horizontal,
    end = horizontal,
    top = top,
    bottom = Dimens.SafeVertical + bottomOverlayInset,
)
