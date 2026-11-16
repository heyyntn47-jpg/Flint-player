// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.playlists

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flint.player.data.FlintData
import com.flint.player.data.MediaEntry
import com.flint.player.data.Playlist
import com.flint.player.library.Thumbnail
import com.flint.player.library.formatDuration
import com.flint.player.ui.EmptyPlaceholder
import com.flint.player.ui.ExpressiveButton
import com.flint.player.ui.ExpressiveDialog
import com.flint.player.ui.ExpressiveIconButton
import com.flint.player.ui.pressItem
import com.flint.player.ui.segmentShape

@Composable
fun PlaylistsTab(onPlay: (playlistId: String, index: Int) -> Unit) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    val playlists = FlintData.playlists
    val open = playlists.firstOrNull { it.id == openId }

    BackHandler(enabled = open != null) { openId = null }

    if (open != null) {
        PlaylistDetail(playlist = open, onBack = { openId = null }, onPlay = onPlay)
    } else {
        PlaylistList(
            playlists = playlists,
            onOpen = { openId = it },
            onNew = { creating = true }
        )
    }

    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { creating = false },
            onConfirm = { name ->
                FlintData.createPlaylist(name)
                creating = false
            }
        )
    }
}

private fun countLabel(count: Int) = if (count == 1) "1 video" else "$count videos"

@Composable
private fun PlaylistList(
    playlists: List<Playlist>,
    onOpen: (String) -> Unit,
    onNew: () -> Unit
) {
    if (playlists.isEmpty()) {
        EmptyPlaceholder(
            icon = Icons.AutoMirrored.Filled.PlaylistPlay,
            title = "No playlists yet",
            subtitle = "Long-press any video and choose Add to playlist.",
            action = {
                ExpressiveButton(text = "New playlist", onClick = onNew, icon = Icons.Rounded.Add)
            }
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
                        .padding(start = 28.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your playlists",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    ExpressiveButton(
                        text = "New",
                        onClick = onNew,
                        icon = Icons.Rounded.Add,
                        tonal = true
                    )
                }
            }
            itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                PlaylistRow(
                    playlist = playlist,
                    shape = segmentShape(index, playlists.size),
                    onClick = { onOpen(playlist.id) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: Playlist, shape: Shape, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val first = playlist.items.firstOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .pressItem(shape = shape, container = scheme.surfaceContainer, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val coverModifier = Modifier
            .width(104.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))

        if (first != null) {
            Thumbnail(uri = Uri.parse(first.uri), modifier = coverModifier)
        } else {
            Box(
                modifier = coverModifier.background(scheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    tint = scheme.onPrimaryContainer
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = countLabel(playlist.items.size),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlaylistDetail(
    playlist: Playlist,
    onBack: () -> Unit,
    onPlay: (String, Int) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = "Back",
                onClick = onBack,
                tint = scheme.onSurfaceVariant
            )
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )
            ExpressiveIconButton(
                icon = Icons.Outlined.Edit,
                description = "Rename",
                onClick = { renaming = true },
                tint = scheme.onSurfaceVariant
            )
            ExpressiveIconButton(
                icon = Icons.Outlined.Delete,
                description = "Delete playlist",
                onClick = { confirmDelete = true },
                tint = scheme.error,
                pressedContainer = scheme.errorContainer,
                pressedTint = scheme.onErrorContainer
            )
        }

        if (playlist.items.isEmpty()) {
            EmptyPlaceholder(
                icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                title = "This playlist is empty",
                subtitle = "Long-press any video and choose Add to playlist."
            )
        } else {
            ExpressiveButton(
                text = "Play all (${playlist.items.size})",
                onClick = { onPlay(playlist.id, 0) },
                icon = Icons.Rounded.PlayArrow,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 128.dp)
            ) {
                itemsIndexed(playlist.items, key = { _, item -> item.uri }) { index, item ->
                    PlaylistItemRow(
                        item = item,
                        shape = segmentShape(index, playlist.items.size),
                        onClick = { onPlay(playlist.id, index) },
                        onRemove = { FlintData.removeFromPlaylist(playlist.id, item.uri) }
                    )
                }
            }
        }
    }

    if (renaming) {
        NameDialog(
            title = "Rename playlist",
            initial = playlist.name,
            confirmLabel = "Save",
            onDismiss = { renaming = false },
            onConfirm = { name ->
                FlintData.renamePlaylist(playlist.id, name)
                renaming = false
            }
        )
    }

    if (confirmDelete) {
        ExpressiveDialog(
            title = "Delete playlist?",
            onDismiss = { confirmDelete = false },
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                confirmDelete = false
                FlintData.deletePlaylist(playlist.id)
                onBack()
            }
        ) {
            Text(
                text = "\"${playlist.name}\" will be removed. Your videos are not deleted.",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaylistItemRow(
    item: MediaEntry,
    shape: Shape,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 1.dp)
            .pressItem(shape = shape, container = scheme.surfaceContainer, onClick = onClick)
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumbnail(
            uri = Uri.parse(item.uri),
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (item.durationMs > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formatDuration(item.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
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