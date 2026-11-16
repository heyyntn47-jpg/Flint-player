// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.library

import com.flint.player.ui.glassBorder
import com.flint.player.ui.MiddleEllipsisText
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flint.player.ui.ModalOverlay
import com.flint.player.ui.bounceClick
import com.flint.player.ui.popFade
import com.flint.player.ui.popScale
import com.flint.player.ui.rememberPopProgress
import com.flint.player.ui.segmentShape

private class MenuAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun VideoContextMenu(
    video: VideoItem,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShare: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val actions = buildList<MenuAction> {
        add(MenuAction(Icons.Outlined.PlayArrow, "Play", onClick = onPlay))
        add(MenuAction(Icons.AutoMirrored.Outlined.PlaylistAdd, "Add to playlist", onClick = onAddToPlaylist))
        onShare?.let { add(MenuAction(Icons.Outlined.Share, "Share", onClick = it)) }
        onRename?.let { add(MenuAction(Icons.Outlined.Edit, "Rename", onClick = it)) }
        onDelete?.let { add(MenuAction(Icons.Outlined.Delete, "Delete", destructive = true, onClick = it)) }
    }

    ModalOverlay(onDismiss = onDismiss) {
        val scheme = MaterialTheme.colorScheme
        val pop = rememberPopProgress()
        val config = LocalConfiguration.current
        val landscape = config.screenWidthDp > config.screenHeightDp
        val maxHeight = (config.screenHeightDp - 32).dp

        Surface(
            modifier = Modifier
                .padding(if (landscape) 16.dp else 24.dp)
                .widthIn(max = if (landscape) 640.dp else 360.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .popScale(pop)
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(36.dp),
            color = scheme.surfaceContainerHigh,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = glassBorder()
        ) {
            if (landscape) {
                // Two columns: the video on the left, the actions on the right
                Row(
                    Modifier
                        .padding(12.dp)
                        .popFade(pop)
                ) {
                    Column(Modifier.weight(1f)) {
                        VideoHeader(video, vertical = true)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        ActionList(actions, compact = true)
                    }
                }
            } else {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                        .popFade(pop)
                ) {
                    VideoHeader(video, vertical = false)
                    Spacer(Modifier.height(8.dp))
                    ActionList(actions, compact = false)
                }
            }
        }
    }
}

@Composable
private fun VideoHeader(video: VideoItem, vertical: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(26.dp)

    if (vertical) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(scheme.surfaceContainerHighest)
                .padding(12.dp)
        ) {
            Thumbnail(
                uri = video.uri,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.height(10.dp))
            VideoTitle(video, maxLines = 2)
        }
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(scheme.surfaceContainerHighest)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Thumbnail(
                uri = video.uri,
                modifier = Modifier
                    .width(100.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.width(14.dp))
            Column { VideoTitle(video, maxLines = 2) }
        }
    }
}

@Composable
private fun VideoTitle(video: VideoItem, maxLines: Int) {
    MiddleEllipsisText(
        text = video.name,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = maxLines
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = "${formatDuration(video.durationMs)} • ${formatSize(video.sizeBytes)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ActionList(actions: List<MenuAction>, compact: Boolean) {
    actions.forEachIndexed { index, action ->
        if (index > 0) Spacer(Modifier.height(2.dp))
        ActionRow(
            action = action,
            compact = compact,
            shape = segmentShape(index, actions.size, outer = 22.dp, inner = 6.dp)
        )
    }
}

@Composable
private fun ActionRow(action: MenuAction, compact: Boolean, shape: Shape) {
    val scheme = MaterialTheme.colorScheme
    val iconContainer = if (action.destructive) scheme.errorContainer else scheme.secondaryContainer
    val iconTint = if (action.destructive) scheme.onErrorContainer else scheme.onSecondaryContainer

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(pressedScale = 0.97f, onClick = action.onClick)
            .clip(shape)
            .background(scheme.surfaceContainerHighest)
            .padding(horizontal = 14.dp, vertical = if (compact) 7.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 34.dp else 38.dp)
                .clip(CircleShape)
                .background(iconContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = action.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (action.destructive) scheme.error else scheme.onSurface
        )
    }
}