package com.example.streetvoicetv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/**
 * One-shot initial focus for the first meaningful item of a screen.
 *
 * Lazy list items are composed during layout, after the parent's effects may
 * already have run, so the request is issued from inside the item itself
 * ([initialFocus]). The "done" flag is saveable so returning to a screen does
 * not steal focus again, and nothing happens when the list is empty.
 */
@Stable
class InitialFocus internal constructor(
    val requester: FocusRequester,
    private val done: MutableState<Boolean>,
) {
    internal var isDone: Boolean
        get() = done.value
        set(value) {
            done.value = value
        }
}

@Composable
fun rememberInitialFocus(): InitialFocus {
    val requester = remember { FocusRequester() }
    val done = rememberSaveable { mutableStateOf(false) }
    return remember(requester, done) { InitialFocus(requester, done) }
}

/**
 * Attaches the [InitialFocus] requester to this element (when [enabled]) and
 * requests focus once it is composed. Must be used on exactly one element.
 */
@Composable
fun Modifier.initialFocus(focus: InitialFocus?, enabled: Boolean = true): Modifier {
    if (focus == null || !enabled) return this
    if (!focus.isDone) {
        LaunchedEffect(focus) {
            focus.requester.requestFocusWhenReady()
            focus.isDone = true
        }
    }
    return this.focusRequester(focus.requester)
}
