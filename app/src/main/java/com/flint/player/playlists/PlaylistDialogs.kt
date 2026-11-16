// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.playlists

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flint.player.data.FlintData
import com.flint.player.data.MediaEntry
import com.flint.player.data.Playlist
import com.flint.player.ui.ExpressiveButton
import com.flint.player.ui.ExpressiveDialog
import com.flint.player.ui.PillTextField
import com.flint.player.ui.bounceClick
import com.flint.player.ui.segmentShape

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initial) }

    ExpressiveDialog(
        title = title,
        onDismiss = onDismiss,
        confirmText = confirmLabel,
        confirmEnabled = name.isNotBlank(),
        onConfirm = { onConfirm(name) }
    ) {
        PillTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "Name",
            autoFocus = true,
            onDone = { if (name.isNotBlank()) onConfirm(name) }
        )
    }
}

@Composable
fun AddToPlaylistDialog(entry: MediaEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }

    fun addTo(playlist: Playlist) {
        val added = FlintData.addToPlaylist(playlist.id, entry)
        val message = if (added) "Added to ${playlist.name}" else "Already in ${playlist.name}"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    if (creating) {
        NameDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { creating = false },
            onConfirm = { name -> addTo(FlintData.createPlaylist(name)) }
        )
    } else {
        ExpressiveDialog(
            title = "Add to playlist",
            onDismiss = onDismiss,
            dismissText = "Close"
        ) {
            ExpressiveButton(
                text = "New playlist",
                onClick = { creating = true },
                modifier = Modifier.fillMaxWidth(),
                icon = Icons.Rounded.Add,
                tonal = true
            )
            val playlists = FlintData.playlists
            if (playlists.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    playlists.forEachIndexed { index, playlist ->
                        if (index > 0) Spacer(Modifier.height(2.dp))
                        PlaylistPickRow(
                            playlist = playlist,
                            alreadyAdded = playlist.items.any { it.uri == entry.uri },
                            shape = segmentShape(index, playlists.size, outer = 20.dp, inner = 6.dp),
                            onClick = { addTo(playlist) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistPickRow(
    playlist: Playlist,
    alreadyAdded: Boolean,
    shape: Shape,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(pressedScale = 0.97f, onClick = onClick)
            .clip(shape)
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(scheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                tint = scheme.onSecondaryContainer
            )
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
                text = if (alreadyAdded) "Already added"
                else if (playlist.items.size == 1) "1 video" else "${playlist.items.size} videos",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
        }
        if (alreadyAdded) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = scheme.primary
            )
        }
    }
}