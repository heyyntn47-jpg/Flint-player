// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.browse

import com.flint.player.ui.MiddleEllipsisText
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flint.player.library.PermissionPrompt
import com.flint.player.library.SortField
import com.flint.player.library.VideoItem
import com.flint.player.library.VideoLibraryState
import com.flint.player.library.VideoRow
import com.flint.player.library.ViewMode
import com.flint.player.library.videoGridItems
import com.flint.player.library.sortVideos
import com.flint.player.ui.EmptyPlaceholder
import com.flint.player.ui.pressItem
import com.flint.player.ui.segmentShape

private data class FolderEntry(
    val name: String,
    val path: String,
    val videoCount: Int,
    val isStorage: Boolean = false
)

private val VOLUME_REGEX = Regex("^/storage/(emulated/\\d+|[^/]+)")

private fun volumeRoot(path: String): String? = VOLUME_REGEX.find(path)?.value

private fun volumeName(root: String): String =
    if (root.startsWith("/storage/emulated")) "Internal storage"
    else "SD card (${root.substringAfterLast('/')})"

private fun displayPath(dir: String): String {
    val root = volumeRoot(dir) ?: return dir
    return volumeName(root) + dir.removePrefix(root)
}

private fun parentOf(dir: String): String? =
    if (volumeRoot(dir) == dir) null else dir.substringBeforeLast('/')

private fun videoCountLabel(count: Int) = if (count == 1) "1 video" else "$count videos"

private fun listStorages(videos: List<VideoItem>): List<FolderEntry> =
    videos.mapNotNull { volumeRoot(it.path) }
        .groupingBy { it }
        .eachCount()
        .map { (root, count) -> FolderEntry(volumeName(root), root, count, isStorage = true) }
        .sortedBy { it.name }

private fun listFolder(
    videos: List<VideoItem>,
    dir: String
): Pair<List<FolderEntry>, List<VideoItem>> {
    val prefix = "$dir/"
    val subCounts = linkedMapOf<String, Int>()
    val direct = mutableListOf<VideoItem>()

    for (video in videos) {
        if (!video.path.startsWith(prefix)) continue
        val rest = video.path.removePrefix(prefix)
        val slash = rest.indexOf('/')
        if (slash >= 0) {
            val sub = rest.substring(0, slash)
            subCounts[sub] = (subCounts[sub] ?: 0) + 1
        } else {
            direct += video
        }
    }

    val folders = subCounts
        .map { (name, count) -> FolderEntry(name, prefix + name, count) }
        .sortedBy { it.name.lowercase() }
    return folders to direct
}

@Composable
fun BrowseTab(
    library: VideoLibraryState,
    sortField: SortField,
    ascending: Boolean,
    viewMode: ViewMode,
    onOpenVideo: (Uri) -> Unit,
    onOpenNetwork: () -> Unit
) {
    var currentDir by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = currentDir != null) {
        currentDir = currentDir?.let { parentOf(it) }
    }

    // Streaming needs no storage permission, so the Network entry also shows above the
    // permission and "no videos" screens
    val listShown = library.hasPermission && !library.loading && library.videos.isNotEmpty()
    Column(Modifier.fillMaxSize()) {
    if (currentDir == null && !listShown) NetworkSection(onOpenNetwork)
    Box(Modifier.weight(1f).fillMaxWidth()) {
        when {
            !library.hasPermission -> PermissionPrompt(onRequest = library.requestPermission)
            library.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            library.videos.isEmpty() -> EmptyPlaceholder(
                icon = Icons.Filled.Folder,
                title = "No videos found",
                subtitle = "Folders that contain videos will show up here."
            )
            else -> {
                val dir = currentDir
                val (folders, files) = remember(library.videos, dir, sortField, ascending) {
                    if (dir == null) {
                        listStorages(library.videos) to emptyList<VideoItem>()
                    } else {
                        val (subFolders, direct) = listFolder(library.videos, dir)
                        subFolders to direct.sortVideos(sortField, ascending)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 128.dp)
                ) {
                    if (dir == null) {
                        item(key = "network") { NetworkSection(onOpenNetwork) }
                    }
                    if (dir != null) {
                        item(key = "path") {
                            PathBar(dir = dir, onUp = { currentDir = parentOf(dir) })
                        }
                    }
                    if (folders.isNotEmpty()) {
                        item(key = "folders-label") {
                            GroupLabel(if (dir == null) "Storage" else "Folders")
                        }
                        if (viewMode == ViewMode.GRID && dir != null) {
                            // Storage entries stay full width; sub-folders become cards
                            items(folders.chunked(2), key = { row -> "folder-grid-${row.first().path}" }) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    row.forEach { folder ->
                                        FolderCard(
                                            title = folder.name,
                                            subtitle = videoCountLabel(folder.videoCount),
                                            onClick = { currentDir = folder.path },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        } else {
                            itemsIndexed(folders, key = { _, folder -> folder.path }) { index, folder ->
                                EntryRow(
                                    icon = if (folder.isStorage) Icons.Filled.Storage else Icons.Filled.Folder,
                                    title = folder.name,
                                    subtitle = videoCountLabel(folder.videoCount),
                                    shape = segmentShape(index, folders.size),
                                    onClick = { currentDir = folder.path }
                                )
                            }
                        }
                    }
                    if (files.isNotEmpty()) {
                        item(key = "files-label") { GroupLabel("Videos") }
                        if (viewMode == ViewMode.GRID) {
                            videoGridItems(files, keyPrefix = "browse", onOpenVideo = onOpenVideo)
                        } else {
                            itemsIndexed(files, key = { _, video -> video.id }) { index, video ->
                                VideoRow(
                                    video = video,
                                    onClick = { onOpenVideo(video.uri) },
                                    shape = segmentShape(index, files.size)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun NetworkSection(onOpen: () -> Unit) {
    GroupLabel("Network")
    EntryRow(
        icon = Icons.Filled.Dns,
        title = "Network and streams",
        subtitle = "Links, playlists, SMB, FTP, SFTP, NFS, UPnP / DLNA",
        shape = segmentShape(0, 1),
        onClick = onOpen
    )
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun PathBar(dir: String, onUp: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val title = if (volumeRoot(dir) == dir) volumeName(dir) else dir.substringAfterLast('/')

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .pressItem(shape = CircleShape, container = scheme.secondaryContainer, onClick = onUp)
            .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(scheme.secondary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Up",
                tint = scheme.onSecondary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            MiddleEllipsisText(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSecondaryContainer
            )
            MiddleEllipsisText(
                text = displayPath(dir),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSecondaryContainer,
                minTail = 14
            )
        }
    }
}

@Composable
private fun EntryRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    shape: Shape,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .pressItem(shape = shape, container = scheme.surfaceContainer, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = scheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            MiddleEllipsisText(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FolderCard(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .pressItem(
                shape = RoundedCornerShape(24.dp),
                container = scheme.surfaceContainer,
                onClick = onClick
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = scheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            MiddleEllipsisText(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
