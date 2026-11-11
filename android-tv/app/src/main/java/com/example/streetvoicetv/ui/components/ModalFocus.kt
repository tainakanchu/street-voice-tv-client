package com.example.streetvoicetv.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties

/** Keeps D-pad focus inside a modal overlay (focus can move within, but never leave it). */
val ModalFocusTrap: Modifier = Modifier
    .focusProperties { onExit = { cancelFocusChange() } }
    .focusGroup()

/** Prevents D-pad focus from entering content that is covered by a modal overlay. */
val BlockFocusEntry: Modifier = Modifier
    .focusProperties { onEnter = { cancelFocusChange() } }
    .focusGroup()
