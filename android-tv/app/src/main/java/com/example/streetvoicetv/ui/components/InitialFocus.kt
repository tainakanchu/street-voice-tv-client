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
import androidx.compose.ui.focus.focusRestorer

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
    internal val rootRequester: FocusRequester,
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
    val rootRequester = remember { FocusRequester() }
    val done = rememberSaveable { mutableStateOf(false) }
    return remember(requester, rootRequester, done) { InitialFocus(requester, rootRequester, done) }
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

/**
 * Marks the focus root of a screen and brings focus back to where it was when the user
 * returns to the screen (e.g. Back from the player): without this, nothing would be focused,
 * because the previously focused element was disposed together with the screen.
 *
 * Must be followed by a focus group (a lazy list's own group, or `.focusGroup()`).
 * [focusRestorer] keeps the focused child in the saved state of the back stack entry, so it
 * survives navigation; every nested focus group on the way down to the element needs its own
 * focusRestorer (lazy rows / lists) for the restoration to reach the exact element.
 * On the first visit nothing happens here: [initialFocus] handles that.
 */
@Composable
fun Modifier.focusRoot(focus: InitialFocus): Modifier {
    val returning = remember { focus.isDone }
    if (returning) {
        LaunchedEffect(focus) { focus.rootRequester.requestFocusWhenReady() }
    }
    return this
        .focusRequester(focus.rootRequester)
        .focusRestorer()
}
