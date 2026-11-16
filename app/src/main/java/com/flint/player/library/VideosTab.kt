// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.library

import com.flint.player.ui.MiddleEllipsisText
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flint.player.ui.TabularNumbers
import com.flint.player.data.MediaEntry
import com.flint.player.playlists.AddToPlaylistDialog
import com.flint.player.playlists.NameDialog
import com.flint.player.ui.ExpressiveDialog
import com.flint.player.ui.pressItem
import com.flint.player.ui.segmentShape

@Composable
fun VideosTab(
    library: VideoLibraryState,
    sortField: SortField,
    ascending: Boolean,
    viewMode: ViewMode,
    onOpenVideo: (Uri) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        when {
            !library.hasPermission -> PermissionPrompt(onRequest = library.requestPermission)
            library.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            library.videos.isEmpty() -> Text("No videos found", Modifier.align(Alignment.Center))
            else -> VideoList(library.videos, sortField, ascending, viewMode, onOpenVideo)
        }
    }
}

@Composable
private fun VideoList(
    videos: List<VideoItem>,
    sortField: SortField,
    ascending: Boolean,
    viewMode: ViewMode,
    onOpenVideo: (Uri) -> Unit
) {
    val grouped = remember(videos, sortField, ascending) {
        videos.groupBy { it.folder }
            .entries
            .sortedBy { it.key.lowercase() }
            .map { (folder, list) -> folder to list.sortVideos(sortField, ascending) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 128.dp)
    ) {
        grouped.forEach { (folder, folderVideos) ->
            item(key = "header-$folder") {
                Text(
                    text = folder,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp)
                )
            }
            if (viewMode == ViewMode.GRID) {
                videoGridItems(folderVideos, keyPrefix = folder, onOpenVideo = onOpenVideo)
            } else {
                itemsIndexed(folderVideos, key = { _, video -> video.id }) { index, video ->
                    VideoRow(
                        video = video,
                        onClick = { onOpenVideo(video.uri) },
                        shape = segmentShape(index, folderVideos.size)
                    )
                }
            }
        }
    }
}

private const val GRID_COLUMNS = 2

/**
 * Adds videos as rows of cards, [GRID_COLUMNS] per row. Built from plain rows inside the
 * existing LazyColumn so headers and other full-width items keep their own padding.
 */
fun LazyListScope.videoGridItems(
    videos: List<VideoItem>,
    keyPrefix: String,
    onOpenVideo: (Uri) -> Unit
) {
    val rows = videos.chunked(GRID_COLUMNS)
    items(rows, key = { row -> "grid-$keyPrefix-${row.first().id}" }) { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            row.forEach { video ->
                VideoCard(
                    video = video,
                    onClick = { onOpenVideo(video.uri) },
                    modifier = Modifier.weight(1f)
                )
            }
            // Keep the last card the same width when the row is not full
            repeat(GRID_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

private enum class RowOverlay { MENU, ADD, RENAME, DELETE }

@Composable
fun VideoRow(
    video: VideoItem,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    val scheme = MaterialTheme.colorScheme
    var overlay by remember { mutableStateOf<RowOverlay?>(null) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .pressItem(
                shape = shape,
                container = scheme.surfaceContainer,
                onLongClick = { overlay = RowOverlay.MENU },
                onClick = onClick
            )
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Thumbnail(uri = video.uri, modifier = Modifier.fillMaxSize())
            Text(
                text = formatDuration(video.durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.merge(TabularNumbers),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            MiddleEllipsisText(
                text = video.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatSize(video.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
    }

    VideoOverlays(video, overlay, onClick, setOverlay = { overlay = it })
}

@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    var overlay by remember { mutableStateOf<RowOverlay?>(null) }
    val nameStyle = MaterialTheme.typography.bodyMedium
    // Always reserve two lines so cards in the same row stay the same height
    val nameHeight = with(LocalDensity.current) { nameStyle.lineHeight.toDp() * 2 }

    Column(
        modifier = modifier
            .pressItem(
                shape = RoundedCornerShape(24.dp),
                container = scheme.surfaceContainer,
                onLongClick = { overlay = RowOverlay.MENU },
                onClick = onClick
            )
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
        ) {
            Thumbnail(uri = video.uri, modifier = Modifier.fillMaxSize())
            Text(
                text = formatDuration(video.durationMs),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall.merge(TabularNumbers),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = nameHeight)) {
            MiddleEllipsisText(
                text = video.name,
                style = nameStyle,
                maxLines = 2
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatSize(video.sizeBytes),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
    }

    VideoOverlays(video, overlay, onClick, setOverlay = { overlay = it })
}

@Composable
private fun VideoOverlays(
    video: VideoItem,
    overlay: RowOverlay?,
    onClick: () -> Unit,
    setOverlay: (RowOverlay?) -> Unit
) {
    val actions = LocalFileActions.current
    val scheme = MaterialTheme.colorScheme
    when (overlay) {
        RowOverlay.MENU -> VideoContextMenu(
            video = video,
            onDismiss = { setOverlay(null) },
            onPlay = { setOverlay(null); onClick() },
            onAddToPlaylist = { setOverlay(RowOverlay.ADD) },
            onShare = actions?.let { a -> { setOverlay(null); a.share(video) } },
            onRename = actions?.let { _ -> { setOverlay(RowOverlay.RENAME) } },
            onDelete = actions?.let { a ->
                {
                    if (Build.VERSION.SDK_INT >= 30) {
                        // Android 11+ shows its own confirmation
                        setOverlay(null)
                        a.delete(video)
                    } else {
                        setOverlay(RowOverlay.DELETE)
                    }
                }
            }
        )

        RowOverlay.ADD -> AddToPlaylistDialog(
            entry = MediaEntry(video.uri.toString(), video.name, video.durationMs),
            onDismiss = { setOverlay(null) }
        )

        RowOverlay.RENAME -> if (actions != null) {
            NameDialog(
                title = "Rename video",
                initial = video.name.substringBeforeLast('.'),
                confirmLabel = "Rename",
                onDismiss = { setOverlay(null) },
                onConfirm = { name ->
                    setOverlay(null)
                    actions.rename(video, name)
                }
            )
        }

        RowOverlay.DELETE -> if (actions != null) {
            ExpressiveDialog(
                title = "Delete video?",
                onDismiss = { setOverlay(null) },
                confirmText = "Delete",
                destructive = true,
                onConfirm = {
                    setOverlay(null)
                    actions.delete(video)
                }
            ) {
                Text(
                    text = "\"${video.name}\" will be permanently deleted from your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            }
        }

        null -> Unit
    }
}