package com.example.streetvoicetv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.ListItemScale
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.example.streetvoicetv.ui.theme.Dimens

/** Small focus scale so focused rows stay inside the TV safe area. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun listItemScale(): ListItemScale =
    ListItemDefaults.scale(focusedScale = Dimens.ListItemFocusedScale)

/**
 * Supporting text for list items. Follows LocalContentColor (so it stays readable
 * on the light focused container) instead of a fixed onSurfaceVariant.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SupportingText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        // tv ListItem already renders supporting content at 80% alpha of its
        // (focus-aware) content colour, so no extra alpha is applied here.
        color = LocalContentColor.current,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * Supporting row: an optional, shrinkable [primary] text (e.g. an artist name, ellipsized
 * when too long) followed by fixed [trailing] texts (durations, counts) that are never pushed out.
 */
@Composable
internal fun SupportingRow(primary: String?, vararg trailing: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (primary != null) {
            SupportingText(primary, Modifier.weight(1f, fill = false))
        }
        trailing.forEach { SupportingText(it) }
    }
}
