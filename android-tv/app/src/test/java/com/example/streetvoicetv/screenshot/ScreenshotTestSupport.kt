package com.example.streetvoicetv.screenshot

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.requestFocus

/** Moves focus to the first focusable node whose (merged) text contains [text]. */
fun ComposeContentTestRule.focusOnText(text: String) {
    onAllNodes(hasText(text, substring = true) and isFocusable()).onFirst().requestFocus()
}

/** Moves focus to the first focusable node whose (merged) content description is [description]. */
fun ComposeContentTestRule.focusOnContentDescription(description: String) {
    onAllNodes(hasContentDescription(description) and isFocusable()).onFirst().requestFocus()
}
