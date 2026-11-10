package com.example.streetvoicetv.navigation

import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.example.streetvoicetv.screenshot.captureTvImage
import com.example.streetvoicetv.ui.components.NOW_PLAYING_BAR_TAG
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.InputDeviceBuilder

/*
 * D-pad navigation test DSL.
 *
 * How key injection works here (verified under Robolectric, SDK 35, Compose 1.10):
 * - Keys are dispatched as real `android.view.KeyEvent`s (ACTION_DOWN + ACTION_UP) through
 *   `Activity.dispatchKeyEvent`, i.e. the same path a remote control takes on a device:
 *   window -> AndroidComposeView -> Compose key handlers (onPreviewKeyEvent / onKeyEvent) ->
 *   Compose 2D focus traversal. BACK reaches the OnBackPressedDispatcher (BackHandler) this way too.
 * - Events carry `InputDevice.SOURCE_DPAD` and a fake, non-virtual remote control as their
 *   `device` (set through Robolectric's ShadowInputEvent). Compose text fields only hand D-pad arrows over to focus
 *   traversal for events from such a device; otherwise a text field would treat Up/Down as
 *   cursor keys and swallow them (unlike a real remote).
 * - `onRoot().performKeyInput { pressKey(Key.DirectionRight) }` also moves focus under Robolectric,
 *   but its events come from a virtual keyboard (text fields swallow arrows) and BACK does not
 *   reach the activity's OnBackPressedDispatcher, so it is not used.
 * - After every key press the test waits for idle (the main clock auto-advances, so scroll /
 *   bring-into-view animations finish) and fails if no node is focused ("focus lost").
 */

/** Id of the fake remote control attached to every key event. */
private const val REMOTE_DEVICE_ID = 7

/** A non-virtual remote control with a D-pad (Robolectric has no input devices of its own). */
private val Remote: InputDevice by lazy {
    InputDeviceBuilder.newBuilder()
        .setId(REMOTE_DEVICE_ID)
        .setName("Test remote")
        .setDescriptor("test-remote")
        .setExternal(true)
        .setSources(InputDevice.SOURCE_DPAD or InputDevice.SOURCE_KEYBOARD)
        .setKeyboardType(InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC)
        .build()
}

/**
 * Makes the Robolectric device report [PackageManager.FEATURE_LEANBACK] like a real Android TV.
 * The `television` resource qualifier alone does not, and Compose picks how it scrolls focused
 * items into view from this feature (LocalBringIntoViewSpec: TV "pivot" vs. phone "minimal
 * scroll"). Call before setContent.
 */
fun emulateTvDevice() {
    val pm = RuntimeEnvironment.getApplication().packageManager
    shadowOf(pm).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)
}

typealias TvComposeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

fun createTvComposeRule(): TvComposeRule = createAndroidComposeRule<ComponentActivity>()

/** Remote-control keys. */
enum class Dpad(val keyCode: Int) {
    Up(KeyEvent.KEYCODE_DPAD_UP),
    Down(KeyEvent.KEYCODE_DPAD_DOWN),
    Left(KeyEvent.KEYCODE_DPAD_LEFT),
    Right(KeyEvent.KEYCODE_DPAD_RIGHT),
    Center(KeyEvent.KEYCODE_DPAD_CENTER),
    Back(KeyEvent.KEYCODE_BACK),
}

/**
 * Presses [keys] one after another like a remote control. After each press, asserts that some
 * node still has focus (unless [allowFocusLoss]).
 */
fun TvComposeRule.dpad(vararg keys: Dpad, allowFocusLoss: Boolean = false) {
    keys.forEachIndexed { i, key ->
        val before = describeFocus()
        sendKey(key.keyCode)
        if (!allowFocusLoss && focusedNodeOrNull() == null) {
            fail("Focus lost after pressing $key (press ${i + 1}/${keys.size}); focus before was: $before")
        }
    }
}

/** Presses [key] [times] times. */
fun TvComposeRule.dpad(key: Dpad, times: Int) = dpad(*Array(times) { key })

