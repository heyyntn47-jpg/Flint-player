// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.library

import android.net.Uri
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flint.player.ui.EmptyPlaceholder
import com.flint.player.ui.segmentShape

@Composable
fun SearchResults(
    library: VideoLibraryState,
    query: String,
    sortField: SortField,
    ascending: Boolean,
    viewMode: ViewMode,
    onOpenVideo: (Uri) -> Unit
) {
    val q = query.trim()
    val results = remember(library.videos, q, sortField, ascending) {
        if (q.isEmpty()) emptyList()
        else library.videos
            .filter { it.name.contains(q, ignoreCase = true) }
            .sortVideos(sortField, ascending)
    }

    when {
        !library.hasPermission -> PermissionPrompt(onRequest = library.requestPermission)
        q.isEmpty() -> EmptyPlaceholder(
            icon = Icons.Filled.Search,
            title = "Search your videos",
            subtitle = "Type part of a file name."
        )
        results.isEmpty() -> EmptyPlaceholder(
            icon = Icons.Filled.Search,
            title = "No matches",
            subtitle = "Nothing is named \"$q\"."
        )
        else -> LazyColumn(
            modifier = Modifier,
            contentPadding = PaddingValues(bottom = 112.dp)
        ) {
            item(key = "count") {
                Text(
                    text = if (results.size == 1) "1 result" else "${results.size} results",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 28.dp, top = 8.dp, bottom = 8.dp)
                )
            }
            if (viewMode == ViewMode.GRID) {
                videoGridItems(results, keyPrefix = "search", onOpenVideo = onOpenVideo)
            } else {
                itemsIndexed(results, key = { _, video -> video.id }) { index, video ->
                    VideoRow(
                        video = video,
                        onClick = { onOpenVideo(video.uri) },
                        shape = segmentShape(index, results.size)
                    )
                }
            }
        }
    }
}