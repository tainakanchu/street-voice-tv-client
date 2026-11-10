package com.example.streetvoicetv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.streetvoicetv.ui.common.LoadMoreState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** How close (in items) to the end of the list the next page is requested. */
const val LOAD_MORE_THRESHOLD = 5

const val LOAD_MORE_FOOTER_TAG = "load_more_footer"

/**
 * Calls [onLoadMore] whenever the last visible item of [listState] is within [threshold] items
 * of the end and [state] allows loading ([LoadMoreState.canLoadMore]). The focused item is
 * always visible (lists scroll it into view), so moving focus down a list - including holding
 * Down - keeps requesting pages ahead of the focus. The effect restarts whenever a load
 * finishes, so a list that is still near the end after a page keeps loading.
 */
@Composable
fun LoadMoreEffect(
    listState: LazyListState,
    state: LoadMoreState,
    onLoadMore: () -> Unit,
    threshold: Int = LOAD_MORE_THRESHOLD,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val enabled = state.canLoadMore
    LaunchedEffect(listState, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            lastVisible >= info.totalItemsCount - 1 - threshold
        }
            .distinctUntilChanged()
            .filter { it }
            .collect { currentOnLoadMore() }
    }
}

/**
 * Footer item of a paginated list: a small, non-focusable loading row while a page loads, or
 * a focusable 「載入更多失敗，重試」 row after a failure. Nothing when neither applies.
 *
 * The loading row is deliberately not focusable: it disappears when the page arrives, and a
 * focused element that disappears would drop the focus. For the same reason the retry row
 * hands focus back to the item above before it turns into the loading row.
 */
fun LazyListScope.loadMoreFooter(
    state: LoadMoreState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isLoadingMore && state.error == null) return
    item(key = LOAD_MORE_FOOTER_TAG, contentType = LOAD_MORE_FOOTER_TAG) {
        LoadMoreFooter(state = state, onRetry = onRetry, modifier = modifier)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LoadMoreFooter(
    state: LoadMoreState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    when {
        state.isLoadingMore -> Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .testTag(LOAD_MORE_FOOTER_TAG),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
            Text(
                text = "載入中…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.error != null -> ListItem(
            selected = false,
            onClick = {
                focusManager.moveFocus(FocusDirection.Up)
                onRetry()
            },
            modifier = modifier
                .fillMaxWidth()
                .testTag(LOAD_MORE_FOOTER_TAG),
            scale = listItemScale(),
            headlineContent = {
                Text(
                    text = "載入更多失敗，重試",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
            },
            leadingContent = {
                Icon(Icons.Default.Refresh, contentDescription = null)
            },
        )
    }
}

/**
 * For the last loaded item of a paginated list: while the next page is loading, Down stays on
 * this item instead of leaving the list (e.g. jumping to the NowPlayingBar) - the next item is
 * about to appear below it.
 */
fun Modifier.holdFocusWhileLoadingMore(isLastLoaded: Boolean, state: LoadMoreState): Modifier =
    if (isLastLoaded && state.isLoadingMore) focusProperties { down = FocusRequester.Cancel } else this
