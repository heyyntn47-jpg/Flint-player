// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flint.player.library.formatDuration
import com.flint.player.ui.ExpressiveIconButton
import com.flint.player.ui.MorphBadge
import androidx.compose.material.icons.outlined.Tune

private class Tile(
    val icon: ImageVector,
    val label: String,
    val subtitle: String?,
    val active: Boolean,
    val lobes: Int,
    val onClick: () -> Unit
)

/** The "Player settings" page: a grid of tiles, each with its own morphing shape. */
@Composable
fun SettingsMenuSheet(
    isPlaylist: Boolean,
    speedText: String,
    backgroundPlay: Boolean,
    decoderText: String,
    castName: String?,
    pipSupported: Boolean,
    sleep: SleepTimerState,
    onQueue: () -> Unit,
    onAudio: () -> Unit,
    onSubtitles: () -> Unit,
    onSpeed: () -> Unit,
    onSleep: () -> Unit,
    onCast: () -> Unit,
    onMixer: () -> Unit,
    onBackgroundPlay: () -> Unit,
    onDecoder: () -> Unit,
    onPip: () -> Unit
) {
    val sleepText = when {
        sleep.endOfVideo -> "After video"
        sleep.endAtMs > 0L -> formatDuration(sleep.remainingMs)
        else -> "Off"
    }

    val tiles = buildList {
        if (isPlaylist) {
            add(Tile(Icons.AutoMirrored.Outlined.PlaylistPlay, "Queue", null, false, 6, onQueue))
        }
        add(Tile(Icons.Outlined.Audiotrack, "Audio", null, false, 8, onAudio))
        add(Tile(Icons.Outlined.Subtitles, "Subtitles", null, false, 5, onSubtitles))
        add(Tile(Icons.Outlined.Speed, "Speed", speedText, false, 7, onSpeed))
        add(
            Tile(
                if (castName != null) Icons.Outlined.CastConnected else Icons.Outlined.Cast,
                "Cast", castName, castName != null, 5, onCast
            )
        )
        add(Tile(Icons.Outlined.Timer, "Sleep", sleepText, sleep.active, 6, onSleep))
        add(Tile(Icons.Outlined.Tune, "Mixer", null, false, 8, onMixer))
        add(
            Tile(
                Icons.Outlined.Headphones, "Background", if (backgroundPlay) "On" else "Off",
                backgroundPlay, 9, onBackgroundPlay
            )
        )
        add(Tile(Icons.Outlined.Memory, "Decoding", decoderText, false, 4, onDecoder))
        if (pipSupported) {
            add(Tile(Icons.Outlined.PictureInPictureAlt, "Pop-out", null, false, 6, onPip))
        }
    }

    Text(
        text = "Player settings",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(vertical = 8.dp)
    )

    tiles.chunked(3).forEach { rowTiles ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowTiles.forEach { tile -> MenuTile(tile) }
            repeat(3 - rowTiles.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun RowScope.MenuTile(tile: Tile) {
    val scheme = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 600f),
        label = "tileScale"
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(26.dp))
            .background(scheme.surfaceContainerHighest)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                tile.onClick()
            }
            .padding(horizontal = 6.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MorphBadge(
            icon = tile.icon,
            description = null,
            lobes = tile.lobes,
            pressed = pressed,
            active = tile.active,
            size = 52.dp,
            iconSize = 26.dp,
            tint = scheme.onSecondaryContainer,
            restContainer = scheme.secondaryContainer
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = tile.label,
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (tile.subtitle != null) {
            Text(
                text = tile.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** "‹ Player settings" row shown above every sub-page. */
@Composable
fun SheetBackRow(label: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            description = "Back",
            onClick = onBack,
            size = 40.dp,
            iconSize = 20.dp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun DecoderSheet(selected: Int, onSelect: (Int) -> Unit) {
    ChoiceSheet(
        title = "Decoding acceleration",
        options = listOf("Automatic", "Disable", "Full"),
        selectedIndex = selected,
        onSelect = onSelect
    )
    Text(
        text = "Automatic uses hardware and switches to software when a video can't play. " +
                "Disable always uses software (more battery). Full always uses hardware. " +
                "Changing it restarts the video at the same spot.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}