private fun TvComposeRule.sendKey(keyCode: Int) {
    runOnUiThread {
        val now = SystemClock.uptimeMillis()
        val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0, REMOTE_DEVICE_ID, 0, 0, InputDevice.SOURCE_DPAD)
        val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, 0, REMOTE_DEVICE_ID, 0, 0, InputDevice.SOURCE_DPAD)
        shadowOf(down).setDevice(Remote)
        shadowOf(up).setDevice(Remote)
        activity.dispatchKeyEvent(down)
        activity.dispatchKeyEvent(up)
    }
    waitForIdle()
}

fun TvComposeRule.focusedNodeOrNull(): SemanticsNode? =
    onAllNodes(isFocused()).fetchSemanticsNodes().firstOrNull()

fun TvComposeRule.focusedNode(): SemanticsNode =
    focusedNodeOrNull() ?: throw AssertionError("Nothing is focused")

/** Human readable description of the focused node (for failure messages). */
fun TvComposeRule.describeFocus(): String = focusedNodeOrNull()?.describe() ?: "<nothing>"

private fun SemanticsNode.describe(): String {
    val c = config
    val parts = listOfNotNull(
        c.getOrNull(SemanticsProperties.TestTag)?.let { "tag=$it" },
        c.getOrNull(SemanticsProperties.Text)?.joinToString(" | ")?.let { "text=$it" },
        c.getOrNull(SemanticsProperties.EditableText)?.let { "editable=$it" },
        c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" | ")?.let { "desc=$it" },
    )
    return if (parts.isEmpty()) "node#$id" else parts.joinToString(", ")
}

/** Asserts the focused node matches [matcher]. */
fun TvComposeRule.assertFocused(matcher: SemanticsMatcher) {
    val node = focusedNode()
    if (!matcher.matches(node)) {
        fail("Expected focus on <${matcher.description}> but focus is on <${node.describe()}>")
    }
}

fun TvComposeRule.assertFocusedText(text: String) = assertFocused(hasText(text, substring = true))

fun TvComposeRule.assertFocusedTag(tag: String) = assertFocused(hasTestTag(tag))

fun TvComposeRule.assertFocusedDescription(description: String) =
    assertFocused(hasContentDescription(description))

/** Whether the focused node is the node with [tag] or one of its descendants. */
fun TvComposeRule.isFocusInside(tag: String): Boolean {
    val container = onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().firstOrNull() ?: return false
    var p: SemanticsNode? = focusedNodeOrNull()
    while (p != null && p.id != container.id) p = p.parent
    return p != null
}

fun TvComposeRule.assertFocusInside(tag: String) {
    if (!isFocusInside(tag)) fail("Expected focus inside <$tag> but focus is on <${describeFocus()}>")
}

/** First text of the focused node (e.g. a card's title). */
fun TvComposeRule.focusedText(): String =
    focusedNode().config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text.orEmpty()

private fun rootBounds(rule: TvComposeRule): Rect = rule.onRoot().fetchSemanticsNode().boundsInRoot

/**
 * Asserts the focused node is fully inside the window, and not hidden behind the
 * floating NowPlayingBar (when that is shown and the focus is not on the bar itself).
 */
fun TvComposeRule.assertFocusedFullyVisible() {
    val node = focusedNode()
    val b = node.boundsInRoot
    val root = rootBounds(this)
    val eps = 1f
    assertTrue(
        "Focused <${node.describe()}> $b is not fully inside the window $root",
        b.left >= root.left - eps && b.top >= root.top - eps &&
            b.right <= root.right + eps && b.bottom <= root.bottom + eps,
    )
    val bar = onAllNodes(hasTestTag(NOW_PLAYING_BAR_TAG)).fetchSemanticsNodes().firstOrNull() ?: return
    val barBounds = bar.boundsInRoot
    var p: SemanticsNode? = node
    while (p != null && p.id != bar.id) p = p.parent
    if (p != null) return // focus is on the bar itself
    val overlap = b.intersect(barBounds)
    assertTrue(
        "Focused <${node.describe()}> $b is (partly) hidden behind the NowPlayingBar $barBounds",
        overlap.width <= eps || overlap.height <= eps,
    )
}

/** Bounds (in root) of the first node matching [matcher]. */
fun TvComposeRule.boundsOf(matcher: SemanticsMatcher): Rect =
    onAllNodes(matcher).fetchSemanticsNodes().first().boundsInRoot

/** Captures `dpad_<name>.png` (kept modest: only at meaningful steps). */
fun TvComposeRule.captureDpad(name: String) = captureTvImage("dpad_$name")
