// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.history

import com.flint.player.ui.MiddleEllipsisText
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flint.player.data.FlintData
import com.flint.player.data.HistoryEntry
import com.flint.player.library.Thumbnail
import com.flint.player.library.formatDuration
import com.flint.player.ui.EmptyPlaceholder
import com.flint.player.ui.ExpressiveChip
import com.flint.player.ui.ExpressiveDialog
import com.flint.player.ui.ExpressiveIconButton
import com.flint.player.ui.pressItem
import com.flint.player.ui.segmentShape

@Composable
fun HistoryTab(onOpenVideo: (Uri) -> Unit) {
    val history = FlintData.history
    var confirmClear by remember { mutableStateOf(false) }

    val groups = remember(history) {
        val today = history.filter { DateUtils.isToday(it.lastPlayed) }
        val yesterday = history.filter { DateUtils.isToday(it.lastPlayed + DateUtils.DAY_IN_MILLIS) }
        val earlier = history.filter { entry ->
            !DateUtils.isToday(entry.lastPlayed) &&
                    !DateUtils.isToday(entry.lastPlayed + DateUtils.DAY_IN_MILLIS)
        }
        listOf("Today" to today, "Yesterday" to yesterday, "Earlier" to earlier)
            .filter { it.second.isNotEmpty() }
    }

    if (history.isEmpty()) {
        EmptyPlaceholder(
            icon = Icons.Filled.History,
            title = "No history yet",
            subtitle = "Videos you watch will show up here, with the spot where you stopped."
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 128.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = 16.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recently played",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveChip(
                        label = "Clear all",
                        selected = false,
                        onClick = { confirmClear = true }
                    )
                }
            }
            groups.forEach { (label, entries) ->
                item(key = "label-$label") {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp)
                    )
                }
                itemsIndexed(entries, key = { _, entry -> entry.uri }) { index, entry ->
                    HistoryRow(
                        entry = entry,
                        shape = segmentShape(index, entries.size),
                        onClick = { onOpenVideo(Uri.parse(entry.uri)) },
                        onRemove = { FlintData.removeHistory(entry.uri) }
                    )
                }
            }
        }
    }

    if (confirmClear) {
        ExpressiveDialog(
            title = "Clear history?",
            onDismiss = { confirmClear = false },
            confirmText = "Clear",
            destructive = true,
            onConfirm = {
                confirmClear = false
                FlintData.clearHistory()
            }
        ) {
            Text(
                text = "This also forgets where you stopped in each video.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryRow(
    entry: HistoryEntry,
    shape: Shape,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val fraction = if (entry.durationMs > 0) {
        (entry.positionMs.toFloat() / entry.durationMs).coerceIn(0f, 1f)
    } else 0f

    val timeAgo = DateUtils.getRelativeTimeSpanString(
        entry.lastPlayed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
    ).toString()

    val subtitle = if (entry.durationMs > 0) {
        "${formatDuration(entry.positionMs)} / ${formatDuration(entry.durationMs)} • $timeAgo"
    } else timeAgo

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .pressItem(shape = shape, container = scheme.surfaceContainer, onClick = onClick)
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Thumbnail(uri = Uri.parse(entry.uri), modifier = Modifier.fillMaxSize())
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(scheme.primary)
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            MiddleEllipsisText(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        ExpressiveIconButton(
            icon = Icons.Filled.Close,
            description = "Remove",
            onClick = onRemove,
            size = 40.dp,
            iconSize = 20.dp,
            tint = scheme.onSurfaceVariant
        )
    }
}