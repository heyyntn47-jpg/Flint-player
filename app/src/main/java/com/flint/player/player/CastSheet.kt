// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 heyyntn47

package com.flint.player.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flint.player.ui.segmentShape

/** "Cast to": this phone first, then every TV or speaker found on the Wi-Fi. */
@Composable
fun CastSheet(engine: VlcEngine) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

    // Look for devices only while this page is open
    DisposableEffect(engine) {
        engine.startCastScan()
        onDispose { engine.stopCastScan() }
    }

    SheetHeader("Cast to", engine.castName ?: "This phone")

    val devices = engine.castDevices
    val rowCount = devices.size + 1

    CastRow(
        icon = Icons.Outlined.PhoneAndroid,
        label = "This phone",
        subtitle = null,
        selected = engine.castName == null,
        shape = segmentShape(0, rowCount)
    ) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        if (engine.castName != null) engine.castTo(null)
    }
    devices.forEachIndexed { index, device ->
        Spacer(Modifier.height(2.dp))
        CastRow(
            icon = if (engine.castName == device.label) Icons.Outlined.CastConnected else Icons.Outlined.Cast,
            label = device.label,
            subtitle = null,
            selected = engine.castName == device.label,
            shape = segmentShape(index + 1, rowCount)
        ) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            engine.castTo(device)
        }
    }

    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (engine.castScanning) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = scheme.primary
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = when {
                devices.isNotEmpty() -> "Phone and device must be on the same Wi-Fi."
                engine.castScanning -> "Looking for TVs and speakers on your Wi-Fi…"
                else -> "No devices found. Check that your phone and TV share the same Wi-Fi."
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CastRow(
    icon: ImageVector,
    label: String,
    subtitle: String?,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (selected) scheme.primary else scheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) scheme.onPrimary else scheme.onSecondaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = scheme.primary
            )
        }
    }
}
