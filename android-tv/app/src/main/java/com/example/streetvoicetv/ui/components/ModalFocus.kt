package com.example.streetvoicetv.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties

/** Keeps D-pad focus inside a modal overlay (focus can move within, but never leave it). */
@OptIn(ExperimentalComposeUiApi::class)
val ModalFocusTrap: Modifier = Modifier
    .focusProperties { exit = { FocusRequester.Cancel } }
    .focusGroup()

/** Prevents D-pad focus from entering content that is covered by a modal overlay. */
@OptIn(ExperimentalComposeUiApi::class)
val BlockFocusEntry: Modifier = Modifier
    .focusProperties { enter = { FocusRequester.Cancel } }
    .focusGroup()
